# database/cosmos.py
from azure.cosmos import CosmosClient, PartitionKey, exceptions as cosmos_exceptions # Renamed for clarity
from datetime import datetime, timezone
import os
from dotenv import load_dotenv
import logging # Use standard logging
import config

load_dotenv()
logger = logging.getLogger(__name__)

COSMOS_URI = config.COSMOS_URI
COSMOS_KEY = config.COSMOS_KEY
COSMOS_DATABASE_NAME = config.COSMOS_DATABASE_NAME

if not all([COSMOS_URI, COSMOS_KEY]):
    logger.critical("Cosmos DB URI or Key not configured. Application will not function correctly.")

# Cosmos client setup
try:
    client = CosmosClient(COSMOS_URI, credential=COSMOS_KEY)
    database = client.get_database_client(COSMOS_DATABASE_NAME)

    users_container = database.get_container_client("users_container") 
    user_tokens_container = database.get_container_client("user_tokens_container") 
    
    session_headers_container = database.get_container_client("session_headers_container")
    session_processing_data_container = database.get_container_client("session_processing_data_container")
    session_details_container = database.get_container_client("session_details_container") 

    devices_container = database.get_container_client("devices_container") 

    logger.info("Successfully connected to Cosmos DB and initialized container clients.")

except Exception as e:
    logger.critical(f"Error connecting to Cosmos DB or initializing containers: {e}", exc_info=True)
    users_container = None
    user_tokens_container = None
    session_headers_container = None
    session_processing_data_container = None
    session_details_container = None
    devices_container = None


# --- User Management ---
def get_user_by_id(user_id: str):
    if not users_container: return None
    try:
        user_doc = users_container.read_item(item=user_id, partition_key=user_id)
        return user_doc
    except cosmos_exceptions.CosmosResourceNotFoundError:
        logger.debug(f"User with id {user_id} not found in users_container.")
        return None
    except Exception as e:
        logger.error(f"Error fetching user by id {user_id}: {e}", exc_info=True)
        return None

def get_user_by_email(email: str):
    if not users_container: return None
    try:
        #query with emial insensitive to case
        query = """
            SELECT * FROM c
            WHERE LOWER(c.email) = LOWER(@email)
        """
        params = [{"name": "@email", "value": email}]
        items = list(users_container.query_items(query=query, parameters=params, enable_cross_partition_query=True))
        if items:
            logger.debug(f"User found with email: {email}")
            return items[0]
        logger.debug(f"No user found with email: {email}")
        return None
    except Exception as e:
        logger.error(f"Error fetching user by email {email}: {e}", exc_info=True)
        return None

def update_user_password(user_id: str, new_hashed_password: str):
    if not users_container: return False
    try:
        user_doc = users_container.read_item(item=user_id, partition_key=user_id)
        user_doc["password"] = new_hashed_password
        users_container.replace_item(item=user_doc["id"], body=user_doc) # PK is user_doc["id"]
        logger.info(f"Password updated for user_id: {user_id}")
        return True
    except cosmos_exceptions.CosmosResourceNotFoundError:
        logger.warning(f"Attempted to update password for non-existent user_id: {user_id}")
        return False
    except Exception as e:
        logger.error(f"Error updating password for user_id {user_id}: {e}", exc_info=True)
        return False

# --- Token Management (Refresh & Password Reset) ---
def store_token(token_id: str, user_id: str, token_type: str, token_string: str, expires_at_iso: str, email: str = None):
    if not user_tokens_container: return
    token_doc = {
        "id": token_id,  # PK: the token string itself or a UUID for the token record
        "user_id": user_id,
        "type": token_type, # "refresh" or "password_reset"
        "token_string": token_string, 
        "issued_at": datetime.now(timezone.utc).isoformat(),
        "expires_at": expires_at_iso,
        "ttl": int((datetime.fromisoformat(expires_at_iso) - datetime.now(timezone.utc)).total_seconds()) + 60 # TTL a bit longer
    }
    if email and token_type == "password_reset":
        token_doc["email"] = email
    
    try:
        user_tokens_container.upsert_item(token_doc)
        logger.info(f"{token_type} token stored for user_id: {user_id}, token_id: {token_id}")
    except Exception as e:
        logger.error(f"Error storing {token_type} token for user_id {user_id}: {e}", exc_info=True)

def get_token_by_id(token_id: str, token_type: str):
    if not user_tokens_container: return None
    try:
        # Assuming 'id' is the partition key (token_id)
        token_doc = user_tokens_container.read_item(item=token_id, partition_key=token_id)
        if token_doc and token_doc.get("type") == token_type:
            # Check expiry (though TTL should handle it, belt and braces)
            if datetime.now(timezone.utc) < datetime.fromisoformat(token_doc["expires_at"]):
                return token_doc
            else:
                logger.info(f"{token_type} token {token_id} found but expired.")
                # Optionally delete expired token here
                # delete_token(token_id)
                return None
        return None
    except cosmos_exceptions.CosmosResourceNotFoundError:
        return None
    except Exception as e:
        logger.error(f"Error fetching {token_type} token by id {token_id}: {e}", exc_info=True)
        return None

def get_refresh_token_by_user_id_and_token(user_id: str, refresh_token_string: str):
    if not user_tokens_container:
        return None

    try:
        token_doc = user_tokens_container.read_item(item=user_id, partition_key=user_id)
        if token_doc and token_doc.get("type") == "refresh" and token_doc.get("token_string") == refresh_token_string:
            if datetime.now(timezone.utc) < datetime.fromisoformat(token_doc["expires_at"]):
                return token_doc
        return None
    except cosmos_exceptions.CosmosResourceNotFoundError:
        return None
    except Exception as e:
        logger.error(f"Error in get_refresh_token_by_user_id_and_token for user {user_id}: {e}", exc_info=True)
        return None


def delete_token(token_id: str):
    if not user_tokens_container: return
    try:
        user_tokens_container.delete_item(item=token_id, partition_key=token_id)
        logger.info(f"Token {token_id} deleted.")
    except cosmos_exceptions.CosmosResourceNotFoundError:
        logger.info(f"Token {token_id} not found for deletion.")
    except Exception as e:
        logger.error(f"Error deleting token {token_id}: {e}", exc_info=True)

# --- Device Management ---
def register_device(user_id: str, fcm_token: str, platform: str, user_type: str):
    if not devices_container: return False
    # Assuming 'id' for devices_container is user_id, making one device per user
    # If multiple devices per user, 'id' should be a unique device registration ID.
    # Current code implies user_id is the unique ID for the device document.
    device_doc = {
        "id": user_id,  # PK for the device document, unique per user
        "user_id": user_id, # For querying if PK is different
        "fcm_token": fcm_token,
        "platform": platform,
        "user_type": user_type, # From the user document
        "last_registered_at": datetime.now(timezone.utc).isoformat()
    }
    try:
        devices_container.upsert_item(device_doc)
        logger.info(f"Device registered/updated for user_id: {user_id}")
        return True
    except Exception as e:
        logger.error(f"Error registering device for user_id {user_id}: {e}", exc_info=True)
        return False

def unregister_device(user_id: str): # Assumes user_id is the id of the device document
    if not devices_container: return False
    try:
        devices_container.delete_item(item=user_id, partition_key=user_id)
        logger.info(f"Device unregistered for user_id: {user_id}")
        return True
    except cosmos_exceptions.CosmosResourceNotFoundError:
        logger.info(f"No device found for user_id {user_id} to unregister.")
        return False
    except Exception as e:
        logger.error(f"Error unregistering device for user_id {user_id}: {e}", exc_info=True)
        return False

# --- Patient Profile Search ---
def search_patients_by_query(query_term: str):
    if not users_container:
        logger.warning("users_container is not initialized in search_patients_by_query.")
        return []
    try:
        lower_query_term = query_term.lower() # query_term is already lowercase from your test

        # Make the type comparison case-insensitive as well
        query = """
            SELECT c.id, c.userFullName, c.email, c.dateOfBirth, c.pictureUrl
            FROM c
            WHERE LOWER(c.type) = 'patient' AND
                  (CONTAINS(LOWER(c.userFullName), @lower_query_term) OR
                   CONTAINS(LOWER(c.id), @lower_query_term) OR
                   CONTAINS(LOWER(c.email), @lower_query_term))
        """
        params = [{"name": "@lower_query_term", "value": lower_query_term}]

        logger.debug(f"Executing patient search with query: '{query}' and params: {params}")
        items = list(users_container.query_items(query=query, parameters=params, enable_cross_partition_query=True))
        logger.debug(f"Cosmos DB returned {len(items)} items for query_term '{query_term}' (after type and name/email filter).")

        results = []
        for item in items:
            results.append({
                "id": item.get("id"),
                "fullName": item.get("userFullName"),
                "email": item.get("email"),
                "dateOfBirth": item.get("dateOfBirth"),
                "pictureUrl": item.get("pictureUrl")
            })
        return results
    except Exception as e:
        logger.error(f"Error searching patients with query '{query_term}': {e}", exc_info=True)
        return []

# --- Session Data Access (New structure) ---

def get_session_header_by_id(session_id: str, partition_key_value: str): # PK be therapist_id
    if not session_headers_container: return None
    try:
        return session_headers_container.read_item(item=session_id, partition_key=partition_key_value)
    except cosmos_exceptions.CosmosResourceNotFoundError:
        return None
    except Exception as e:
        logger.error(f"Error fetching session header {session_id} with PK {partition_key_value}: {e}", exc_info=True)
        return None

def get_final_session_details_by_id(session_id: str): # session_id is PK for details
    if not session_details_container: return None
    try:
        return session_details_container.read_item(item=session_id, partition_key=session_id)
    except cosmos_exceptions.CosmosResourceNotFoundError:
        return None
    except Exception as e:
        logger.error(f"Error fetching final session details {session_id}: {e}", exc_info=True)
        return None

def get_session_summaries_for_user(user_id: str, user_type: str):
    if not session_headers_container: return []
    query = f"SELECT * FROM c \
              WHERE c.patient_id = @user_id AND c.status = 'FINALIZED' \
              ORDER BY c.session_date DESC"
    params = [{"name": "@user_id", "value": user_id}]
    try:
        items = list(session_headers_container.query_items(query=query, parameters=params, enable_cross_partition_query=True)) # Check PK
        # Format to match SessionSummaryResponse (id, patientId, patientName, therapistId, therapistName, title, date, descriptionPreview, positive, neutral, negative)
        summaries = []
        for s_header in items:
            summaries.append({
                "id": s_header.get("id"),
                "patientId": s_header.get("patient_id"),
                "patientName": s_header.get("patient_name"),
                "therapistId": s_header.get("therapist_id"),
                "therapistName": s_header.get("therapist_name"),
                "title": s_header.get("title", f"Session on {s_header.get('session_date')}"),
                "date": s_header.get("session_date"), # Consider formatting if needed
                "descriptionPreview": s_header.get("summary_preview", "")[:100],
                "positive": s_header.get("overall_sentiment_positive", 0.0),
                "neutral": s_header.get("overall_sentiment_neutral", 0.0),
                "negative": s_header.get("overall_sentiment_negative", 0.0)
            })
        return summaries
    except Exception as e:
        logger.error(f"Error fetching session summaries for user {user_id} ({user_type}): {e}", exc_info=True)
        return []

def search_session_summaries_by_query(query_term: str):
    if not session_headers_container: return []
    
    # Make query_term lowercase for case-insensitive search
    lower_query_term = query_term.lower()
    
    query = """
    SELECT * FROM c 
    WHERE c.status = 'FINALIZED'
    AND (CONTAINS(LOWER(c.patient_name), @lower_query_term) OR
         CONTAINS(LOWER(c.patient_id), @lower_query_term) OR
         CONTAINS(LOWER(c.therapist_name), @lower_query_term) OR
         CONTAINS(LOWER(c.therapist_id), @lower_query_term) OR
         CONTAINS(LOWER(c.session_date), @lower_query_term) OR
         CONTAINS(LOWER(c.summary_preview), @lower_query_term))
    ORDER BY c.session_date DESC
    """
    
    params = [
        {"name": "@lower_query_term", "value": lower_query_term}
    ]
    
    try:
        items = list(session_headers_container.query_items(query=query, parameters=params, enable_cross_partition_query=True))
        summaries = []
        for s_header in items:
             summaries.append({
                "id": s_header.get("id"), 
                "patientId": s_header.get("patient_id"), 
                "patientName": s_header.get("patient_name"),
                "therapistId": s_header.get("therapist_id"), 
                "therapistName": s_header.get("therapist_name"),
                "title": s_header.get("title", f"Session on {s_header.get('session_date')}"),
                "date": s_header.get("session_date"), 
                "descriptionPreview": s_header.get("summary_preview", "")[:100],
                "positive": s_header.get("overall_sentiment_positive", 0.0),
                "neutral": s_header.get("overall_sentiment_neutral", 0.0),
                "negative": s_header.get("overall_sentiment_negative", 0.0)
            })
        return summaries
    except Exception as e:
        logger.error(f"Error searching session summaries for query '{query_term}': {e}", exc_info=True)
        return []