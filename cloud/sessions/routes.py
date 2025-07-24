# sessions/routes.py
from flask import Blueprint, request, jsonify, current_app
import uuid
import os
import logging
import json
from datetime import datetime, timezone
import socket


from flask_jwt_extended import jwt_required, get_jwt_identity
from database.cosmos import (
    get_user_by_id, get_session_summaries_for_user, search_session_summaries_by_query,
    session_headers_container, session_processing_data_container, session_details_container
)
from azure.cosmos import exceptions as cosmos_exceptions
import config 

from azure.storage.blob import BlobServiceClient, ContentSettings 
from .tasks import simple_test_task  

sessions_bp = Blueprint("sessions_bp", __name__)
logger = logging.getLogger(__name__)

# Initialize Blob Service Client
try:
    if config.AZURE_STORAGE_CONNECTION_STRING:
        # This client is initialized when the blueprint is loaded.
        flask_blob_service_client = BlobServiceClient.from_connection_string(config.AZURE_STORAGE_CONNECTION_STRING)
    else:
        flask_blob_service_client = None
        logger.error("Azure Blob Storage connection string not configured in Flask. Direct uploads will fail.")
except Exception as e:
    logger.error(f"FATAL: Could not initialize Azure Blob Storage client in Flask routes: {e}", exc_info=True)
    flask_blob_service_client = None



@sessions_bp.route('/test-socket-to-redis', methods=['GET'])
def test_socket_to_redis():
    host = "redis"
    port = 6379
    connection_message = ""
    try:
        logger.info(f"Attempting Python socket connection to {host}:{port}")
        sock = socket.create_connection((host, port), timeout=5)
        connection_message = f"Successfully connected to {host}:{port} via Python socket."
        logger.info(connection_message)

        # Optional: Try sending a PING command (raw Redis protocol)
        sock.sendall(b"*1\r\n$4\r\nPING\r\n")
        response = sock.recv(1024)
        logger.info(f"Redis PING response: {response}")
        if b"+PONG" in response:
            connection_message += " PING successful."
        else:
            connection_message += f" PING got unexpected response: {response!r}"
        sock.close()
        return jsonify({"status": "success", "message": connection_message}), 200
    except socket.timeout:
        error_message = f"Python socket connection to {host}:{port} timed out."
        logger.error(error_message)
        return jsonify({"status": "error", "message": error_message}), 500
    except ConnectionRefusedError:
        error_message = f"Python socket connection to {host}:{port} was refused."
        logger.error(error_message)
        return jsonify({"status": "error", "message": error_message}), 500
    except socket.gaierror as e: # Address info error (e.g., name not known)
        error_message = f"Python socket GAIError for {host}:{port}: {e}"
        logger.error(error_message)
        return jsonify({"status": "error", "message": error_message}), 500
    except Exception as e:
        error_message = f"Python socket connection to {host}:{port} failed with other error: {e}"
        logger.error(error_message, exc_info=True)
        return jsonify({"status": "error", "message": error_message}), 500

# Your existing /test-celery route (keep it for comparison)
@sessions_bp.route('/test-celery', methods=['GET'])
def test_celery_dispatch():
    try:
        logger.info("Attempting to dispatch simple_test_task...")
        task_result_async = simple_test_task.delay(5, 10)
        logger.info(f"Dispatched simple_test_task. Task ID: {task_result_async.id}")
        return jsonify({"message": "Simple test task dispatched!", "task_id": task_result_async.id}), 200
    except Exception as e:
        logger.error(f"Error dispatching simple_test_task: {e}", exc_info=True)
        return jsonify({"error": "Failed to dispatch simple test task", "details": str(e)}), 500

@sessions_bp.route("/<string:session_id>/details", methods=["GET"])
@jwt_required()
def get_final_session_details(session_id): 
    user_id = get_jwt_identity()
    current_user = get_user_by_id(user_id)
    if not current_user: 
        return jsonify({"error": "Unauthorized"}), 401

    try:
        session_doc = session_details_container.read_item(item=session_id, partition_key=session_id)
    except cosmos_exceptions.CosmosResourceNotFoundError:
        logger.info(f"Final session details {session_id} not found.")
        return jsonify({"error": "Session not found"}), 404
    except Exception as e: 
        logger.error(f"Error fetching session details {session_id}: {e}", exc_info=True)
        return jsonify({"error": "Internal server error"}), 500
    
    if current_user.get("type", "").upper() != "THERAPIST" and user_id != session_doc.get("patient_id"):
        logger.info(f"User {user_id} not authorized to access session {session_id}.")
        return jsonify({"error": "Forbidden"}), 403

    session_date_db = session_doc.get("session_date")
    session_date_api = session_date_db 
    if session_date_db:
        try:
            session_date_api_fmt = datetime.strptime(session_date_db, "%Y-%m-%d").strftime("%d-%m-%Y")
        except ValueError:
            logger.warning(f"Could not parse session_date '{session_date_db}' for session {session_id} (details).")

    patient_dob_db = session_doc.get("patient_date_of_birth") # Expected YYYY-MM-DD from DB
    patient_dob_api_fmt = patient_dob_db
    if patient_dob_db:
        try:
            patient_dob_api_fmt = datetime.strptime(patient_dob_db, "%Y-%m-%d").strftime("%d-%m-%Y")
        except ValueError:
            logger.warning(f"Could not parse patient_date_of_birth '{patient_dob_db}' (YYYY-MM-DD expected) for session {session_id} (details). Sending as is.")

    response = {
        "sessionId": session_doc.get("sessionId", session_doc.get("id")), 
        "therapist_name": session_doc.get("therapist_name"),
        "therapist_email": session_doc.get("therapist_email"),
        "patient_id": session_doc.get("patient_id"),
        "patient_name": session_doc.get("patient_name"),
        "patient_email": session_doc.get("patient_email"),
        "patient_date_of_birth": patient_dob_api_fmt, 
        "session_date": session_date_api_fmt,
        "summary": session_doc.get("summary"), 
        "timed_notes": session_doc.get("timed_notes", []), 
        "general_notes": session_doc.get("general_notes", []), 
        "positive": session_doc.get("positive", 0.0), 
        "neutral": session_doc.get("neutral", 0.0), 
        "negative": session_doc.get("negative", 0.0), 
        "sentiment_scores": session_doc.get("sentiment_scores", []), # List<SentimentScoreEntry>
        # No therapist_speech or patient_speech strings as per corrected understanding
    }
    return jsonify(response), 200

# Other routes (/me, /search) remain largely the same as previous correct versions.

@sessions_bp.route("/me", methods=["GET"])
@jwt_required()
def get_my_final_sessions():
    user_id = get_jwt_identity()
    current_user = get_user_by_id(user_id)
    if not current_user: return jsonify({"error": "Unauthorized"}), 401

    user_type = current_user.get("type", "").upper()
    if user_type not in ["PATIENT", "THERAPIST"]:
        return jsonify({"error": "Invalid user type"}), 403 

    try:
        summaries = get_session_summaries_for_user(user_id, user_type) 
        return jsonify(summaries), 200
    except Exception as e:
        logger.error(f"Error in /sessions/me for user {user_id}: {e}", exc_info=True)
        return jsonify({"error": "Internal server error"}), 500


@sessions_bp.route("/search", methods=["GET"])
@jwt_required()
def search_final_sessions():
    user_id = get_jwt_identity()
    current_user = get_user_by_id(user_id)
    if not current_user or current_user.get("type","").upper() != "THERAPIST":
        return jsonify({"error": "Forbidden: Only therapists can search sessions."}), 403

    query_term = request.args.get("query", "").strip()
    if not query_term: return jsonify([]), 200

    try:
        results = search_session_summaries_by_query(query_term)
        return jsonify(results), 200
    except Exception as e:
        logger.error(f"Error in /sessions/search (therapist {user_id}, query '{query_term}'): {e}", exc_info=True)
        return jsonify({"error": "Internal server error"}), 500


@sessions_bp.route('/upload', methods=['POST'])
@jwt_required()
def upload_audio_session_direct_to_blob(): # Renamed for clarity
    current_user_id = get_jwt_identity()
    user = get_user_by_id(current_user_id)

    if not user:
        return jsonify({"error": "User not found or unauthorized"}), 401

    if not flask_blob_service_client:
        logger.error(f"User {current_user_id} attempted upload but Blob Service Client is not initialized.")
        return jsonify({"error": "File storage service not available."}), 503 # Service Unavailable

    audio_file_part = request.files.get("audio_file")
    if not audio_file_part or not audio_file_part.filename:
        logger.warning(f"Missing 'audio_file' part or filename in upload from user {current_user_id}.")
        return jsonify({"error": "Missing 'audio_file' part or filename in request"}), 400

    metadata_json_string = request.form.get("metadata")
    if not metadata_json_string:
        logger.warning(f"Missing 'metadata' part in upload from user {current_user_id} for file {audio_file_part.filename}.")
        return jsonify({"error": "Missing 'metadata' part in request"}), 400

    try:
        metadata = json.loads(metadata_json_string)
    except json.JSONDecodeError as e:
        logger.warning(f"Invalid JSON in metadata from user {current_user_id} for file {audio_file_part.filename}. Error: {e}")
        return jsonify({"error": "Invalid JSON in metadata"}), 400

    session_id_str = str(uuid.uuid4())
    original_filename = audio_file_part.filename
    file_content_type = audio_file_part.content_type # Get content type from uploaded file

    # --- Upload directly to Azure Blob Storage ---
    blob_folder = session_id_str
    blob_name_in_storage = f"{blob_folder}/{original_filename}"
    blob_url = None

    try:
        container_name = config.AUDIO_UPLOAD_BLOB_CONTAINER_NAME
        container_client = flask_blob_service_client.get_container_client(container_name)

        if not container_client.exists():
             container_client.create_container()
             logger.info(f"Container '{container_name}' did not exist and was created.")

        blob_client = container_client.get_blob_client(blob=blob_name_in_storage)
        # Get the stream from the file part
        audio_stream = audio_file_part.stream
        # Set content type for the blob for easier consumption later
        content_settings = ContentSettings(content_type=file_content_type)
        blob_client.upload_blob(audio_stream, overwrite=True, content_settings=content_settings)
        blob_url = blob_client.url
        logger.info(f"User {current_user_id} uploaded '{original_filename}' directly to {blob_url} for session {session_id_str}.")
    except Exception as e_blob_flask:
        logger.error(f"Error user {current_user_id} uploading audio directly to blob for session {session_id_str}: {e_blob_flask}", exc_info=True)
        return jsonify({"error": "Server error during file storage."}), 500

    # --- Helper to parse actor metadata ---
    def parse_actor_meta(actor_str_or_dict, actor_type_name):
        if isinstance(actor_str_or_dict, str):
            try: return json.loads(actor_str_or_dict)
            except json.JSONDecodeError:
                logger.warning(f"Could not parse JSON string for {actor_type_name} metadata: {actor_str_or_dict}"); return {}
        elif isinstance(actor_str_or_dict, dict): return actor_str_or_dict
        logger.warning(f"Unexpected type for {actor_type_name} metadata: {type(actor_str_or_dict)}"); return {}
        return {}


    # --- Create session_header_document and session_processing_data document ---
    try:
        therapist_details_dict = parse_actor_meta(metadata.get("therapist"), "therapist")
        patient_details_dict = parse_actor_meta(metadata.get("patient"), "patient")

        therapist_id_from_meta = therapist_details_dict.get("id", user.get("id"))
        therapist_name_from_meta = therapist_details_dict.get("name", user.get("userFullName"))
        patient_id_from_meta = patient_details_dict.get("id")
        patient_name_from_meta = patient_details_dict.get("name")
        patient_email_from_meta = patient_details_dict.get("email", "Unknown")

        if not patient_id_from_meta or not patient_name_from_meta:
            logger.error(f"Missing patient ID or name in metadata for session {session_id_str} by user {current_user_id}. Metadata: {metadata}")
            # Consider deleting the uploaded blob if this critical info is missing
            try: blob_client.delete_blob()
            except: pass
            return jsonify({"error": "Patient ID and Name are required in metadata"}), 400

        session_date_raw_from_client = metadata.get("session_date") # Expected dd-MM-YYYY
        session_date_for_db = datetime.now(timezone.utc).strftime("%Y-%m-%d") # Default
        if session_date_raw_from_client:
            try:
                session_date_for_db = datetime.strptime(session_date_raw_from_client, "%d-%m-%Y").strftime("%Y-%m-%d")
            except ValueError:
                logger.warning(f"Could not parse session_date '{session_date_raw_from_client}' for session {session_id_str}. Using current date for DB.")

        current_ts_iso = datetime.now(timezone.utc).isoformat()

        header_doc = {
            "id": session_id_str, "sessionId": session_id_str, # PK for headers is therapist_id, id is session_id
            "therapist_id": therapist_id_from_meta,
            "therapist_name": therapist_name_from_meta,
            "patient_id": patient_id_from_meta,
            "patient_name": patient_name_from_meta,
            "session_date": session_date_for_db, # YYYY-MM-DD
            "title": f"Session: {patient_name_from_meta} - {session_date_raw_from_client or session_date_for_db}",
            "status": "QUEUED_FOR_CELERY_STT", # Initial status after successful upload and DB entries
            "summary_preview": metadata.get("summary", "")[:150],
            "uploaded_at": current_ts_iso,
            "last_updated_at": current_ts_iso,
            "processing_id_link": session_id_str,
            "blob_url": blob_url, # Store the direct blob URL
            "overall_sentiment_positive": 0.0, "overall_sentiment_neutral": 1.0, "overall_sentiment_negative": 0.0,
        }
        # Partition key for session_headers_container is therapist_id
        session_headers_container.upsert_item(body=header_doc)
        logger.info(f"Initial header created for session {session_id_str} by user {current_user_id}.")

        general_notes_list = []
        raw_general_notes = metadata.get("general_notes", [])
        if isinstance(raw_general_notes, list):
            for note_item in raw_general_notes:
                content = note_item.get("content") if isinstance(note_item, dict) else str(note_item)
                if content is not None: general_notes_list.append(content)
        
        timed_notes_list = []
        raw_timed_notes = metadata.get("timed_notes", [])
        if isinstance(raw_timed_notes, list):
            for tn_item in raw_timed_notes:
                if isinstance(tn_item, dict) and "timestamp" in tn_item and "content" in tn_item:
                    timed_notes_list.append({
                        "timestamp": str(tn_item.get("timestamp","00:00:00")),
                        "content": str(tn_item.get("content",""))
                    })

        processing_doc = {
            "id": session_id_str, # PK for this container is session_id
            "header_id": session_id_str,
            "therapist_id": therapist_id_from_meta,
            "patient_id": patient_id_from_meta,
            "patient_name": patient_name_from_meta,
            "patient_email": patient_email_from_meta,
            "session_date": session_date_for_db, # YYYY-MM-DD
            "original_filename": original_filename,
            "blob_container_name": config.AUDIO_UPLOAD_BLOB_CONTAINER_NAME,
            "blob_name": blob_name_in_storage, # Path within container
            "status": "QUEUED_FOR_CELERY_STT",
            "uploaded_at": current_ts_iso, # More like "metadata_created_at"
            "summary_initial": metadata.get("summary", ""),
            "general_notes": general_notes_list,
            "timed_notes": timed_notes_list,
            "transcript_processed": [],
        }
        session_processing_data_container.upsert_item(body=processing_doc)
        logger.info(f"Processing data document created for session {session_id_str}.")

    except Exception as e_db:
        logger.error(f"Error creating DB records for session {session_id_str} after blob upload: {e_db}", exc_info=True)
        # Attempt to delete the orphaned blob
        try:
            if blob_client: blob_client.delete_blob()
            logger.info(f"Orphaned blob {blob_name_in_storage} deleted for session {session_id_str} due to DB error.")
        except Exception as e_blob_delete:
            logger.error(f"Failed to delete orphaned blob {blob_name_in_storage} for session {session_id_str}: {e_blob_delete}")
        return jsonify({"error": "Server error during data recording."}), 500

    # --- Dispatch Celery task for STT processing ---
    try:
        from .tasks import process_audio_stt_task # Ensure direct import if not already
        process_audio_stt_task.delay(processing_id=session_id_str)
        logger.info(f"Dispatched 'process_audio_stt_task' for session {session_id_str}")
    except Exception as e_celery:
        logger.error(f"Error dispatching STT Celery task for session {session_id_str}: {e_celery}", exc_info=True)
        try:
            header_doc["status"] = "UPLOAD_FAILED_STT_DISPATCH"
            header_doc["last_updated_at"] = datetime.now(timezone.utc).isoformat()
            session_headers_container.replace_item(item=header_doc["id"], body=header_doc) # PK is therapist_id

            processing_doc["status"] = "UPLOAD_FAILED_STT_DISPATCH"
            session_processing_data_container.replace_item(item=processing_doc["id"], body=processing_doc) # PK is id
        except Exception as e_status_update:
            logger.error(f"Failed to update status to UPLOAD_FAILED_STT_DISPATCH for session {session_id_str}: {e_status_update}")
        return jsonify({"error": "Server error queueing file for STT processing."}), 500

    return jsonify({
        "session_id": session_id_str,
        "status": "UPLOAD_COMPLETE_STT_QUEUED",
        "message": "Audio received and queued for speech-to-text processing."
    }), 202