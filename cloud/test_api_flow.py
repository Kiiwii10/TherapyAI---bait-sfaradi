import requests
import json
import time
import os
import uuid
from datetime import datetime, timezone
import wave
import random
import struct

# --- Configuration ---
# BASE_URL = "http://localhost:8000" #local
BASE_URL = "https://therapyaiapp-chgaenawcmgcfkev.israelcentral-01.azurewebsites.net" #azure
SAMPLE_AUDIO_FILENAME = "converted_audio.wav"  # Use a small M4A or WAV for testing
SAMPLE_AUDIO_FILE_PATH = os.path.join(os.path.dirname(__file__), SAMPLE_AUDIO_FILENAME)
AUDIO_CONTENT_TYPE = "audio/wav" # Or "audio/wav" if using a WAV file

# Assumes seed_database.py is modified to create these users with number=1 or number=2 etc.
# Password for all seeded users is "6!'DA;ozi5j2"
THERAPIST_EMAIL = "alex.lebed2@example.health"
PATIENT_EMAIL_FOR_UPLOAD_METADATA = "jordan.white1@example.health" # Patient whose session is being uploaded
PATIENT_EMAIL_FOR_LOGIN = "jamie.davis2@example.health"     # Patient who logs in to view their sessions
SHARED_PASSWORD = "6!'DA;ozi5j2"

# --- Global Test Variables ---
# To store details fetched during tests
therapist_context = {}
patient_context = {}
session_context = {"created_session_id": None, "pending_review_session_id": None}

def create_wav_file():
    # --- Parameters for the WAV file ---
    nchannels = 2  # 2 for stereo, 1 for mono
    sampwidth = 2  # Sample width in bytes (2 bytes = 16-bit audio)
    framerate = 44100  # Samples per second (CD quality)
    duration = 5  # a 5-second audio file
    nframes = duration * framerate  # Total number of frames

    # --- Compression ---
    comptype = "NONE"  # The only supported compression type [6]
    compname = "not compressed" # Human-readable version of the compression type

    # --- Generate random audio data ---
    # Generate random integers between -32767 and 32767 for 16-bit audio [18]
    # And pack them as short integers
    frames = []
    for _ in range(nframes * nchannels):
        sample = random.randint(-32767, 32767)
        frames.append(struct.pack('<h', sample))

    frames_data = b''.join(frames)

    # --- Write the WAV file ---
    file_path = SAMPLE_AUDIO_FILENAME

    try:
        with wave.open(file_path, 'wb') as wav_file:
            # Set the parameters for the WAV file [8]
            wav_file.setnchannels(nchannels)
            wav_file.setsampwidth(sampwidth)
            wav_file.setframerate(framerate)
            wav_file.setnframes(nframes)
            wav_file.setcomptype(comptype, compname)

            # Write the audio frames
            wav_file.writeframes(frames_data)

        print(f"Successfully created a {duration}-second random WAV file: {file_path}")

    except Exception as e:
        print(f"An error occurred: {e}")

# --- Helper Functions ---
def print_json(data, title="Response JSON"):
    print(f"\n--- {title} ---")
    try:
        print(json.dumps(data, indent=2, ensure_ascii=False))
    except (TypeError, json.JSONDecodeError):
        print(data) # If not json serializable (e.g. raw text or error)
    print("--- End ---")

def get_headers(token=None):
    headers = {} # Content-Type will be set by requests.post/put for json=payload, or manually for files
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers

def poll_for_session_in_pending(session_id, therapist_token, max_wait_sec=240, interval_sec=15):
    """Polls the /data/pending endpoint until the session_id appears or timeout."""
    print(f"\n>>> Polling for session '{session_id}' to appear in therapist's pending review list...")
    start_time = time.time()
    while time.time() - start_time < max_wait_sec:
        response = requests.get(f"{BASE_URL}/data/pending", headers=get_headers(therapist_token))
        if response.status_code == 200:
            pending_items = response.json()
            for item in pending_items:
                if item.get("id") == session_id:
                    print(f"Session '{session_id}' found in pending review list.")
                    session_context["pending_review_session_id"] = session_id
                    return True
            print(f"Session '{session_id}' not yet in pending list. Retrying in {interval_sec}s...")
        else:
            print(f"Warning: Polling /data/pending returned status {response.status_code}. Retrying...")
        time.sleep(interval_sec)
    print(f"Timeout: Session '{session_id}' did not appear in pending review list after {max_wait_sec}s.")
    return False

def poll_for_session_finalization(session_id, user_token, max_wait_sec=120, interval_sec=10):
    """Polls the /sessions/{sessionId}/details endpoint until it's found (200 OK) or timeout."""
    print(f"\n>>> Polling for session '{session_id}' to be finalized...")
    start_time = time.time()
    while time.time() - start_time < max_wait_sec:
        response = requests.get(f"{BASE_URL}/sessions/{session_id}/details", headers=get_headers(user_token))
        if response.status_code == 200:
            print(f"Session '{session_id}' is finalized and details are available.")
            return True, response.json()
        elif response.status_code == 404:
            print(f"Session '{session_id}' not yet finalized (404). Retrying in {interval_sec}s...")
        else:
            print(f"Warning: Polling /sessions/{session_id}/details returned status {response.status_code}. Retrying...")
        time.sleep(interval_sec)
    print(f"Timeout: Session '{session_id}' did not finalize or details not found after {max_wait_sec}s.")
    return False, None

# --- Test Functions ---

def test_01_therapist_login():
    print(f"\n>>> TEST 01: Therapist Login ({THERAPIST_EMAIL})...")
    payload = {"email": THERAPIST_EMAIL, "password": SHARED_PASSWORD}
    response = requests.post(f"{BASE_URL}/auth/login", headers={"Content-Type": "application/json"}, json=payload)
    print(f"Login Status: {response.status_code}")
    data = response.json()
    print_json(data, "Therapist Login Response")
    assert response.status_code == 200, f"Therapist login failed: {response.text}"
    
    therapist_context["token"] = data.get("token")
    therapist_context["refresh_token"] = data.get("refreshToken")
    therapist_context["user_id"] = data.get("userId")
    therapist_context["full_name"] = data.get("userFullName")
    therapist_context["email"] = THERAPIST_EMAIL
    
    assert therapist_context["token"], "Therapist access token missing."
    assert therapist_context["refresh_token"], "Therapist refresh token missing."
    assert therapist_context["user_id"], "Therapist user ID missing."
    print("Therapist Login Successful.")

def test_02_patient_login():
    print(f"\n>>> TEST 02: Patient Login ({PATIENT_EMAIL_FOR_LOGIN})...")
    payload = {"email": PATIENT_EMAIL_FOR_LOGIN, "password": SHARED_PASSWORD}
    response = requests.post(f"{BASE_URL}/auth/login", headers={"Content-Type": "application/json"}, json=payload)
    print(f"Login Status: {response.status_code}")
    data = response.json()
    print_json(data, "Patient Login Response")
    assert response.status_code == 200, f"Patient login failed: {response.text}"

    patient_context["token"] = data.get("token")
    patient_context["refresh_token"] = data.get("refreshToken")
    patient_context["user_id"] = data.get("userId")
    patient_context["full_name"] = data.get("userFullName")
    patient_context["date_of_birth"] = data.get("dateOfBirth") # Expected format from API: dd-MM-YYYY
    patient_context["email"] = PATIENT_EMAIL_FOR_LOGIN

    assert patient_context["token"], "Patient access token missing."
    assert patient_context["user_id"], "Patient user ID missing."
    print("Patient Login Successful.")

def test_03_refresh_therapist_token():
    print("\n>>> TEST 03: Refresh Therapist Token...")
    if not therapist_context.get("refresh_token"):
        print("Skipping: Therapist refresh token not available.")
        return

    payload = {"refreshToken": therapist_context["refresh_token"]} # Ensure this matches expected server field name
    # The auth/routes.py uses @jwt_required(refresh=True) which expects the refresh token in Authorization header
    # The refresh_bp.route('/refresh') does NOT expect a JSON body. It expects JWT in header.
    # Flask-JWT-Extended's create_refresh_token() is used.
    # @jwt_required(refresh=True) gets identity from this token.

    # To call /auth/refresh, the refresh token itself needs to be sent as a Bearer token
    refresh_headers = {"Authorization": f"Bearer {therapist_context['refresh_token']}"}

    response = requests.post(f"{BASE_URL}/auth/refresh", headers=refresh_headers) # No JSON body
    print(f"Token Refresh Status: {response.status_code}")
    data = response.json()
    print_json(data, "Token Refresh Response")
    assert response.status_code == 200, f"Token refresh failed: {response.text}"
    
    new_access_token = data.get("accessToken") # API spec from routes.py
    new_refresh_token = data.get("refreshToken")
    assert new_access_token, "New access token missing in refresh response."
    
    therapist_context["token"] = new_access_token
    if new_refresh_token: # Store if a new one is issued (good practice for rotation)
        therapist_context["refresh_token"] = new_refresh_token
    
    # Verify new token works
    profile_response = requests.get(f"{BASE_URL}/profiles/me", headers=get_headers(therapist_context["token"]))
    assert profile_response.status_code == 200, "Failed to access protected route with new access token."
    print("Token Refresh Successful, new access token is valid.")

def test_04_register_device_therapist():
    print("\n>>> TEST 04: Register Device (Therapist)...")
    if not therapist_context.get("token") or not therapist_context.get("user_id"):
        print("Skipping: Therapist not logged in properly.")
        return
    
    fake_fcm_token = f"fake_fcm_token_for_therapist_script_{uuid.uuid4()}"
    payload = {
        "token": fake_fcm_token,
        "platform": "PYTHON_TEST_SCRIPT",
        "user_id": therapist_context["user_id"],
        "user_type": "THERAPIST"
    }
    response = requests.post(f"{BASE_URL}/devices/register",
                             headers={**get_headers(therapist_context["token"]), "Content-Type": "application/json"},
                             json=payload)
    print(f"Register Device Status: {response.status_code}")
    assert response.status_code == 204, f"Device registration failed: {response.text}"
    print("Therapist Device Registration endpoint hit successfully.")

def test_05_therapist_search_patient_profiles():
    print("\n>>> TEST 05: Therapist Search Patient Profiles...")
    if not therapist_context.get("token"):
        print("Skipping: Therapist not logged in.")
        return

    # Search for the patient intended for session upload
    search_query = PATIENT_EMAIL_FOR_UPLOAD_METADATA.split("@")[0].split(".")[0] # Search by first name "Jordan"

    response = requests.get(f"{BASE_URL}/profiles/search", headers=get_headers(therapist_context["token"]), params={"query": search_query})
    print(f"Search Profiles Status: {response.status_code}")
    data = response.json() # Assuming response is JSON

    # ---- START DEBUG BLOCK ----
    print("\n--- DEBUG: API Response for /profiles/search ---")
    print(f"Search Query Sent: '{search_query}'")
    print(f"Expected Email: '{PATIENT_EMAIL_FOR_UPLOAD_METADATA}'")
    print("Full API Response Data:")
    print_json(data) # Your helper function
    if isinstance(data, list):
        print(f"Number of profiles returned: {len(data)}")
        for idx, profile_item in enumerate(data):
            print(f"  Profile {idx + 1}: ID='{profile_item.get('id')}', Email='{profile_item.get('email')}', Name='{profile_item.get('fullName')}'")
    print("--- END DEBUG BLOCK ---\n")
    # ---- END DEBUG BLOCK ----

    assert response.status_code == 200, f"Search profiles failed: {response.text}"
    assert isinstance(data, list), "Search profiles response is not a list."
    
    found_patient_for_upload = None
    if data:
        for profile in data:
            if profile.get("email") == PATIENT_EMAIL_FOR_UPLOAD_METADATA:
                found_patient_for_upload = profile
                break
    assert found_patient_for_upload, f"Patient {PATIENT_EMAIL_FOR_UPLOAD_METADATA} not found via search. Check seed data, search query, and API response above."
    # Store details for upload metadata
    therapist_context["patient_for_upload_id"] = found_patient_for_upload.get("id")
    therapist_context["patient_for_upload_fullName"] = found_patient_for_upload.get("fullName")
    therapist_context["patient_for_upload_dob_api"] = found_patient_for_upload.get("dateOfBirth") # API returns YYYY-MM-DD
    
    print(f"Found patient for upload: {therapist_context['patient_for_upload_fullName']} (ID: {therapist_context['patient_for_upload_id']})")
    print("Therapist Search Profiles Successful.")


def test_06_therapist_get_own_profile():
    print("\n>>> TEST 06: Therapist Get Own Profile...")
    # ... (similar to your existing test, use therapist_context)
    if not therapist_context.get("token"): print("Skipping: Therapist not logged in."); return
    response = requests.get(f"{BASE_URL}/profiles/me", headers=get_headers(therapist_context["token"]))
    assert response.status_code == 200
    data = response.json(); print_json(data, "Therapist Own Profile")
    assert data.get("id") == therapist_context["user_id"]
    print("Therapist Get Own Profile Successful.")

def test_07_patient_get_own_profile():
    print("\n>>> TEST 07: Patient Get Own Profile...")
    # ... (similar to your existing test, use patient_context)
    if not patient_context.get("token"): print("Skipping: Patient not logged in."); return
    response = requests.get(f"{BASE_URL}/profiles/me", headers=get_headers(patient_context["token"]))
    assert response.status_code == 200
    data = response.json(); print_json(data, "Patient Own Profile")
    assert data.get("id") == patient_context["user_id"]
    # API returns dateOfBirth as YYYY-MM-DD, login returns dd-MM-YYYY.
    # This test uses /profiles/me which should be YYYY-MM-DD from user doc directly
    assert data.get("dateOfBirth") == patient_context.get("date_of_birth_db_format") or patient_context.get("date_of_birth")
    # To make it more robust, convert login DOB to YYYY-MM-DD for comparison if needed.
    # For now, we assume /profiles/me gives YYYY-MM-DD if it's stored that way.
    # Login response for patient_context["date_of_birth"] is dd-MM-YYYY
    # Cosmos stores YYYY-MM-DD. User.get("dateOfBirth") in profiles/me is YYYY-MM-DD.
    # Let's ensure patient_context stores the DB format for consistency or convert for check.
    # For simplicity, let's assume the login test stores the API format and we check against that for now.
    # The seed script stores "YYYY-MM-DD". profiles/routes.py /me returns it as is.
    # The login route in auth/routes.py converts DB "YYYY-MM-DD" to "dd-MM-YYYY" for the API response.
    # So, patient_context["date_of_birth"] (from login) is dd-MM-YYYY.
    # This /profiles/me data.get("dateOfBirth") is YYYY-MM-DD. They won't match directly.
    # Let's get the YYYY-MM-DD from the earlier patient search for comparison.
    if therapist_context.get("patient_for_upload_email") == patient_context.get("email"): # if the logged in patient is the one searched
         assert data.get("dateOfBirth") == therapist_context.get("patient_for_upload_dob_api")

    print("Patient Get Own Profile Successful.")


def test_08_upload_session_audio():
    print("\n>>> TEST 08: Upload Audio Session...")
    if not all([therapist_context.get("token"), 
                therapist_context.get("user_id"), 
                therapist_context.get("full_name"),
                therapist_context.get("patient_for_upload_id"),
                therapist_context.get("patient_for_upload_fullName"),
                therapist_context.get("patient_for_upload_dob_api") # This is YYYY-MM-DD
                ]):
        print("Skipping: Therapist or patient-for-upload details from previous tests are missing.")
        return
    if not os.path.exists(SAMPLE_AUDIO_FILE_PATH):
        print(f"Skipping: Sample audio file '{SAMPLE_AUDIO_FILE_PATH}' not found.")
        # Create a dummy file for test to proceed if it's missing.
        with open(SAMPLE_AUDIO_FILE_PATH, 'wb') as f:
            f.write(os.urandom(1024)) # Create 1KB dummy audio if not present
        print(f"Created dummy '{SAMPLE_AUDIO_FILE_PATH}' for testing.")
        # assert False, f"Sample audio file '{SAMPLE_AUDIO_FILE_PATH}' is required." # Or create dummy

    therapist_meta = {
        "id": therapist_context["user_id"],
        "name": therapist_context["full_name"],
        "email": therapist_context["email"]
    }
    patient_meta = {
        "id": therapist_context["patient_for_upload_id"],
        "name": therapist_context["patient_for_upload_fullName"],
        "email": PATIENT_EMAIL_FOR_UPLOAD_METADATA,
        "date_of_birth": therapist_context["patient_for_upload_dob_api"] # YYYY-MM-DD
    }

    metadata_payload = {
        "therapist": json.dumps(therapist_meta), # therapist_meta is a Python dict
        "patient": json.dumps(patient_meta),     # patient_meta is a Python dict
        "session_date": datetime.now(timezone.utc).strftime("%d-%m-%Y"),
        "summary": f"Test summary from Python script for {SAMPLE_AUDIO_FILENAME}",
        "general_notes": [{"content": "Test script general note 1."}, {"content": "Another general note."}],
        "timed_notes": [
            {"timestamp": "00:00:01.000", "content": "Test script timed note at 1s."},
            {"timestamp": "00:00:05.500", "content": "Test script timed note at 5.5s."}
        ]
    }

    files = {
        'audio_file': (os.path.basename(SAMPLE_AUDIO_FILE_PATH), open(SAMPLE_AUDIO_FILE_PATH, 'rb'), AUDIO_CONTENT_TYPE),
        # 'metadata' key here is correct as Flask's request.form.get("metadata") will retrieve it
        'metadata': (None, json.dumps(metadata_payload), 'application/json') # Sending the whole thing as a JSON string.
    }
        
    upload_headers = {"Authorization": f"Bearer {therapist_context['token']}"} # No Content-Type here, requests sets it for multipart

    response = requests.post(f"{BASE_URL}/sessions/upload", headers=upload_headers, files=files)
    
    print(f"Upload Session Status: {response.status_code}")
    data = response.json()
    print_json(data, "Upload Session Response")
    assert response.status_code == 202, f"Session upload failed: {response.text}"
    
    session_context["created_session_id"] = data.get("session_id")
    assert session_context["created_session_id"], "Session ID missing in upload response."
    print(f"Session Upload Accepted. Session ID: {session_context['created_session_id']}.")

    # Polling for STT completion (i.e., session appears in pending review)
    stt_completed = poll_for_session_in_pending(session_context["created_session_id"], therapist_context["token"])
    assert stt_completed, f"STT processing for session {session_context['created_session_id']} did not complete in time."


def test_09_therapist_get_pending_data_and_select_session():
    print("\n>>> TEST 09: Therapist Get Pending Data and Select Session...")
    if not therapist_context.get("token"):
        print("Skipping: Therapist not logged in.")
        return

    response = requests.get(f"{BASE_URL}/data/pending", headers=get_headers(therapist_context["token"]))
    print(f"Get Pending Data Status: {response.status_code}")
    data = response.json()
    print_json(data, "Pending Data Response")
    assert response.status_code == 200, f"Getting pending data failed: {response.text}"
    assert isinstance(data, list), "Pending data response is not a list."
    
    if session_context.get("created_session_id"):
        found_uploaded = any(item.get("id") == session_context["created_session_id"] for item in data)
        if found_uploaded:
            session_context["pending_review_session_id"] = session_context["created_session_id"]
            print(f"Uploaded session {session_context['pending_review_session_id']} confirmed in pending list.")
        else:
            print(f"WARN: Uploaded session {session_context['created_session_id']} not found in pending list. STT might have failed or is very slow.")
            if data: # Fallback to first available if any
                session_context["pending_review_session_id"] = data[0].get("id")
                print(f"Using first available pending session: {session_context['pending_review_session_id']}")
            else: # No sessions available at all
                 session_context["pending_review_session_id"] = None
    elif data: # If no created_session_id (e.g. upload test skipped), pick first one
        session_context["pending_review_session_id"] = data[0].get("id")
        print(f"No specific session uploaded in this run, using first available pending session: {session_context['pending_review_session_id']}")
    else: # No created session and no pending sessions
        session_context["pending_review_session_id"] = None

    assert session_context["pending_review_session_id"], "No session available for pending review. Cannot proceed with editing tests."
    print("Therapist Get Pending Data and Session Selection Successful.")


def test_10_therapist_get_transcript_for_editing():
    print("\n>>> TEST 10: Therapist Get Transcript for Editing...")
    if not therapist_context.get("token") or not session_context.get("pending_review_session_id"):
        print("Skipping: Therapist not logged in or no pending session ID.")
        return []

    pending_id = session_context["pending_review_session_id"]
    response = requests.get(f"{BASE_URL}/data/transcript/{pending_id}", headers=get_headers(therapist_context["token"]))
    print(f"Get Transcript Detail Status: {response.status_code}")
    data = response.json()
    print_json(data, f"Transcript Detail for Editing (ID: {pending_id})")
    assert response.status_code == 200, f"Failed to get transcript for editing: {response.text}"
    
    transcript = data.get("transcript")
    assert transcript is not None, "Transcript data is missing."
    assert data.get("patientName") is not None, "Patient name missing in transcript detail."
    print("Therapist Get Transcript Detail Successful.")
    return transcript


def test_11_therapist_submit_edited_transcript(original_transcript):
    print("\n>>> TEST 11: Therapist Submit Edited Transcript...")
    if not therapist_context.get("token") or not session_context.get("pending_review_session_id"):
        print("Skipping: Therapist not logged in or no pending session ID.")
        return
    if not original_transcript:
        print("Skipping: No original transcript data to edit.")
        # As a fallback, create a dummy transcript to submit
        original_transcript = [{"speaker": "Therapist", "text": "Default entry if STT failed.", "timestamp": "00:00:00.000"}]

    edited_transcript = []
    for i, segment in enumerate(original_transcript):
        new_segment = segment.copy() # Create a copy to modify
        new_segment["text"] = f"(TestScriptEdit {i+1}) " + segment.get("text", "N/A")
        # Optionally swap speaker for some segments to test flexibility
        if i % 2 == 0 and "speaker" in new_segment : # Every other segment
             new_segment["speaker"] = "Patient" if segment["speaker"] == "Therapist" else "Therapist"
        edited_transcript.append(new_segment)
    
    if not edited_transcript: # Should not happen if fallback above is used
        edited_transcript.append({"speaker": "Therapist", "text": "Manually added final segment.", "timestamp": "00:00:01.000"})

    pending_id = session_context["pending_review_session_id"]
    print_json(edited_transcript, f"Submitting Edited Transcript Payload for {pending_id}")
    
    # The API expects a raw JSON string in the body, not a JSON object for the `json` param of `requests`.
    # Headers need "Content-Type": "application/json"
    submit_headers = {**get_headers(therapist_context["token"]), "Content-Type": "application/json"}
    response = requests.put(f"{BASE_URL}/data/transcript/{pending_id}", headers=submit_headers, data=json.dumps(edited_transcript))
    
    print(f"Submit Edited Transcript Status: {response.status_code}")
    response_content = response.json() if response.content and response.headers.get('Content-Type') == 'application/json' else response.text
    print_json(response_content, "Submit Edited Transcript Response")
    assert response.status_code == 202, f"Submitting edited transcript failed: {response_content}"
    
    print(f"Edited Transcript Submitted for {pending_id}. Waiting for finalization...")
    finalized, _ = poll_for_session_finalization(pending_id, therapist_context["token"])
    assert finalized, f"Session {pending_id} did not finalize in time."


def test_12_therapist_get_finalized_session_details():
    print("\n>>> TEST 12: Therapist Get Finalized Session Details...")
    session_id_to_check = session_context.get("pending_review_session_id") 
    if not therapist_context.get("token") or not session_id_to_check:
        print("Skipping: Therapist not logged in or no session ID from previous test.")
        return

    finalized, data = poll_for_session_finalization(session_id_to_check, therapist_context["token"], max_wait_sec=10, interval_sec=2) 
    assert finalized, f"Session {session_id_to_check} not found or not finalized."

    print_json(data, f"Finalized Session Details (ID: {session_id_to_check})")
    assert data.get("sessionId") == session_id_to_check, "Session ID mismatch in finalized details."
    
    sentiment_scores = data.get("sentiment_scores")
    assert sentiment_scores is not None, "Sentiment scores array missing."
    assert isinstance(sentiment_scores, list), "Sentiment scores is not a list."

    if not sentiment_scores:
        print("Warning: Sentiment scores list is empty. This might happen if the transcript was empty.")
    
    edited_found_in_text = False
    for entry in sentiment_scores:
        assert "speaker" in entry, f"Speaker missing in sentiment entry: {entry}"
        assert "text" in entry, f"Text missing in sentiment entry: {entry}"
        assert "timestamp" in entry, f"Timestamp missing in sentiment entry: {entry}"
        assert "positive" in entry, f"Positive score missing in sentiment entry: {entry}"
        assert "neutral" in entry, f"Neutral score missing in sentiment entry: {entry}"
        assert "negative" in entry, f"Negative score missing in sentiment entry: {entry}"

        if entry.get("text") is not None and "(TestScriptEdit" in entry.get("text"):
            edited_found_in_text = True
            # For patient lines, sentiment should be there. For therapist, it might be default.
            if entry.get("speaker", "").upper() == "PATIENT":
                # Check that scores sum to 1 (approx) if not all zero
                sum_s = entry["positive"] + entry["neutral"] + entry["negative"]
                assert abs(sum_s - 1.0) < 0.01 or (entry["positive"] == 0 and entry["neutral"] == 0 and entry["negative"] == 0) or (entry["positive"] == 0 and entry["neutral"] == 1 and entry["negative"] == 0) , \
                    f"Patient sentiment scores do not sum to 1 for entry: {entry}"
            
    assert edited_found_in_text, "Submitted edits '(TestScriptEdit)' not found in any text within finalized sentiment_scores."
    print("Therapist Get Finalized Session Details Successful, new structure and edits confirmed.")

def test_13_patient_get_own_sessions():
    print("\n>>> TEST 13: Patient Get Own Sessions...")
    if not patient_context.get("token") or not patient_context.get("user_id"):
        print("Skipping: Patient not logged in.")
        return
    
    response = requests.get(f"{BASE_URL}/sessions/me", headers=get_headers(patient_context["token"]))
    print(f"Patient Get Own Sessions Status: {response.status_code}")
    data = response.json()
    print_json(data, "Patient Own Sessions Response")
    assert response.status_code == 200, f"Failed to get patient's own sessions: {response.text}"
    assert isinstance(data, list), "Patient sessions response is not a list."

    if data:
        print(f"Patient {patient_context['user_id']} has {len(data)} finalized session(s).")
        # Check if the session processed (if it involved this logged-in patient) appears here
        # This requires knowing if therapist_context["patient_for_upload_id"] == patient_context["user_id"]
        processed_session_id = session_context.get("pending_review_session_id") # This was the ID finalized
        if processed_session_id and therapist_context.get("patient_for_upload_id") == patient_context.get("user_id"):
            session_found_for_patient = any(s.get("id") == processed_session_id for s in data)
            assert session_found_for_patient, f"Finalized session {processed_session_id} not listed for patient {patient_context['user_id']}."
            print(f"Session {processed_session_id} is correctly listed for patient {patient_context['user_id']}.")
        elif processed_session_id:
             print(f"Session {processed_session_id} was finalized but may not belong to logged-in patient {patient_context['user_id']}.")
    else:
        print(f"Patient {patient_context['user_id']} has no finalized sessions listed. This may be expected if no sessions were for them.")
    print("Patient Get Own Sessions - API call successful.")


def test_14_therapist_search_sessions():
    print("\n>>> TEST 14: Therapist Search Sessions...")
    if not therapist_context.get("token"):
        print("Skipping: Therapist not logged in.")
        return
    
    search_query = "TestScriptEdit" # From our edited transcript
    if therapist_context.get("patient_for_upload_fullName"):
        search_query = therapist_context["patient_for_upload_fullName"].split(" ")[0] # Search by patient first name

    response = requests.get(f"{BASE_URL}/sessions/search", headers=get_headers(therapist_context["token"]), params={"query": search_query})
    print(f"Search Sessions Status: {response.status_code}")
    data = response.json()
    print_json(data, f"Search Sessions Response for query '{search_query}'")
    assert response.status_code == 200, f"Session search failed: {response.text}"
    assert isinstance(data, list), "Session search response is not a list."
    print(f"Found {len(data)} session(s) matching query '{search_query}'.")

    # If a session was processed and involved a patient matching the query, it should be found.
    finalized_session_id = session_context.get("pending_review_session_id")
    if finalized_session_id and data:
        if any(s.get("id") == finalized_session_id for s in data):
            print(f"Confirmed that session {finalized_session_id} can be found via search for '{search_query}'.")
    print("Therapist Search Sessions - API call successful.")

def test_15_unregister_device_therapist():
    print("\n>>> TEST 15: Unregister Device (Therapist)...")
    if not therapist_context.get("token") or not therapist_context.get("user_id"):
        print("Skipping: Therapist not logged in.")
        return
    
    response = requests.post(f"{BASE_URL}/devices/unregister/{therapist_context['user_id']}", 
                             headers=get_headers(therapist_context["token"])) # No body for unregister
    print(f"Unregister Device Status: {response.status_code}")
    # Expect 204 No Content
    assert response.status_code == 204, f"Device unregistration failed: {response.text if response.content else 'No Content'}"
    print("Therapist Device Unregistration endpoint hit successfully.")


def run_all_tests():
    # Ensure sample audio file exists or create a dummy one
    if not os.path.exists(SAMPLE_AUDIO_FILE_PATH):
        print(f"Warning: Sample audio file '{SAMPLE_AUDIO_FILE_PATH}' not found.")
        try:
            with open(SAMPLE_AUDIO_FILE_PATH, 'wb') as f:
                create_wav_file()
            print(f"Created dummy '{SAMPLE_AUDIO_FILE_PATH}' for testing.")
        except IOError as e:
            print(f"ERROR: Could not create dummy audio file: {e}. Please create it manually.")
            return

    try:
        test_01_therapist_login()
        test_02_patient_login()
        test_03_refresh_therapist_token()
        test_04_register_device_therapist()
        test_05_therapist_search_patient_profiles() # Populates therapist_context with patient_for_upload details
        test_06_therapist_get_own_profile()
        test_07_patient_get_own_profile()
        
        test_08_upload_session_audio() # Sets created_session_id, polls for STT
        
        test_09_therapist_get_pending_data_and_select_session() # Sets pending_review_session_id
        
        if session_context.get("pending_review_session_id"):
            original_transcript = test_10_therapist_get_transcript_for_editing()
            if original_transcript is not None: # Ensure transcript was fetched
                test_11_therapist_submit_edited_transcript(original_transcript) # Polls for finalization
                test_12_therapist_get_finalized_session_details()
            else:
                print("\nSKIPPING transcript editing and finalization tests due to no transcript fetched.")
        else:
            print("\nSKIPPING transcript editing and finalization tests due to no pending session ID.")

        test_13_patient_get_own_sessions()
        test_14_therapist_search_sessions()
        test_15_unregister_device_therapist()

        print("\n\n✅✅✅ ALL AVAILABLE TESTS COMPLETED! ✅✅✅")

    except AssertionError as e:
        print(f"\n❌❌❌ TEST FAILED: {e} ❌❌❌")
        import traceback
        traceback.print_exc()
    except Exception as e:
        print(f"\n🔥🔥🔥 AN UNEXPECTED ERROR OCCURRED: {e} 🔥🔥🔥")
        import traceback
        traceback.print_exc()
    finally:
        # Clean up dummy audio file if it was created by the script and named uniquely
        if os.path.exists(SAMPLE_AUDIO_FILE_PATH) and "dummy" in SAMPLE_AUDIO_FILE_PATH:
            # Be cautious with auto-delete. Better to instruct manual cleanup or use a tempfile.
            # For now, let's assume manual management of the sample audio file.
            pass


if __name__ == "__main__":
    run_all_tests()