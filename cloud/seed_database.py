# seed_database.py
import uuid
import bcrypt
from datetime import datetime, timedelta, timezone
import os
import json
import random
import logging # Use standard logging

import config

from dotenv import load_dotenv
load_dotenv()

from azure.cosmos import CosmosClient, exceptions as cosmos_exceptions, PartitionKey

# --- Configuration & Logging ---
logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

COSMOS_URI = config.COSMOS_URI
COSMOS_KEY = config.COSMOS_KEY
COSMOS_DATABASE_NAME = config.COSMOS_DATABASE_NAME

PLAIN_PASSWORD = "6!'DA;ozi5j2" # Default password for seeded users

if not all([COSMOS_URI, COSMOS_KEY]):
    logger.critical("FATAL: COSMOS_URI and COSMOS_KEY environment variables must be set.")
    exit(1)

# --- Initialize Cosmos DB Client ---
try:
    client = CosmosClient(COSMOS_URI, credential=COSMOS_KEY)
    database = client.create_database_if_not_exists(id=COSMOS_DATABASE_NAME)
    logger.info(f"Database '{COSMOS_DATABASE_NAME}' is ready.")
    
    # Ensure containers exist or create them (idempotent)
    # Define partition keys as per your design
    users_container = database.create_container_if_not_exists(
        id="users_container", partition_key=PartitionKey(path="/id")
    )
    user_tokens_container = database.create_container_if_not_exists(
        id="user_tokens_container", partition_key=PartitionKey(path="/id"), default_ttl=-1 # Use TTL for tokens
    )
    session_headers_container = database.create_container_if_not_exists(
        id="session_headers_container", partition_key=PartitionKey(path="/therapist_id")
    )
    session_processing_data_container = database.create_container_if_not_exists(
        id="session_processing_data_container", partition_key=PartitionKey(path="/id")
    )
    session_details_container = database.create_container_if_not_exists(
        id="session_details_container", partition_key=PartitionKey(path="/id")
    )
    devices_container = database.create_container_if_not_exists(
        id="devices_container", partition_key=PartitionKey(path="/user_id") 
    )
    
    logger.info("Successfully connected to Cosmos DB and ensured container clients.")
except cosmos_exceptions.CosmosHttpResponseError as e:
    logger.critical(f"FATAL: A Cosmos DB HTTP error occurred: {e}", exc_info=True)
    exit(1)
except Exception as e:
    logger.critical(f"FATAL: Error connecting to Cosmos DB or initializing containers: {e}", exc_info=True)
    exit(1)

# --- Helper Functions ---
def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')

def format_timestamp_from_ticks(ticks: int) -> str:
    if ticks is None: return "00:00:00.000" # Ensure HH:MM:SS.mmm
    seconds_float = ticks / 10_000_000.0
    td = timedelta(seconds=seconds_float)
    total_seconds_int = int(td.total_seconds())
    hours = total_seconds_int // 3600
    minutes = (total_seconds_int % 3600) // 60
    secs = total_seconds_int % 60
    milliseconds = int(round(td.microseconds / 1000)) # Round to nearest millisecond
    return f"{hours:02}:{minutes:02}:{secs:02}.{milliseconds:03}"


def generate_mock_transcript_sentences(num_turns=random.randint(8, 15)):
    segments, current_time_ticks = [], 0
    p_base = ["I've been feeling quite overwhelmed lately with my new project.", "The deadlines at work are causing a lot of stress and anxiety.", 
              "I find it hard to switch off in the evenings, my mind keeps racing.", "Sleep has been very disturbed, I wake up multiple times.", 
              "I tried that breathing exercise we discussed, it helped a bit to calm down.", "It's just hard to maintain focus when I'm feeling this anxious.",
              "I had a disagreement with my partner yesterday over household chores.", "I'm worried about the upcoming family gathering next month.",
              "I feel like I'm not good enough for my current role sometimes.", "The medication seems to be stabilizing my mood swings a bit."]
    t_base = ["Can you tell me more about what feels overwhelming regarding the project?", "How has this stress from work been manifesting for you physically or emotionally?", 
              "What are some of the thoughts that go through your mind when you're trying to switch off?", "That sounds difficult. What have you tried so far to improve your sleep?",
              "Let's explore that feeling of not being able to switch off more deeply.", "What does 'disturbed sleep' look like for you? Can you describe it?",
              "It's good you recognized the breathing exercise helped. What made it helpful in that moment?", "Focus can be challenging with a lot on your mind. Are there specific times it's worse?",
              "How did that disagreement with your partner make you feel?", "What are your specific concerns about the family gathering?"]
    random.shuffle(p_base); random.shuffle(t_base)
    
    for i in range(num_turns):
        therapist_text = random.choice(t_base)
        segments.append({"speaker": "Therapist", "text": therapist_text, "timestamp": format_timestamp_from_ticks(current_time_ticks)})
        current_time_ticks += random.randint(5, 15) * 10_000_000 # 5-15s for therapist
        
        patient_text = random.choice(p_base)
        segments.append({"speaker": "Patient", "text": patient_text, "timestamp": format_timestamp_from_ticks(current_time_ticks)})
        current_time_ticks += random.randint(10, 35) * 10_000_000 # 10-35s for patient
    return segments

def generate_mock_final_sentiment_scores(transcript_sentences):
    """
    Generates a list of sentiment score entries for a finalized session,
    matching the new structure: one entry per sentence.
    Sentiment is applied only to patient sentences.
    """
    final_sentiment_scores = []
    for segment in transcript_sentences:
        speaker = segment.get("speaker", "Unknown")
        text = segment.get("text", "")
        timestamp = segment.get("timestamp", "00:00:00.000")

        entry = {
            "speaker": speaker,
            "text": text,
            "timestamp": timestamp,
            "positive": 0.0,
            "neutral": 1.0, # Default for therapist or if sentiment fails
            "negative": 0.0
        }

        if speaker.upper() == "PATIENT" and text.strip():
            pos, neu, neg = random.uniform(0,1), random.uniform(0,1), random.uniform(0,1)
            total = pos + neu + neg
            if total > 0:
                entry["positive"] = round(pos/total, 3)
                entry["neutral"] = round(neu/total, 3)
                entry["negative"] = round(1.0 - entry["positive"] - entry["neutral"], 3) # Ensure sum is 1
            else: # Fallback if all randoms are 0
                entry["neutral"] = 1.0 
                entry["positive"] = 0.0
                entry["negative"] = 0.0
        
        final_sentiment_scores.append(entry)
    return final_sentiment_scores


# --- Data Creation Functions ---
def create_user_item(number, user_type="patient", password_plain=PLAIN_PASSWORD):
    uid = str(random.randint(100000000, 999999999)) # Random 9-digit ID in string format
    first_names_p = ["Alex", "Jamie", "Chris", "Pat", "Taylor", "Jordan", "Morgan", "Riley", "Casey", "Drew"]
    last_names_p = ["Miller", "Davis", "Garcia", "Wilson", "Martinez", "Anderson", "Thomas", "Jackson", "White", "Harris"]
    first_names_t = ["Dr. Evelyn", "Dr. Samuel", "Dr. Olivia", "Dr. Benjamin", "Dr. Sophia", "Dr. Liam", "Dr. Ava", "Dr. Noah", "Dr. Isabella", "Dr. Lucas"]
    last_names_t = ["Reed", "Carter", "Hayes", "Price", "Bennett", "Wood", "Barnes", "Ross", "Henderson", "Coleman"]

    if user_type == "therapist" and number == 1: # Test therapist
        fn, ln = "Dr. Olivia", "Hayes" # This will generate olivia.hayes1@example.health
    elif user_type == "therapist" and number == 2: # Test therapist
        fn, ln = "Dr. Alex", "Lebed" # This will generate Alex.Lebed2@example.health
    elif user_type == "patient" and number == 1: # Test patient 1 (for upload metadata if needed, or general use)
        fn, ln = "Jordan", "White"   # This will generate jordan.white1@example.health
    elif user_type == "patient" and number == 2: # Test patient 2 (for patient login)
        fn, ln = "Jamie", "Davis"    # This will generate jamie.davis2@example.health
    else: # Random for the rest
        if user_type == "patient":
            fn, ln = random.choice(first_names_p), random.choice(last_names_p)
        else: # therapist
            fn, ln = random.choice(first_names_t), random.choice(last_names_t)

    full_name_val = f"{fn} {ln}"
    email_val = f"{fn.lower().replace('dr. ', '')}.{ln.lower()}{number}@example.health"
    
    dob_year_start = 1960 if user_type == 'therapist' else 1975
    dob_year_end = 1985 if user_type == 'therapist' else 2003
    dob_dt = datetime(random.randint(dob_year_start, dob_year_end), random.randint(1,12), random.randint(1,28))
    
    return {
        "id": uid,
        "email": email_val,
        "password": hash_password(password_plain),
        "userFullName": full_name_val,
        "fullName": full_name_val, 
        "type": user_type.upper(),
        "dateOfBirth": dob_dt.strftime("%Y-%m-%d"),
        "pictureUrl": f"https://i.pravatar.cc/150?u={email_val}"
    }

def seed_users(num_patients=5, num_therapists=5):
    logger.info("\n--- Seeding Users ---")
    created_users = {"patients": [], "therapists": []}
    for utype, count in [("patient", num_patients), ("therapist", num_therapists)]:
        for i in range(1, count + 1):
            user_data = create_user_item(i, utype)
            try:
                users_container.upsert_item(user_data)
                created_users[f"{utype}s"].append(user_data)
                logger.info(f"  Upserted {utype}: {user_data['email']} (ID: {user_data['id']}) (Password: {PLAIN_PASSWORD})")
            except cosmos_exceptions.CosmosHttpResponseError as e:
                logger.error(f"  Error upserting {utype} {user_data['email']}: {e}")
    return created_users["patients"], created_users["therapists"]

def seed_sessions(therapists, patients, num_sessions_total=15, num_pending_review=5):
    if not therapists or not patients:
        logger.warning("Cannot seed sessions without therapists and patients.")
        return
    if len(patients) < 2 and len(therapists) > 1:
        logger.warning("Warning: Need at least 2 patients for optimal variety with multiple therapists.")

    logger.info("\n--- Seeding Sessions ---")
    
    num_finalized = num_sessions_total - num_pending_review
    if num_finalized < 0:
        logger.error("num_pending_review cannot exceed num_sessions_total.")
        return

    all_sessions_data = [] # To collect all session data before distributing states

    for i in range(num_sessions_total):
        therapist = therapists[i % len(therapists)] # Cycle through therapists

        # Assign patients to ensure variety for therapists and minimums for patients
        # This logic aims for each therapist to see at least two different patients if possible over 3 sessions
        if len(patients) >= 2:
            patient_indices_for_therapist = [(i // len(therapists) + j) % len(patients) for j in range(2)] # Basic rotation
            patient_idx_for_this_session = patient_indices_for_therapist[i % 2] # Alternate between the two assigned for this therapist's batch
            patient = patients[patient_idx_for_this_session]
        else: # Only one patient
            patient = patients[0]
        
        session_id = str(uuid.uuid4())
        session_date_dt = datetime.now(timezone.utc) - timedelta(days=random.randint(2, 180))
        session_date_db_fmt = session_date_dt.strftime("%Y-%m-%d")
        
        display_date_for_summary = session_date_dt.strftime("%Y-%m-%d")

        uploaded_at_iso = (session_date_dt - timedelta(hours=random.randint(1,24))).isoformat()
        
        session_title = f"Session: {patient['userFullName']} ({display_date_for_summary})"
        summary_base = f"Session on {display_date_for_summary} with {patient['userFullName']}."
        summary_preview = summary_base + f" Explored {random.choice(['coping mechanisms', 'recent events', 'emotional state'])}."

        all_sessions_data.append({
            "session_id": session_id, "therapist": therapist, "patient": patient,
            "session_date_db_fmt": session_date_db_fmt, "uploaded_at_iso": uploaded_at_iso,
            "title": session_title, "summary_preview": summary_preview,
            "summary_initial": summary_base + f" Initial notes: Patient seemed {random.choice(['engaged', 'distracted', 'anxious'])}. Discussed {random.choice(['goals', 'challenges', 'progress'])}.",
            "general_notes": [f"Patient reported {random.choice(['good', 'poor', 'average'])} sleep.", "Followed up on action items."],
            "timed_notes": [
                {"timestamp": "00:03:15.500", "content": "Patient shared a recent experience."},
                {"timestamp": "00:12:45.120", "content": "Explored cognitive distortions related to the experience."}
            ]
        })

    random.shuffle(all_sessions_data) # Shuffle before assigning states

    for idx, data in enumerate(all_sessions_data):
        session_id, therapist, patient = data["session_id"], data["therapist"], data["patient"]
        session_date_db_fmt, uploaded_at_iso = data["session_date_db_fmt"], data["uploaded_at_iso"]
        title, summary_preview = data["title"], data["summary_preview"]

        is_pending_session = idx < num_pending_review # First N are pending

        header_doc_base = {
            "id": session_id, "sessionId": session_id,
            "therapist_id": therapist["id"], # This is the PK for session_headers_container
            "therapist_name": therapist["userFullName"], 
            "patient_id": patient["id"], "patient_name": patient["userFullName"],
            "session_date": session_date_db_fmt, "title": title, "summary_preview": summary_preview,
            "overall_sentiment_positive": 0.0, "overall_sentiment_neutral": 1.0, "overall_sentiment_negative": 0.0,
            "uploaded_at": uploaded_at_iso, "last_updated_at": uploaded_at_iso
        }

        original_audio_format = random.choice(['m4a', 'wav', 'mp3'])
        processing_doc_base = {
            "id": session_id, "header_id": session_id, # PK for this container
            "therapist_id": therapist["id"], "patient_id": patient["id"],
            "patient_name": patient["userFullName"], "session_date": session_date_db_fmt,
            "original_filename": f"session_audio_{session_id[:7]}.{original_audio_format}",
            "blob_storage_path": f"https://{os.getenv('AZURE_STORAGE_ACCOUNT_NAME','yourstgaccount')}.blob.core.windows.net/{config.AUDIO_UPLOAD_BLOB_CONTAINER_NAME}/{session_id}/audio.{original_audio_format}",
            "blob_container_name": config.AUDIO_UPLOAD_BLOB_CONTAINER_NAME, 
            "blob_name": f"{session_id}/audio.{original_audio_format}",
            "uploaded_at": uploaded_at_iso, 
            "summary_initial": data["summary_initial"],
            "general_notes": data["general_notes"],
            "timed_notes": data["timed_notes"],
        }

        if is_pending_session:
            header_doc = {**header_doc_base, "status": "COMPLETED_PENDING_REVIEW", "processing_id_link": session_id}
            stt_processed_time = (datetime.fromisoformat(uploaded_at_iso) + timedelta(minutes=random.randint(10,45))).isoformat()
            header_doc["last_updated_at"] = stt_processed_time
            
            raw_stt_transcript = generate_mock_transcript_sentences()
            
            processing_doc = {
                **processing_doc_base, 
                "status": "COMPLETED_PENDING_REVIEW",
                "transcript_processed": raw_stt_transcript,
                "therapist_speech_stt": " ".join([s["text"] for s in raw_stt_transcript if s["speaker"]=="Therapist"]),
                "patient_speech_stt": " ".join([s["text"] for s in raw_stt_transcript if s["speaker"]=="Patient"]),
                "stt_processed_at": stt_processed_time
            }
            try:
                session_headers_container.upsert_item(header_doc)
                session_processing_data_container.upsert_item(processing_doc)
                logger.info(f"  Created PENDING_REVIEW session: {session_id} (T: {therapist['email']}, P: {patient['email']})")
            except Exception as e: logger.error(f"  Error creating PENDING_REVIEW session {session_id}: {e}", exc_info=True)
        
        else: # Finalized session
            header_doc = {**header_doc_base, "status": "FINALIZED", "finalized_session_details_id": session_id}
            finalized_timestamp = (datetime.fromisoformat(uploaded_at_iso) + timedelta(days=random.randint(1,3), hours=random.randint(1,10))).isoformat()
            header_doc["last_updated_at"] = finalized_timestamp

            mock_stt_transcript = generate_mock_transcript_sentences() # This is List<{"speaker": ..., "text":..., "timestamp":...}>
            final_edited_transcript_for_seed = mock_stt_transcript # Simulate edits are same as STT for seed data

            # Generate sentiment scores based on the new structure
            final_sentiment_scores_list = generate_mock_final_sentiment_scores(final_edited_transcript_for_seed)
            
            # Calculate overall sentiment from patient lines in the new structure
            patient_sentiments_from_final_list = [
                s for s in final_sentiment_scores_list 
                if s["speaker"].upper() == "PATIENT"
            ]

            if patient_sentiments_from_final_list:
                num_analyzed = len(patient_sentiments_from_final_list)
                avg_pos = sum(s['positive'] for s in patient_sentiments_from_final_list) / num_analyzed
                avg_neu = sum(s['neutral'] for s in patient_sentiments_from_final_list) / num_analyzed
                avg_neg = sum(s['negative'] for s in patient_sentiments_from_final_list) / num_analyzed
                
                total_avg_sent = avg_pos + avg_neu + avg_neg
                if total_avg_sent > 0 and abs(total_avg_sent - 1.0) > 0.01:
                    header_doc["overall_sentiment_positive"] = round(avg_pos / total_avg_sent, 3)
                    header_doc["overall_sentiment_neutral"] = round(avg_neu / total_avg_sent, 3)
                    header_doc["overall_sentiment_negative"] = round(1.0 - header_doc["overall_sentiment_positive"] - header_doc["overall_sentiment_neutral"], 3)
                else: # Already normalized or all zero
                    header_doc["overall_sentiment_positive"] = round(avg_pos, 3)
                    header_doc["overall_sentiment_neutral"] = round(avg_neu, 3)
                    header_doc["overall_sentiment_negative"] = round(avg_neg, 3)
            else: # No patient sentences, default overall sentiment
                header_doc["overall_sentiment_positive"] = 0.0
                header_doc["overall_sentiment_neutral"] = 1.0
                header_doc["overall_sentiment_negative"] = 0.0

            details_doc = {
                "id": session_id, "sessionId": session_id, 
                "therapist_id": therapist["id"], "therapist_name": therapist["userFullName"], "therapist_email": therapist["email"],
                "patient_id": patient["id"], "patient_name": patient["userFullName"], "patient_email": patient["email"],
                "patient_date_of_birth": patient.get("dateOfBirth"), 
                "session_date": session_date_db_fmt, 
                "summary": processing_doc_base["summary_initial"] + f" Final thoughts: Patient to work on {random.choice(['thought records', 'exposure exercises'])}.",
                "timed_notes": processing_doc_base["timed_notes"],
                "general_notes": processing_doc_base["general_notes"],
                "positive": header_doc["overall_sentiment_positive"], 
                "neutral": header_doc["overall_sentiment_neutral"],
                "negative": header_doc["overall_sentiment_negative"],
                "sentiment_scores": final_sentiment_scores_list, # Use new structure
                "finalized_at_timestamp": finalized_timestamp,
            }
            
            archived_processing_doc = {
                **processing_doc_base, "status": "ARCHIVED_FINALIZED",
                "transcript_processed": mock_stt_transcript, # Original STT
                # "therapist_speech_stt": " ".join([s["text"] for s in mock_stt_transcript if s["speaker"]=="Therapist"]), # Optional
                # "patient_speech_stt": " ".join([s["text"] for s in mock_stt_transcript if s["speaker"]=="Patient"]),   # Optional
                "stt_processed_at": (datetime.fromisoformat(uploaded_at_iso) + timedelta(minutes=random.randint(5,25))).isoformat(),
                "final_transcript_edited_payload": final_edited_transcript_for_seed, # What therapist submitted
                "therapist_final_edit_at": finalized_timestamp, # When therapist submitted edits
                "archived_at": datetime.now(timezone.utc).isoformat()
            }
            try:
                session_headers_container.upsert_item(header_doc)
                session_details_container.upsert_item(details_doc)
                session_processing_data_container.upsert_item(archived_processing_doc)
                logger.info(f"  Created FINALIZED session: {session_id} (T: {therapist['email']}, P: {patient['email']}) with new sentiment_scores structure.")
            except Exception as e: logger.error(f"  Error creating FINALIZED session {session_id}: {e}", exc_info=True)

def main():
    confirm = input(f"This script will ADD/UPDATE data in Cosmos DB '{COSMOS_DATABASE_NAME}' using URI '{COSMOS_URI}'. Are you sure? (yes/no): ")
    if confirm.lower() != 'yes':
        print("Seeding cancelled.")
        return

    num_therapists = 5
    num_patients = 5
    total_sessions_to_create = 15 
    num_sessions_pending_review = 5

    seeded_patients, seeded_therapists = seed_users(num_patients=num_patients, num_therapists=num_therapists)
    
    if not seeded_therapists or not seeded_patients:
        logger.error("User seeding failed or produced no users. Cannot seed sessions.")
        return
        
    seed_sessions(seeded_therapists, seeded_patients, 
                  num_sessions_total=total_sessions_to_create, 
                  num_pending_review=num_sessions_pending_review)

    logger.info("\n--- Database Seeding Attempt Complete ---")
    logger.info("Please verify data in Azure Portal Data Explorer.")
    logger.info(f"Passwords for seeded users are '{PLAIN_PASSWORD}'.")

if __name__ == "__main__":
    main()
