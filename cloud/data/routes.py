# data/routes.py
from flask import Blueprint, request, jsonify, current_app
from flask_jwt_extended import jwt_required, get_jwt_identity
from database.cosmos import (
    get_user_by_id, 
    session_processing_data_container, 
    session_headers_container,
)
from datetime import datetime, timezone
from azure.cosmos.exceptions import CosmosResourceNotFoundError
import json
import logging
from sessions.tasks import finalize_session_task 

data_bp = Blueprint("data", __name__)
logger = logging.getLogger(__name__)

# --- get_pending_data route ---
@data_bp.route("/pending", methods=["GET"])
@jwt_required()
def get_pending_data():
    user_id = get_jwt_identity()
    current_user = get_user_by_id(user_id)
    if not current_user or current_user.get("type", "").upper() != "THERAPIST": 
        return jsonify({"error": "Forbidden: Only therapists can access pending data"}), 403
    
    query = "SELECT c.id, c.patient_name, c.session_date, c.summary_preview AS summaryPreview FROM c WHERE c.therapist_id = @tid AND c.status = @status ORDER BY c.last_updated_at DESC"
    params = [
        {"name": "@tid", "value": user_id},
        {"name": "@status", "value": "COMPLETED_PENDING_REVIEW"}
    ]
    try:

        items = list(session_headers_container.query_items(query=query, parameters=params, partition_key=user_id)) 
        
        results = []
        for item in items:
            session_date_db = item.get("session_date") 
            session_date_api_fmt = session_date_db 
            if session_date_db: 
                try: 
                    session_date_api_fmt = datetime.strptime(session_date_db, "%Y-%m-%d").strftime("%d-%m-%Y")
                except ValueError: 
                    logger.warning(f"Could not format session_date '{session_date_db}' for /pending for item {item.get('id')}. Sending as is.")

            results.append({
                "id": item.get("id"), 
                "patientName": item.get("patient_name"),
                "sessionDate": session_date_api_fmt, 
                "summaryPreview": item.get("summaryPreview", "")[:20]
            })
        return jsonify(results), 200
    except Exception as e:
        logger.error(f"Error in /data/pending for user {user_id}: {e}", exc_info=True)
        return jsonify({"error": "Internal server error"}), 500

# --- get_transcript_detail (for editing) route ---
@data_bp.route("/transcript/<string:data_id>", methods=["GET"]) # API Spec pg 12: /data/transcript/{dataId}
@jwt_required()
def get_transcript_detail_for_editing(data_id):
    user_id = get_jwt_identity()
    current_user = get_user_by_id(user_id)
    if not current_user or current_user.get("type", "").upper() != "THERAPIST":
        return jsonify({"error": "Forbidden: Only therapists can access this"}), 403

    try:
        item_to_review = session_processing_data_container.read_item(item=data_id, partition_key=data_id)
    except CosmosResourceNotFoundError:
        return jsonify({"error": "Not Found: Transcript data not found"}), 404

    if item_to_review.get("therapist_id") != user_id:
        return jsonify({"error": "Forbidden: Not authorized for this transcript data"}), 403
    if item_to_review.get("status") != "COMPLETED_PENDING_REVIEW":
        return jsonify({"error": "Conflict: Transcript not in a reviewable state"}), 409
    
    session_date_db = item_to_review.get("session_date") 
    session_date_api_fmt = session_date_db
    if session_date_db:
        try:
            session_date_api_fmt = datetime.strptime(session_date_db, "%Y-%m-%d").strftime("%d-%m-%Y")
        except ValueError:
            logger.warning(f"Could not format session_date '{session_date_db}' for /data/transcript GET for item {data_id}. Sending as is.")

    response = {
        "patientName": item_to_review.get("patient_name"),
        "sessionDate": session_date_api_fmt,
        "summary": item_to_review.get("summary_initial", ""), 
        "transcript": item_to_review.get("transcript_processed", []) 
    }
    return jsonify(response), 200
    


@data_bp.route("/transcript/<string:data_id>", methods=["PUT"]) # API Spec pg 13
@jwt_required()
def submit_final_transcript(data_id): 
    user_id = get_jwt_identity()
    current_user = get_user_by_id(user_id)
    try:

        if not current_user or current_user.get("type", "").upper() != "THERAPIST":
            logger.warning(f"Forbidden attempt to submit transcript by user {user_id} (not a therapist) for data {data_id}")
            return jsonify({"error": "Forbidden: Only therapists can submit transcripts"}), 403

        raw_json_string_body = request.get_data(as_text=True)
        try:
            final_edited_transcript_segments = json.loads(raw_json_string_body) # This is List<SessionTranscriptResponse>
            if not isinstance(final_edited_transcript_segments, list):
                raise ValueError("Final transcript must be a list of segments.")
        except (json.JSONDecodeError, ValueError) as e:
            logger.warning(f"Invalid JSON for PUT /data/transcript/{data_id} by user {user_id}: {e}", exc_info=True)
            return jsonify({"error": "Bad Request: Invalid JSON string format in the body"}), 400

        try:
            # 1. Read the original processed data item
            # Assuming 'id' (data_id) is the PK for processed_data_container
            processed_item = session_processing_data_container.read_item(item=data_id, partition_key=data_id)
        except CosmosResourceNotFoundError:
            logger.warning(f"Data item {data_id} not found for PUT /data/transcript by user {user_id}")
            return jsonify({"error": "Not Found: No processed data with the given ID"}), 404

        if processed_item.get("therapist_id") != user_id:
            logger.warning(f"Forbidden attempt by therapist {user_id} to finalize data {data_id} belonging to another therapist.")
            return jsonify({"error": "Forbidden: This therapist cannot finalize this data"}), 403
        if processed_item.get("status") != "COMPLETED_PENDING_REVIEW":
            logger.warning(f"Conflict: Data {data_id} by user {user_id} is not in a reviewable state (status: {processed_item.get('status')}).")
            return jsonify({"error": "Conflict: This data is not awaiting review"}), 409

        # 2. Perform Sentiment Analysis on the FINAL EDITED patient transcript
        
        logger.info(f"Therapist {user_id} submitted final transcript for {data_id}. Running sentiment analysis.")
        processed_item["final_transcript_edited_payload"] = final_edited_transcript_segments
        processed_item["status"] = "PENDING_FINAL_SENTIMENT_ANALYSIS" # New status
        processed_item["pending_review"] = False # No longer pending therapist STT review
        processed_item["therapist_final_edit_at"] = datetime.now(timezone.utc).isoformat()
        
        # Preserve notes from initial to final edit (since notes aren't edited in transcript review)
        processed_item["timed_notes"] = processed_item.get("timed_notes", [])
        processed_item["general_notes"] = processed_item.get("general_notes", [])
        processed_item["summary_final_edit"] = processed_item.get("summary_initial", "")
        
        session_processing_data_container.replace_item(item=processed_item["id"], body=processed_item)
        logger.info(f"Processed data item {data_id} updated with final transcript, status PENDING_FINAL_SENTIMENT_ANALYSIS.")


        try:
            header_item = session_headers_container.read_item(item=data_id, partition_key=processed_item["therapist_id"]) # Use therapist_id as PK
            header_item["status"] = "PENDING_FINAL_PROCESSING"
            header_item["last_updated_at"] = datetime.now(timezone.utc).isoformat()
            session_headers_container.replace_item(item=header_item["id"], body=header_item)
        except Exception as e_h:
            logger.error(f"Error updating header for {data_id} to PENDING_FINAL_PROCESSING: {e_h}")
            raise


        # 4. Dispatch finalize_session_task (this task now does sentiment and creates final docs)
        finalize_session_task.delay(processing_id=data_id)
        
        return jsonify({"message": "Transcript edits accepted, queued for finalization."}), 202

    except Exception as e:
        logger.error(f"Error in PUT /data/transcript/{data_id} (queuing sentiment task) by user {user_id}: {str(e)}", exc_info=True)
        return jsonify({"error": "Internal Server Error during final submission."}), 500