# sessions/tasks.py
import os
import tempfile
import time
import json
from datetime import datetime, timedelta, timezone
import uuid
import logging
from io import BytesIO
import threading
import subprocess
import struct

from celery import shared_task
from celery_app import celery
from azure.storage.blob import BlobServiceClient
from azure.cognitiveservices.speech import (
    SpeechConfig, AudioConfig, ServicePropertyChannel, OutputFormat, ResultReason, CancellationReason,
    SpeechRecognitionEventArgs, SessionEventArgs, SpeechRecognitionCanceledEventArgs
)
import azure.cognitiveservices.speech as speechsdk

from pydub import AudioSegment 

from azure.core.exceptions import ResourceNotFoundError as AzureBlobResourceNotFoundError


from database.cosmos import ( 
    session_processing_data_container, 
    session_headers_container, 
    session_details_container,
    devices_container, 
    users_container 
)
from azure.cosmos import exceptions as cosmos_exceptions


from sessions.utils import analyze_sentiment
from notifications.fcm import send_fcm_notification
import config 

logger = logging.getLogger(__name__)

def format_timestamp_from_ticks(ticks: int) -> str:
    """
    Convert a timespan ticks value (100-nanosecond units) to a readable timestamp string.
    
    Args:
        ticks: Number of 100-nanosecond intervals since start
        
    Returns:
        Formatted string in the form of "h:mm:ss.fff"
    """
    if ticks is None:
        return "0:00:00.000"
    
    # Ensure ticks is an integer
    try:
        ticks = int(ticks)
    except (TypeError, ValueError):
        logger.warning(f"Invalid ticks value: {ticks}, using 0")
        return "0:00:00.000"
    
    # Convert ticks (100-nanosecond units) to seconds
    seconds_float = ticks / 10_000_000.0
    td = timedelta(seconds=seconds_float)
    
    # Extract time components
    total_seconds_int = int(td.total_seconds())
    hours = total_seconds_int // 3600
    minutes = (total_seconds_int % 3600) // 60
    secs = total_seconds_int % 60
    milliseconds = td.microseconds // 1000
    
    return f"{hours:01}:{minutes:02}:{secs:02}.{milliseconds:03}"

def get_speech_service_config_for_task(): # Keep this helper
    speech_cfg = SpeechConfig(subscription=config.SPEECH_KEY, region=config.SPEECH_REGION)
    speech_cfg.speech_recognition_language = config.SPEECH_RECOGNITION_LANGUAGE
    speech_cfg.output_format = OutputFormat.Detailed
    speech_cfg.request_word_level_timestamps()
    return speech_cfg


## TEST ##
@celery.task(bind=True)
def simple_test_task(self, x, y):
    logger.info(f"[SIMPLE TEST TASK {self.request.id}] Received: x={x}, y={y}. Returning sum.")
    return x + y

@celery.task(name="test_task", bind=True)
def test_task(self):
    """
    A simple test task to verify Celery is working correctly
    """
    logger.info(f"Test task started at {datetime.utcnow().isoformat()}")
    
    # Task ID for tracking
    task_id = self.request.id
    logger.info(f"Task ID: {task_id}")
    
    # Simulate work
    time.sleep(5)
    
    logger.info(f"Test task completed at {datetime.utcnow().isoformat()}")
    return {
        "status": "completed", 
        "message": "Celery test task completed successfully",
        "task_id": task_id,
        "completed_at": datetime.utcnow().isoformat()
    }

## /TEST ##

@celery.task(bind=True, max_retries=3, default_retry_delay=120, acks_late=True, time_limit=1200)
def process_audio_stt_task(self, processing_id: str):
    logger.info(f"[STT TASK {self.request.id}] Starting for Processing ID: {processing_id}")
    downloaded_raw_audio_path = None
    converted_wav_path = None   

    try:
        # Read processing_item using processing_id as item ID and partition key
        processing_item = session_processing_data_container.read_item(item=processing_id, partition_key=processing_id)
        
        # Get therapist_id for header's partition key
        therapist_id_for_header_pk = processing_item.get("therapist_id")
        if not therapist_id_for_header_pk:
            logger.error(f"[STT TASK {self.request.id}] Therapist ID missing in processing_item for {processing_id}. Cannot update header.")
            raise ValueError(f"Therapist ID missing for processing_id {processing_id}")

        current_status = processing_item.get("status")
        if current_status != "QUEUED_FOR_CELERY_STT":
            logger.warning(f"[STT TASK {self.request.id}] Task for {processing_id} received but status is '{current_status}', not 'QUEUED_FOR_CELERY_STT'. Aborting if already processed or failed.")
            if current_status in ["PROCESSING_STT", "COMPLETED_PENDING_REVIEW", "FAILED_STT_PROCESSING"]: # Avoid reprocessing
                 return {"status": "SKIPPED_WRONG_STATE", "processing_id": processing_id, "current_status": current_status}

        # Update status in processing_item
        processing_item["status"] = "PROCESSING_STT"
        processing_item["task_stt_id"] = self.request.id # Store Celery task ID
        session_processing_data_container.replace_item(item=processing_item["id"], body=processing_item)
        
        # Update status in header_item
        try:
            header_item = session_headers_container.read_item(item=processing_id, partition_key=therapist_id_for_header_pk)
            header_item["status"] = "PROCESSING_STT"
            header_item["last_updated_at"] = datetime.now(timezone.utc).isoformat()
            session_headers_container.replace_item(item=header_item["id"], body=header_item)
        except cosmos_exceptions.CosmosResourceNotFoundError:
            logger.error(f"[STT TASK {self.request.id}] Header item {processing_id} with PK {therapist_id_for_header_pk} not found for STT start update.")
            # Decide if this is critical. For now, log and continue.
        except Exception as e_h_start:
            logger.warning(f"[STT TASK {self.request.id}] Error updating header for {processing_id} at STT start: {e_h_start}", exc_info=True)

        # 1. Download Raw Audio from Blob (decrypted audio from client)
        logger.info(f"[STT TASK {self.request.id}] Downloading raw audio from blob: {processing_item['blob_name']}")
        blob_service_client_task = BlobServiceClient.from_connection_string(config.AZURE_STORAGE_CONNECTION_STRING)
        blob_client = blob_service_client_task.get_blob_client(
            container=processing_item["blob_container_name"], 
            blob=processing_item["blob_name"]
        )
        
        try:
            raw_audio_blob_bytes = blob_client.download_blob().readall()
        except AzureBlobResourceNotFoundError:
            logger.error(f"[STT TASK {self.request.id}] Blob not found: {processing_item['blob_name']} in container {processing_item['blob_container_name']}")
            raise # Re-raise to fail the task
        
        logger.info(f"[STT TASK {self.request.id}] Raw audio downloaded. Size: {len(raw_audio_blob_bytes)} bytes.")

        # 2. Convert raw audio to WAV
        original_filename = processing_item.get("original_filename", "audio.dat")
        file_ext = os.path.splitext(original_filename)[1].lower().replace(".", "") or "m4a" # Default to m4a if no ext

        # Create temp files correctly within a 'with' block for automatic cleanup on exit/error
        
        with tempfile.NamedTemporaryFile(suffix=f".{file_ext}", delete=False) as tmp_raw_file_obj, \
             tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp_wav_file_obj:
            downloaded_raw_audio_path = tmp_raw_file_obj.name
            converted_wav_path = tmp_wav_file_obj.name
        
        try:
            with open(downloaded_raw_audio_path, 'wb') as f_raw:
                f_raw.write(raw_audio_blob_bytes)
            
            # # Validate file integrity before processing
            # if not _validate_audio_file_integrity(downloaded_raw_audio_path, file_ext, self.request.id):
            #     raise ValueError(f"Audio file validation failed for {processing_id}. File appears to be corrupted or incomplete.")
            
            logger.info(f"[STT TASK {self.request.id}] Attempting to convert '{downloaded_raw_audio_path}' (ext: {file_ext}) to WAV '{converted_wav_path}'.")
            
            # Try multiple conversion strategies for better compatibility
            audio_segment = _convert_audio_with_fallback(downloaded_raw_audio_path, file_ext, self.request.id)
            
            target_channels = int(config.SPEECH_AUDIO_CHANNEL_COUNT)
            audio_segment = audio_segment.set_frame_rate(16000).set_sample_width(2)
            if audio_segment.channels != target_channels:
                audio_segment = audio_segment.set_channels(target_channels)
            
            audio_segment.export(converted_wav_path, format="wav")
            logger.info(f"[STT TASK {self.request.id}] Audio converted to WAV: {converted_wav_path}, Channels: {target_channels}, Duration: {len(audio_segment)/1000.0}s")
        except Exception as conversion_err:
            logger.error(f"[STT TASK {self.request.id}] Error converting audio to WAV: {conversion_err}", exc_info=True)
            # Mark task as failed with specific error details for better client feedback
            _mark_processing_failed(processing_id, f"Audio conversion failed: {str(conversion_err)}", self.request.id)
            raise # Re-raise to fail the task and trigger retry/error handling

        # 3. Perform Speech-to-Text
        
        logger.info(f"[STT TASK {self.request.id}] Starting STT with ConversationTranscriber using {converted_wav_path}.")
        speech_sdk_config_for_task = get_speech_service_config_for_task()
        audio_input_config = AudioConfig(filename=converted_wav_path)
        
        transcriber = speechsdk.transcription.ConversationTranscriber(speech_config=speech_sdk_config_for_task, audio_config=audio_input_config)

        stt_results_list = [] 
        stt_completed_event = threading.Event()
        stt_cancellation_details = None # Variable to store cancellation details

        def recognized_cb(evt: SpeechRecognitionEventArgs): # microsoft docs
            if evt.result.reason == ResultReason.RecognizedSpeech and  len(evt.result.text) > 0:
                stt_results_list.append({
                    "speaker_id": evt.result.speaker_id if hasattr(evt.result, 'speaker_id') else "Unknown", # speaker_id for ConversationTranscriber
                    "text": evt.result.text, 
                    "offset_ticks": evt.result.offset, # Offset from WordLevel timing
                    "duration_ticks": evt.result.duration, # Duration from WordLevel timing
                })
            elif evt.result.reason == ResultReason.NoMatch:
                logger.debug(f"[STT CB] NoMatch for input.")

        def session_stopped_cb(evt: SessionEventArgs): 
            logger.info(f"[STT CB] Session stopped. SessionId: {evt.session_id}")
            # stt_completed_event.signal()
            stt_completed_event.set()

        def canceled_cb(evt: SpeechRecognitionCanceledEventArgs):
            nonlocal stt_cancellation_details # Ensure this variable is from the outer scope
            logger.warning(f"[STT CB CANCELED] Reason={evt.reason}, SessionId: {evt.session_id}")
            if evt.reason == CancellationReason.Error:
                stt_cancellation_details = f"STT Error ({evt.error_code}): {evt.error_details}"
                logger.error(f"[STT CB CANCELED] ErrorDetails: {evt.error_details}")
            # stt_completed_event.signal()
            stt_completed_event.set()

        transcriber.transcribed.connect(recognized_cb)
        transcriber.session_stopped.connect(session_stopped_cb)
        transcriber.canceled.connect(canceled_cb)

        transcriber.start_transcribing_async().get() # Wait for start
        logger.info(f"[STT TASK {self.request.id}] Transcription session started, waiting for completion signal...")
        
        # if not stt_completed_event.wait_for_signaled(timeout_seconds=config.SPEECH_STT_TIMEOUT_SECONDS or 600): # Use config or default
        if not stt_completed_event.wait(timeout=(config.SPEECH_STT_TIMEOUT_SECONDS or 600)):            
            logger.warning(f"[STT TASK {self.request.id}] STT process timed out for {processing_id}. Attempting to stop.")
            transcriber.stop_transcribing_async().get() # Wait for stop
            raise TimeoutError(f"STT process timed out for {processing_id}.")
        
        transcriber.stop_transcribing_async().get() # Ensure it's stopped if signaled normally
        logger.info(f"[STT TASK {self.request.id}] Transcription session stopped for {processing_id}.")
        
        if stt_cancellation_details: 
            logger.error(f"[STT TASK {self.request.id}] STT failed due to cancellation: {stt_cancellation_details} for {processing_id}")
            raise Exception(stt_cancellation_details)
        
        if not stt_results_list:
             logger.warning(f"[STT TASK {self.request.id}] STT completed but produced no transcript segments for {processing_id}. Check audio quality or speech content.")
             # Potentially raise an error or handle as an empty transcript

        logger.info(f"[STT TASK {self.request.id}] STT completed. Received {len(stt_results_list)} segments for {processing_id}.")

        # Process STT results (speaker mapping, api_transcript_for_db, etc.)
        speaker_role_map = {}
        distinct_speaker_ids = sorted(list(set(res["speaker_id"] for res in stt_results_list if res["speaker_id"] and res["speaker_id"] != "Unknown")))
        
        # More robust speaker mapping might be needed. This is a simple heuristic.
        if len(distinct_speaker_ids) > 0: speaker_role_map[distinct_speaker_ids[0]] = "Therapist" # Or speaker1
        if len(distinct_speaker_ids) > 1: speaker_role_map[distinct_speaker_ids[1]] = "Patient"   # Or speaker2
        # Any other speaker_ids will default to "Unknown" or their original ID if not mapped.

        api_transcript_for_db = []
        full_therapist_speech_list = []
        full_patient_speech_list = []
        for res_idx, res in enumerate(stt_results_list):
            mapped_role = speaker_role_map.get(res["speaker_id"])
            if mapped_role not in ["Therapist", "Patient"]:
                logger.warning(f"[STT TASK {self.request.id}] Speaker ID '{res['speaker_id']}' not recognized. Defaulting to 'Unknown'.")
                continue # Skip this entry NOTE: UNDER THE ASSUMPTION THAT ONLY 2 PEOPLE ARE IN A SESSION!!
            role = speaker_role_map.get(res["speaker_id"], f"Speaker {res['speaker_id']}" if res["speaker_id"] != "Unknown" else f"UnknownSpeaker_{res_idx+1}")
            text = res["text"]
            timestamp_str = format_timestamp_from_ticks(res["offset_ticks"])
            logger.debug(f"Converting offset_ticks={res['offset_ticks']} to timestamp={timestamp_str}")
            api_transcript_for_db.append({"speaker": role, "text": text, "timestamp": timestamp_str})
            if role == "Therapist": full_therapist_speech_list.append(text)
            elif role == "Patient": full_patient_speech_list.append(text)

        # 4. Update Database
        processing_item_db_update = session_processing_data_container.read_item(item=processing_id, partition_key=processing_id) # Re-read to avoid optimistic concurrency issues if any
        processing_item_db_update["transcript_processed"] = api_transcript_for_db
        # processing_item_db_update["therapist_speech_stt"] = " ".join(full_therapist_speech_list).strip()
        # processing_item_db_update["patient_speech_stt"] = " ".join(full_patient_speech_list).strip()
        processing_item_db_update["status"] = "COMPLETED_PENDING_REVIEW"
        processing_item_db_update["stt_processed_at"] = datetime.now(timezone.utc).isoformat()
        session_processing_data_container.replace_item(item=processing_item_db_update["id"], body=processing_item_db_update)

        header_item_final_update = session_headers_container.read_item(item=processing_id, partition_key=therapist_id_for_header_pk)
        header_item_final_update["status"] = "COMPLETED_PENDING_REVIEW"
        header_item_final_update["last_updated_at"] = processing_item_db_update["stt_processed_at"]
        session_headers_container.replace_item(item=header_item_final_update["id"], body=header_item_final_update)

        # 5. Send FCM Notification (if therapist device is registered)
        fcm_title = "Session Ready for Review"
        fcm_body = f"Transcript for session with {processing_item_db_update.get('patient_name', 'patient')} on {processing_item_db_update.get('session_date', 'unknown date')} is ready."
        fcm_data_payload = {"notification_type": "SESSION_READY", "dataId": processing_id} # Changed type for consistency
        try:
            # PK for devices_container is user_id (therapist_id_for_header_pk)
            therapist_device = devices_container.read_item(item=therapist_id_for_header_pk, partition_key=therapist_id_for_header_pk)
            if therapist_device and therapist_device.get("fcm_token"):
                send_fcm_notification(
                    project_id=config.FIREBASE_PROJECT_ID,
                    device_token=therapist_device["fcm_token"],
                    title=fcm_title,
                    body=fcm_body,
                    data_payload=fcm_data_payload
                )
                logger.info(f"[STT TASK {self.request.id}] FCM notification sent to therapist {therapist_id_for_header_pk} for session {processing_id}.")
            else:
                logger.info(f"[STT TASK {self.request.id}] No FCM token for therapist {therapist_id_for_header_pk}, notification not sent for session {processing_id}.")
        except cosmos_exceptions.CosmosResourceNotFoundError:
            logger.warning(f"[STT TASK {self.request.id}] No device registered for therapist {therapist_id_for_header_pk} for FCM (session {processing_id}).")
        except Exception as e_fcm: 
            logger.error(f"[STT TASK {self.request.id}] Error sending FCM for session {processing_id}: {e_fcm}", exc_info=True)
        
        # 6. Clean up temporary files
        if downloaded_raw_audio_path and os.path.exists(downloaded_raw_audio_path):
            try: 
                os.remove(downloaded_raw_audio_path)
                logger.info(f"[STT TASK {self.request.id}] Cleaned up temp file: {downloaded_raw_audio_path}")
            except Exception as e_clean: 
                logger.error(f"[STT TASK {self.request.id}] Error cleaning up {downloaded_raw_audio_path}: {e_clean}")


        logger.info(f"[STT TASK {self.request.id}] Successfully processed STT for {processing_id}")
        return {"status": "SUCCESS", "processing_id": processing_id, "message": "STT complete, ready for review."}

    except Exception as e: # Broader exception handling
        task_id = self.request.id or "UNKNOWN_TASK_ID"
        logger.error(f"[STT TASK {task_id} ERROR] Main processing failed for {processing_id}: {e}", exc_info=True)
        try: 
            # Read processing_item, PK is processing_id
            proc_item_fail = session_processing_data_container.read_item(item=processing_id, partition_key=processing_id)
            proc_item_fail["status"] = "FAILED_STT_PROCESSING"
            proc_item_fail["error_message_stt"] = str(e)[:1000] # Store error
            proc_item_fail["task_stt_id"] = task_id
            session_processing_data_container.replace_item(item=proc_item_fail["id"], body=proc_item_fail)
            
            therapist_id_for_header_pk_err = proc_item_fail.get("therapist_id")
            if therapist_id_for_header_pk_err:
                try:
                    head_item_fail = session_headers_container.read_item(item=processing_id, partition_key=therapist_id_for_header_pk_err)
                    head_item_fail["status"] = "FAILED_STT_PROCESSING"
                    head_item_fail["last_updated_at"] = datetime.now(timezone.utc).isoformat()
                    session_headers_container.replace_item(item=head_item_fail["id"], body=head_item_fail)
                except cosmos_exceptions.CosmosResourceNotFoundError:
                     logger.error(f"[STT TASK {task_id} DB_ERROR] Header item {processing_id} with PK {therapist_id_for_header_pk_err} not found for FAILED_STT update.")
                except Exception as db_e_h_final:
                    logger.error(f"[STT TASK {task_id} DB_ERROR] Could not update FAILED_STT status for header {processing_id}: {db_e_h_final}", exc_info=True)
            else:
                logger.error(f"[STT TASK {task_id} DB_ERROR] Therapist ID missing in failed processing_item {processing_id}. Cannot update header.")

        except cosmos_exceptions.CosmosResourceNotFoundError:
            logger.error(f"[STT TASK {task_id} DB_ERROR] Processing item {processing_id} not found during error handling.")
        except Exception as db_e_final:
            logger.error(f"[STT TASK {task_id} DB_ERROR] Could not update FAILED_STT status for processing_item {processing_id}: {db_e_final}", exc_info=True)
        
        # Celery will retry based on max_retries if not a soft timeout or specific non-retryable error
        raise # Re-raise the original exception to trigger Celery's retry mechanism or mark as failed
    finally:
        # Ensure temporary files are cleaned up
        for path_to_clean in [downloaded_raw_audio_path, converted_wav_path]:
            if path_to_clean and os.path.exists(path_to_clean):
                try: 
                    os.remove(path_to_clean)
                    logger.info(f"[STT TASK {self.request.id if self.request else 'N/A'}] Cleaned up temp file: {path_to_clean}")
                except Exception as e_clean: 
                    logger.error(f"[STT TASK {self.request.id if self.request else 'N/A'}] Error cleaning up {path_to_clean}: {e_clean}")
        logger.info(f"[STT TASK {self.request.id if self.request else 'N/A'}] Cleanup for {processing_id} finished.")

@celery.task(bind=True, max_retries=3, default_retry_delay=60, acks_late=True, time_limit=300)
def finalize_session_task(self, processing_id: str):
    logger.info(f"[FINALIZE TASK {self.request.id}] Starting for Processing ID: {processing_id}")
    try:
        processing_item = session_processing_data_container.read_item(item=processing_id, partition_key=processing_id)
        therapist_id_pk = processing_item["therapist_id"]

        if processing_item.get("status") != "PENDING_FINAL_SENTIMENT_ANALYSIS":
            logger.warning(f"[FINALIZE TASK] Item {processing_id} not PENDING_FINAL_SENTIMENT_ANALYSIS. Status: {processing_item.get('status')}. Skipping.")
            return {"status": "SKIPPED", "message": "Not in correct state for finalization."}

        final_edited_transcript_segments = processing_item.get("final_transcript_edited_payload", [])
        if not isinstance(final_edited_transcript_segments, list):
            logger.error(f"[FINALIZE TASK] Final transcript payload for {processing_id} is malformed or not a list.")
            raise ValueError("Final transcript payload is malformed.")

        # Extract only patient speech for Azure Text Analytics
        patient_speech_texts_for_sentiment = [
            seg.get("text", "") for seg in final_edited_transcript_segments
            if isinstance(seg, dict) and seg.get("speaker", "").upper() == "PATIENT" and seg.get("text", "").strip()
        ]
        
        sdk_sentiment_results = []
        if patient_speech_texts_for_sentiment:
            try:
                sdk_sentiment_results = analyze_sentiment(patient_speech_texts_for_sentiment)
            except Exception as e_sentiment:
                logger.error(f"[FINALIZE TASK] Sentiment analysis failed for {processing_id}: {e_sentiment}", exc_info=True)
                # Decide if to proceed with 0 scores or fail the task. For now, proceed with defaults.
        
        final_sentiment_scores_list = []
        patient_sentiment_idx = 0
        for segment in final_edited_transcript_segments:
            if not isinstance(segment, dict): # Skip malformed segments
                logger.warning(f"[FINALIZE TASK] Skipping malformed segment in transcript for {processing_id}: {segment}")
                continue

            speaker = segment.get("speaker", "Unknown")
            text = segment.get("text", "")
            timestamp = segment.get("timestamp", "0:00:00.000") # Default timestamp if missing

            entry = {
                "speaker": speaker,
                "text": text,
                "timestamp": timestamp,
                "positive": 0.0,
                "neutral": 1.0, # Default to neutral
                "negative": 0.0
            }

            if speaker.upper() == "PATIENT" and text.strip():
                if patient_sentiment_idx < len(sdk_sentiment_results):
                    score = sdk_sentiment_results[patient_sentiment_idx]
                    entry["positive"] = round(score.positive, 3)
                    entry["neutral"] = round(score.neutral, 3)
                    entry["negative"] = round(score.negative, 3)
                    # Normalize if Azure somehow doesn't sum to 1 (it should)
                    sum_scores = entry["positive"] + entry["neutral"] + entry["negative"]
                    if sum_scores > 0 and abs(sum_scores - 1.0) > 0.01 : # Allow small epsilon
                        entry["positive"] = round(entry["positive"] / sum_scores, 3)
                        entry["neutral"] = round(entry["neutral"] / sum_scores, 3)
                        entry["negative"] = round(1.0 - entry["positive"] - entry["neutral"], 3) # Ensure sum is 1

                    patient_sentiment_idx += 1
                else:
                    logger.warning(f"[FINALIZE TASK] Mismatch between patient segments and sentiment results for {processing_id}. Using default for: {text}")
            
            final_sentiment_scores_list.append(entry)

        overall_pos, overall_neu, overall_neg = 0.0, 1.0, 0.0 # Default to neutral overall
        if sdk_sentiment_results: # Calculate overall only from actual analyzed sentiments
            num_analyzed = len(sdk_sentiment_results)
            if num_analyzed > 0:
                overall_pos = round(sum(s.positive for s in sdk_sentiment_results) / num_analyzed, 3)
                overall_neu = round(sum(s.neutral for s in sdk_sentiment_results) / num_analyzed, 3)
                overall_neg = round(sum(s.negative for s in sdk_sentiment_results) / num_analyzed, 3)
                
                # Normalize overall scores
                total_overall_sent = overall_pos + overall_neu + overall_neg
                if total_overall_sent > 0 and abs(total_overall_sent - 1.0) > 0.01: # Allow small epsilon
                    overall_pos = round(overall_pos / total_overall_sent, 3)
                    overall_neu = round(overall_neu / total_overall_sent, 3)
                    overall_neg = round(1.0 - overall_pos - overall_neu, 3) # Ensure sum is 1.000
        try:
            patient_full_doc = users_container.read_item(item=processing_item["patient_id"], partition_key=processing_item["patient_id"])
            # patinet document doesnt exist, thus we add what we can and know
            patinet_id = processing_item["patient_id"]
            patient_name = patient_full_doc.get("userFullName")
            patient_email = patient_full_doc.get("email")
            patient_date_of_birth =  patient_full_doc.get("dateOfBirth")

        except cosmos_exceptions.CosmosResourceNotFoundError:
            logger.error(f"[FINALIZE TASK] Patient document not found for {processing_item['patient_id']}. Cannot finalize session.")
            patinet_id = processing_item["patient_id"]
            patient_name = processing_item.get("patient_name", "Unknown Patient")
            patient_email = processing_item.get("patient_email", "Unknown Email")
            patient_date_of_birth = processing_item.get("patient_date_of_birth", "99-99-9999") # Default if not set


        therapist_full_doc = users_container.read_item(item=therapist_id_pk, partition_key=therapist_id_pk)

        # Prepare the session_details_container document
        final_details_doc = {
            "id": processing_id, "sessionId": processing_id, # PK for this container
            "therapist_id": therapist_id_pk,
            "therapist_name": therapist_full_doc.get("userFullName"),
            "therapist_email": therapist_full_doc.get("email"),
            "patient_id": patinet_id,
            "patient_name": patient_name,
            "patient_email": patient_email,
            "patient_date_of_birth": patient_date_of_birth,
            "session_date": processing_item.get("session_date"), # Should be YYYY-MM-DD
            "summary": processing_item.get("summary_final_edit", processing_item.get("summary_initial","No summary provided.")),
            "timed_notes": processing_item.get("timed_notes", processing_item.get("timed_notes", [])),
            "general_notes": processing_item.get("general_notes", processing_item.get("general_notes", [])),
            "positive": overall_pos, "neutral": overall_neu, "negative": overall_neg,
            "sentiment_scores": final_sentiment_scores_list, # Use the new list
            "finalized_at_timestamp": datetime.now(timezone.utc).isoformat()
        }
        session_details_container.upsert_item(body=final_details_doc)
        logger.info(f"[FINALIZE TASK] Final session details stored for {processing_id} with new sentiment_scores structure.")

        # Update header
        header_item = session_headers_container.read_item(item=processing_id, partition_key=therapist_id_pk)
        header_item["status"] = "FINALIZED"
        header_item["overall_sentiment_positive"] = overall_pos
        header_item["overall_sentiment_neutral"] = overall_neu
        header_item["overall_sentiment_negative"] = overall_neg
        header_item["summary_preview"] = final_details_doc["summary"][:150] 
        header_item["last_updated_at"] = final_details_doc["finalized_at_timestamp"]
        header_item["finalized_session_details_id"] = processing_id # Link to the details doc
        session_headers_container.replace_item(item=header_item["id"], body=header_item)
        logger.info(f"[FINALIZE TASK] Session header updated to FINALIZED for {processing_id}")

        # Archive processing_item
        processing_item["status"] = "ARCHIVED_FINALIZED"
        processing_item["archived_at"] = datetime.now(timezone.utc).isoformat()
        processing_item.pop("final_transcript_edited_payload", None) # Remove large payload
        # processing_item.pop("transcript_processed", None) # Optionally remove STT transcript too
        session_processing_data_container.replace_item(item=processing_item["id"], body=processing_item)
        logger.info(f"[FINALIZE TASK] Processing item {processing_id} archived.")
        
        return {"status": "SUCCESS", "processing_id": processing_id, "message": "Session finalized."}

    except cosmos_exceptions.CosmosResourceNotFoundError as e_crnf:
        logger.error(f"[FINALIZE TASK {self.request.id} DB_NOT_FOUND] Resource not found for {processing_id}: {e_crnf}", exc_info=True)
        # No specific item to mark as failed if it's not found.
        raise # Let Celery handle retry if applicable
    except Exception as e: # Broader exception handling
        logger.error(f"[FINALIZE TASK {self.request.id} ERROR] Finalization failed for {processing_id}: {e}", exc_info=True)
        therapist_id_pk_err_val = processing_id 
        try:
            # Attempt to read processing_item to get therapist_id if not already set
            # This is a best-effort, it might fail if processing_id itself is problematic
            if 'therapist_id_pk' not in locals() or not therapist_id_pk:
                temp_proc_item = session_processing_data_container.read_item(item=processing_id, partition_key=processing_id)
                therapist_id_pk_err_val = temp_proc_item["therapist_id"]
        except Exception:
            logger.warning(f"[FINALIZE TASK DB_ERROR] Could not determine therapist_id for error reporting on header for {processing_id}")

        try: # Try to mark header as failed
            header_err = session_headers_container.read_item(item=processing_id, partition_key=therapist_id_pk_err_val)
            if header_err.get("status") != "FINALIZED": # Don't overwrite if somehow finalized elsewhere
                header_err["status"] = "FAILED_FINALIZATION"
                header_err["last_updated_at"] = datetime.now(timezone.utc).isoformat()
                session_headers_container.replace_item(item=header_err["id"], body=header_err)
        except Exception as db_e_h: logger.error(f"[FINALIZE TASK DB_ERROR] Could not update header to FAILED for {processing_id} (PK tried: {therapist_id_pk_err_val}): {db_e_h}")
        
        try: # Try to mark processing_item as failed if not already archived
            proc_item_fail = session_processing_data_container.read_item(item=processing_id, partition_key=processing_id)
            if proc_item_fail.get("status") not in ["ARCHIVED_FINALIZED", "FAILED_FINALIZATION"]:
                proc_item_fail["status"] = "FAILED_FINALIZATION"
                proc_item_fail["error_message_finalization"] = str(e)[:1000] # Store snippet of error
                session_processing_data_container.replace_item(item=proc_item_fail["id"], body=proc_item_fail)
        except cosmos_exceptions.CosmosResourceNotFoundError: 
            logger.warning(f"[FINALIZE TASK DB_ERROR] Processing item {processing_id} not found during error handling, might be already archived or deleted.")
        except Exception as db_e_p: 
            logger.error(f"[FINALIZE TASK DB_ERROR] Could not update processing_item to FAILED for {processing_id}: {db_e_p}")
        raise # Re-raise the original exception for Celery to handle (e.g., retry)


def structure_dialogue_turns_with_initial_sentiment(flat_transcript_segments, initial_patient_sentiments):
    """
    Transforms a flat list of transcript segments into dialogue turns.
    Initial patient sentiments are matched to patient utterances.
    Output: List of objects similar to SentimentScoreEntry for editing.
    """
    dialogue_turns = []
    patient_sentiment_idx = 0
    i = 0
    while i < len(flat_transcript_segments):
        turn_entry = {
            "therapist_sentence": None, "therapist_sentence_time": None,
            "patient_sentence": None, "patient_sentence_time": None,
            "positive": 0.0, "neutral": 1.0, "negative": 0.0 # Default sentiment
        }
        current_segment = flat_transcript_segments[i]
        is_therapist = current_segment["speaker"].upper() == "THERAPIST"
        is_patient = current_segment["speaker"].upper() == "PATIENT"

        if is_therapist:
            turn_entry["therapist_sentence"] = current_segment["text"]
            turn_entry["therapist_sentence_time"] = current_segment["timestamp"]
            i += 1
            # Check if next is patient for the same "turn"
            if i < len(flat_transcript_segments) and flat_transcript_segments[i]["speaker"].upper() == "PATIENT":
                patient_segment = flat_transcript_segments[i]
                turn_entry["patient_sentence"] = patient_segment["text"]
                turn_entry["patient_sentence_time"] = patient_segment["timestamp"]
                if patient_segment["text"].strip() and patient_sentiment_idx < len(initial_patient_sentiments):
                    score = initial_patient_sentiments[patient_sentiment_idx]
                    turn_entry["positive"] = round(score.positive, 3)
                    turn_entry["neutral"] = round(score.neutral, 3)
                    turn_entry["negative"] = round(score.negative, 3)
                    patient_sentiment_idx += 1
                i += 1
        elif is_patient:
            turn_entry["patient_sentence"] = current_segment["text"]
            turn_entry["patient_sentence_time"] = current_segment["timestamp"]
            if current_segment["text"].strip() and patient_sentiment_idx < len(initial_patient_sentiments):
                score = initial_patient_sentiments[patient_sentiment_idx]
                turn_entry["positive"] = round(score.positive, 3)
                turn_entry["neutral"] = round(score.neutral, 3)
                turn_entry["negative"] = round(score.negative, 3)
                patient_sentiment_idx += 1
            i += 1
        else: # Unknown speaker or structure
            logger.warning(f"Unknown speaker '{current_segment['speaker']}' in STT output.")
            i += 1
            continue 
        
        if turn_entry["therapist_sentence"] or turn_entry["patient_sentence"]:
            dialogue_turns.append(turn_entry)
            
    return dialogue_turns

# def _validate_audio_file_integrity(file_path: str, file_ext: str, task_id: str) -> bool:
#     """
#     Validate audio file integrity before processing.
    
#     Args:
#         file_path: Path to the audio file
#         file_ext: File extension (m4a, mp3, wav, etc.)
#         task_id: Task ID for logging
        
#     Returns:
#         True if file appears valid, False otherwise
#     """
#     try:
#         file_size = os.path.getsize(file_path)
#         if file_size < 100:  # File too small to be valid audio
#             logger.error(f"[STT TASK {task_id}] Audio file too small: {file_size} bytes")
#             return False
            
#         # For M4A/MP4 files, check for essential atoms
#         if file_ext in ['m4a', 'mp4', 'mov']:
#             return _validate_mp4_file_structure(file_path, task_id)
        
#         # For other formats, try basic ffprobe validation
#         return _validate_with_ffprobe(file_path, task_id)
        
#     except Exception as e:
#         logger.error(f"[STT TASK {task_id}] File validation error: {e}")
#         return False

def _validate_mp4_file_structure(file_path: str, task_id: str) -> bool:
    """
    Validate MP4/M4A file structure by checking for required atoms.
    
    Args:
        file_path: Path to the MP4/M4A file
        task_id: Task ID for logging
        
    Returns:
        True if file has required structure, False otherwise
    """
    try:
        with open(file_path, 'rb') as f:
            # Read first 64 bytes to look for file type box (ftyp)
            header = f.read(64)
            if b'ftyp' not in header:
                logger.error(f"[STT TASK {task_id}] No ftyp atom found in MP4/M4A file")
                return False
            
            # Look for moov atom in the file
            f.seek(0)
            file_data = f.read(8192)  # Read first 8KB to look for moov
            if b'moov' not in file_data:
                # Try reading more of the file
                f.seek(0)
                chunk_size = 65536  # 64KB chunks
                found_moov = False
                while True:
                    chunk = f.read(chunk_size)
                    if not chunk:
                        break
                    if b'moov' in chunk:
                        found_moov = True
                        break
                
                if not found_moov:
                    logger.error(f"[STT TASK {task_id}] No moov atom found in MP4/M4A file - file appears incomplete")
                    return False
            
            logger.info(f"[STT TASK {task_id}] MP4/M4A file structure validation passed")
            return True
            
    except Exception as e:
        logger.error(f"[STT TASK {task_id}] MP4/M4A validation error: {e}")
        return False

def _validate_with_ffprobe(file_path: str, task_id: str) -> bool:
    """
    Validate audio file using ffprobe.
    
    Args:
        file_path: Path to the audio file
        task_id: Task ID for logging
        
    Returns:
        True if ffprobe can read the file, False otherwise
    """
    try:
        result = subprocess.run([
            'ffprobe', '-v', 'quiet', '-print_format', 'json', 
            '-show_format', '-show_streams', file_path
        ], capture_output=True, text=True, timeout=10)
        
        if result.returncode == 0:
            # Try to parse the JSON output
            probe_data = json.loads(result.stdout)
            if 'streams' in probe_data and len(probe_data['streams']) > 0:
                logger.info(f"[STT TASK {task_id}] ffprobe validation passed")
                return True
        
        logger.error(f"[STT TASK {task_id}] ffprobe validation failed: {result.stderr}")
        return False
        
    except (subprocess.TimeoutExpired, subprocess.CalledProcessError, json.JSONDecodeError) as e:
        logger.error(f"[STT TASK {task_id}] ffprobe validation error: {e}")
        return False

def _convert_audio_with_fallback(file_path: str, file_ext: str, task_id: str) -> AudioSegment:
    """
    Convert audio file to AudioSegment with multiple fallback strategies.
    
    Args:
        file_path: Path to the audio file
        file_ext: File extension
        task_id: Task ID for logging
        
    Returns:
        AudioSegment object
        
    Raises:
        Exception if all conversion methods fail
    """
    conversion_errors = []
    
    # Strategy 1: Let pydub auto-detect format
    try:
        logger.info(f"[STT TASK {task_id}] Trying auto-detection conversion")
        audio_segment = AudioSegment.from_file(file_path)
        logger.info(f"[STT TASK {task_id}] Auto-detection conversion successful")
        return audio_segment
    except Exception as e:
        conversion_errors.append(f"Auto-detection: {str(e)}")
        logger.warning(f"[STT TASK {task_id}] Auto-detection failed: {e}")
    
    # Strategy 2: Explicitly specify format
    try:
        logger.info(f"[STT TASK {task_id}] Trying explicit format conversion: {file_ext}")
        audio_segment = AudioSegment.from_file(file_path, format=file_ext)
        logger.info(f"[STT TASK {task_id}] Explicit format conversion successful")
        return audio_segment
    except Exception as e:
        conversion_errors.append(f"Explicit format ({file_ext}): {str(e)}")
        logger.warning(f"[STT TASK {task_id}] Explicit format conversion failed: {e}")
    
    # Strategy 3: Try common alternative formats
    alternative_formats = ['mp4', 'm4a', 'mp3', 'wav', 'aac']
    for fmt in alternative_formats:
        if fmt == file_ext:
            continue  # Already tried
        try:
            logger.info(f"[STT TASK {task_id}] Trying alternative format: {fmt}")
            audio_segment = AudioSegment.from_file(file_path, format=fmt)
            logger.info(f"[STT TASK {task_id}] Alternative format conversion successful: {fmt}")
            return audio_segment
        except Exception as e:
            conversion_errors.append(f"Alternative format ({fmt}): {str(e)}")
            logger.warning(f"[STT TASK {task_id}] Alternative format {fmt} failed: {e}")
    
    # Strategy 4: Try using ffmpeg directly to repair and convert
    try:
        logger.info(f"[STT TASK {task_id}] Trying ffmpeg repair and conversion")
        repaired_file = _repair_audio_with_ffmpeg(file_path, task_id)
        audio_segment = AudioSegment.from_file(repaired_file, format='wav')
        logger.info(f"[STT TASK {task_id}] ffmpeg repair conversion successful")
        # Clean up the temporary repaired file
        try:
            os.unlink(repaired_file)
        except:
            pass
        return audio_segment
    except Exception as e:
        conversion_errors.append(f"ffmpeg repair: {str(e)}")
        logger.warning(f"[STT TASK {task_id}] ffmpeg repair conversion failed: {e}")
    
    # All strategies failed
    error_summary = "; ".join(conversion_errors)
    raise Exception(f"All audio conversion strategies failed. Errors: {error_summary}")

def _repair_audio_with_ffmpeg(input_path: str, task_id: str) -> str:
    """
    Attempt to repair audio file using ffmpeg and convert to WAV.
    
    Args:
        input_path: Path to the corrupted audio file
        task_id: Task ID for logging
        
    Returns:
        Path to the repaired WAV file
        
    Raises:
        Exception if repair fails
    """
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp_file:
        output_path = tmp_file.name
    
    try:
        # Use ffmpeg with error recovery options
        cmd = [
            'ffmpeg', '-y',  # Overwrite output file
            '-err_detect', 'ignore_err',  # Ignore errors
            '-i', input_path,
            '-c:a', 'pcm_s16le',  # Convert to uncompressed WAV
            '-ar', '16000',  # 16kHz sample rate
            '-ac', '1',  # Mono
            '-f', 'wav',
            output_path
        ]
        
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        
        if result.returncode == 0 and os.path.exists(output_path) and os.path.getsize(output_path) > 0:
            logger.info(f"[STT TASK {task_id}] ffmpeg repair successful")
            return output_path
        else:
            logger.error(f"[STT TASK {task_id}] ffmpeg repair failed: {result.stderr}")
            raise Exception(f"ffmpeg repair failed: {result.stderr}")
            
    except subprocess.TimeoutExpired:
        logger.error(f"[STT TASK {task_id}] ffmpeg repair timed out")
        raise Exception("ffmpeg repair timed out")
    except Exception as e:
        logger.error(f"[STT TASK {task_id}] ffmpeg repair error: {e}")
        # Clean up failed output file
        try:
            os.unlink(output_path)
        except:
            pass
        raise

def _mark_processing_failed(processing_id: str, error_message: str, task_id: str):
    """
    Mark processing item as failed with detailed error information.
    
    Args:
        processing_id: ID of the processing item
        error_message: Detailed error message
        task_id: Task ID for logging
    """
    try:
        # Read current processing item
        processing_item = session_processing_data_container.read_item(
            item=processing_id, 
            partition_key=processing_id
        )
        
        # Update status and error details
        processing_item["status"] = "FAILED"
        processing_item["error_message"] = error_message
        processing_item["failed_at"] = datetime.now(timezone.utc).isoformat()
        
        # Update the item
        session_processing_data_container.replace_item(
            item=processing_id,
            body=processing_item
        )
        
        logger.info(f"[STT TASK {task_id}] Marked processing {processing_id} as FAILED")
        
    except Exception as e:
        logger.error(f"[STT TASK {task_id}] Failed to mark processing as failed: {e}")
