# app.py
from flask import Flask
from flask_jwt_extended import JWTManager
from datetime import timedelta

# Import your blueprints
from auth.routes import auth_bp, refresh_bp
from profiles.routes import profiles_bp
from sessions.routes import sessions_bp
from devices.routes import devices_bp
from data.routes import data_bp
import os
from celery_app import celery, init_celery
import json


# Import centralized config
import config


# def setup_firebase_credentials():
#     """
#     Set up Firebase service account credentials for FCM notifications.
#     Checks multiple possible locations and fallback methods.
#     """
#     # Define possible file locations in order of preference
#     firebase_credential_paths = [
#         '/app/firebase_service_account.json',           # Container root location
#         '/app/secrets/firebase_service_account.json',   # Secrets directory
#         './firebase_service_account.json'               # Local development
#     ]
    
#     # Try to find existing Firebase service account file
#     for credential_path in firebase_credential_paths:
#         if os.path.exists(credential_path):
#             os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = credential_path
#             print(f"✓ Firebase credentials found at: {credential_path}")
#             return True
    
#     # If no file found, try to create from environment variable
#     firebase_json_content = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON_CONTENT")
#     if firebase_json_content:
#         target_path = '/app/secrets/firebase_service_account.json'
#         return _create_firebase_file_from_env(firebase_json_content, target_path)
    
#     print("⚠️  Warning: No Firebase service account file found and FIREBASE_SERVICE_ACCOUNT_JSON_CONTENT not set.")
#     print("   FCM notifications will not work.")
#     return False

# def _create_firebase_file_from_env(json_content: str, target_path: str) -> bool:
#     """
#     Create Firebase service account file from environment variable content.
    
#     Args:
#         json_content: JSON content as string
#         target_path: Where to save the file
        
#     Returns:
#         bool: True if successful, False otherwise
#     """
#     try:
#         # Ensure the directory exists
#         os.makedirs(os.path.dirname(target_path), exist_ok=True)
        
#         # Validate and parse JSON
#         parsed_json = json.loads(json_content)
        
#         # Write to file
#         with open(target_path, 'w') as f:
#             json.dump(parsed_json, f, indent=2)
        
#         # Set environment variable
#         os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = target_path
#         print(f"✓ Firebase credentials created at: {target_path}")
#         return True
        
#     except json.JSONDecodeError as e:
#         print(f"❌ ERROR: Invalid JSON in FIREBASE_SERVICE_ACCOUNT_JSON_CONTENT: {e}")
#         return False
#     except Exception as e:
#         print(f"❌ ERROR: Failed to create Firebase credentials file: {e}")
#         return False

# # Initialize Firebase credentials
# firebase_setup_success = setup_firebase_credentials()
# if not firebase_setup_success:
#     print("❌ Firebase service account setup failed. FCM notifications will not work.")

# Initialize Flask App
app = Flask(__name__)

# Load Configurations from config.py
app.config["SECRET_KEY"] = config.SECRET_KEY
app.config["JWT_SECRET_KEY"] = config.JWT_SECRET_KEY
app.config["JWT_TOKEN_LOCATION"] = config.JWT_TOKEN_LOCATION
app.config["JWT_ACCESS_TOKEN_EXPIRES"] = timedelta(minutes=config.JWT_ACCESS_TOKEN_EXPIRES_MINUTES)
app.config["JWT_REFRESH_TOKEN_EXPIRES"] = timedelta(days=config.JWT_REFRESH_TOKEN_EXPIRES_DAYS)

# Initialize JWT Manager
jwt = JWTManager(app)

init_celery(app)


# Register Blueprints
app.register_blueprint(auth_bp, url_prefix="/auth")
app.register_blueprint(refresh_bp, url_prefix="/auth")
app.register_blueprint(profiles_bp, url_prefix="/profiles")
app.register_blueprint(sessions_bp, url_prefix="/sessions")
app.register_blueprint(data_bp, url_prefix="/data")
app.register_blueprint(devices_bp, url_prefix="/devices")

# Remove from sessions import init_app as init_sessions if not used
# from sessions import init_app as init_sessions
# init_sessions(app) # Only if sessions/__init__.py has a meaningful init_app

if __name__ == '__main__':
    # For local development, Gunicorn is preferred for production
    app.run(debug=config.FLASK_DEBUG.lower() == "true", host="0.0.0.0", port=config.FLASK_PORT)