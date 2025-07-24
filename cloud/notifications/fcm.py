#notification/fcm.py

from google.oauth2 import service_account
import google.auth.transport.requests
import requests
import json
import os
import logging
import config 

logger = logging.getLogger(__name__)

def send_fcm_notification(project_id: str, device_token: str, title: str, body: str, data_payload: dict = None):
    """
    Sends an FCM notification using the HTTP v1 API and a service account.
    
    Args:
        project_id: Firebase project ID
        service_account_file: Path to the service account JSON file
        device_token: FCM device token
        title: Notification title
        body: Notification body
        data_payload: Optional data payload dict
        
    Returns:
        dict: Response from FCM API or None if failed
    """    

    if not config.FIREBASE_SERVICE_ACCOUNT_JSON_CONTENT and not config.GOOGLE_APPLICATION_CREDENTIALS:
        logger.error("[FCM V1 ERROR] ❌ Firebase service account JSON content or file path is not set.")
        return None
    if config.FIREBASE_SERVICE_ACCOUNT_JSON_CONTENT:
        try:
            service_account_info = json.loads(config.FIREBASE_SERVICE_ACCOUNT_JSON_CONTENT)
        except json.JSONDecodeError as e:
            logger.error(f"[FCM V1 ERROR] ❌ Invalid JSON content for Firebase service account: {e}")
            return None
    else:
        service_account_info = config.GOOGLE_APPLICATION_CREDENTIALS
        if os.path.exists(service_account_info):
            with open(service_account_info, 'r') as f:
                try:
                    service_account_info = json.load(f)
                except json.JSONDecodeError as e:
                    logger.error(f"[FCM V1 ERROR] ❌ Invalid JSON file for Firebase service account: {e}")
                    return None
        else:
            logger.error(f"[FCM V1 ERROR] ❌ Service account file not found at: {service_account_info}")
            return None
    
    # Validate required parameters
    if not service_account_info or not project_id:
        logger.error("[FCM V1 ERROR] ❌ Missing Firebase Project ID or Service Account File path.")
        return None
    if not device_token:
        logger.error("[FCM V1 ERROR] ❌ Device token is missing.")
        return None


    try:
        # Load service account credentials
        credentials = service_account.Credentials.from_service_account_info(
            service_account_info,
            scopes=["https://www.googleapis.com/auth/firebase.messaging"]
        )
        
        # Get OAuth2 access token
        auth_req = google.auth.transport.requests.Request()
        credentials.refresh(auth_req)
        access_token = credentials.token        # Prepare FCM API request
        url = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"
        headers = {
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json; UTF-8"
        }

        message_body = {
            "message": {
                "token": device_token,
                "notification": { # For display message
                    "title": title,
                    "body": body,
                },
                # Data payload is always sent, app decides if/how to use it when in foreground/background
                "data": {str(k): str(v) for k, v in data_payload.items()} if data_payload else {}
            }
        }

        response = requests.post(url, headers=headers, data=json.dumps(message_body))
        
        if response.status_code == 200:
            logger.info(f"[FCM V1 SUCCESS] ✓ Notification sent to token {device_token[:20]}...")
            return response.json()
        else:
            logger.error(f"[FCM V1 ERROR] ❌ HTTP {response.status_code}: {response.text}")
            return None
            
    except requests.exceptions.HTTPError as e:
        logger.error(f"[FCM V1 ERROR] ❌ HTTP Error: {e}")
        if e.response is not None:
            logger.error(f"[FCM V1 ERROR] Response: {e.response.text}")
        return None
    except requests.exceptions.ConnectionError as e:
        logger.error(f"[FCM V1 ERROR] ❌ Connection Error: {e}")
        return None
    except requests.exceptions.Timeout as e:
        logger.error(f"[FCM V1 ERROR] ❌ Timeout Error: {e}")
        return None
    except FileNotFoundError:
        logger.error(f"[FCM V1 ERROR] ❌ Service account file not found at: {service_account_info}")
        return None
    except Exception as e:
        logger.error(f"[FCM V1 ERROR] ❌ Unexpected error: {e}", exc_info=True)
        return None


