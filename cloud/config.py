# config.py
import os
from dotenv import load_dotenv

if "WEBSITE_INSTANCE_ID" not in os.environ:
    print("---- Local development detected. Loading .env file. ----")
    load_dotenv()

# Flask Core Config
SECRET_KEY = os.getenv("FLASK_SECRET_KEY", os.urandom(24)) # For Flask session, CSRF, etc.
FLASK_DEBUG = "False"
FLASK_PORT = 8000

# JWT Configuration
JWT_SECRET_KEY = os.getenv("JWT_SECRET_KEY") 
JWT_TOKEN_LOCATION = ["headers"]
JWT_ACCESS_TOKEN_EXPIRES_MINUTES = 30
JWT_REFRESH_TOKEN_EXPIRES_DAYS = 1

# Azure Cosmos DB
COSMOS_URI = os.getenv("COSMOS_URI")
COSMOS_KEY = os.getenv("COSMOS_KEY")
COSMOS_DATABASE_NAME = "therapydb" 

# Azure Cognitive Services - Speech
SPEECH_KEY = os.getenv("SPEECH_KEY")
SPEECH_REGION = os.getenv("SPEECH_REGION")
SPEECH_RECOGNITION_LANGUAGE = "he-IL"
SPEECH_DIARIZATION_ENABLED = True
SPEECH_AUDIO_CHANNEL_COUNT = "2" # Assuming stereo for diarization
SPEECH_SEPARATE_RECOGNITION_PER_CHANNEL = True
SPEECH_STT_TIMEOUT_SECONDS = 1200 # Timeout for STT processing


# Azure Cognitive Services - Text Analytics (Language)
TEXT_ANALYTICS_KEY = os.getenv("TEXT_ANALYTICS_KEY")
TEXT_ANALYTICS_ENDPOINT = os.getenv("TEXT_ANALYTICS_ENDPOINT")
TEXT_ANALYTICS_DEFAULT_LANGUAGE = "he"

# Azure Storage (for async audio processing)
AZURE_STORAGE_CONNECTION_STRING = os.getenv("AZURE_STORAGE_CONNECTION_STRING")
AUDIO_UPLOAD_BLOB_CONTAINER_NAME = os.getenv("AUDIO_UPLOAD_BLOB_CONTAINER_NAME", "audio-uploads")


REDIS_HOST = os.getenv("REDIS_HOST", "localhost") # e.g., your-cache.redis.cache.windows.net
REDIS_PORT = int(os.getenv("REDIS_PORT", 6380)) # 6380 for SSL, 6379 for non-SSL
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "")
REDIS_USE_SSL = os.getenv("REDIS_USE_SSL", "True").lower() == "true" # Azure Cache for Redis typically uses SSL
# SSL_CERT_PARAMS = "ssl_cert_reqs=CERT_NONE"
SSL_CERT_PARAMS = "ssl_cert_reqs=CERT_REQUIRED"

if REDIS_USE_SSL:
    CELERY_BROKER_URL = f"rediss://:{REDIS_PASSWORD}@{REDIS_HOST}:{REDIS_PORT}/0?{SSL_CERT_PARAMS}"
    CELERY_RESULT_BACKEND_URL = f"rediss://:{REDIS_PASSWORD}@{REDIS_HOST}:{REDIS_PORT}/1?{SSL_CERT_PARAMS}"
else:
    CELERY_BROKER_URL = f"redis://:{REDIS_PASSWORD}@{REDIS_HOST}:{REDIS_PORT}/0"
    CELERY_RESULT_BACKEND_URL = f"redis://:{REDIS_PASSWORD}@{REDIS_HOST}:{REDIS_PORT}/1"


CELERY_TASK_SERIALIZER = 'json'
CELERY_RESULT_SERIALIZER = 'json'
CELERY_ACCEPT_CONTENT = ['json']
CELERY_TIMEZONE = 'UTC'
CELERY_ENABLE_UTC = True
CELERY_TASK_TRACK_STARTED = True 


# Firebase Cloud Messaging (FCM)
FIREBASE_PROJECT_ID = os.getenv("FIREBASE_PROJECT_ID")

# Firebase service account file path - try environment variable first, then fallback to standard locations
FIREBASE_SERVICE_ACCOUNT_JSON_CONTENT = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON_CONTENT")
GOOGLE_APPLICATION_CREDENTIALS = (
    os.getenv("GOOGLE_APPLICATION_CREDENTIALS") or
    "/app/firebase_service_account.json" or 
    "/app/secrets/firebase_service_account.json"
)

# Feature Flags (Example for password reset)
FEATURE_ENABLE_PASSWORD_RESET = "False"

