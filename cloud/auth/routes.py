# auth/routes.py
from flask import Blueprint, request, jsonify, current_app
from flask_jwt_extended import create_access_token, create_refresh_token, jwt_required, get_jwt_identity
from datetime import datetime, timedelta, timezone 
from database.cosmos import ( 
    get_user_by_email, 
    store_token, get_token_by_id, delete_token,
    get_user_by_id, update_user_password,
)
from auth.utils import verify_password, hash_password, generate_reset_token, get_token_expiry
import config as app_config 

auth_bp = Blueprint('auth', __name__)
refresh_bp = Blueprint('refresh_bp', __name__) # Keep separate for clarity if different prefix in app.py

@auth_bp.route('/protected', methods=['GET']) # From original
@jwt_required()
def protected():
    current_user = get_jwt_identity()
    return jsonify({"message": "Access granted", "current_user": current_user}), 200


@auth_bp.route('/login', methods=['POST'])
def login():
    try:
        data = request.get_json()
        if not data or not data.get("email") or not data.get("password"):
            return jsonify({"error": "Invalid request format. Email and password are required."}), 400

        email = data.get("email")
        password = data.get("password")
        user = get_user_by_email(email) # Uses updated function querying users_container

        if user and verify_password(password, user["password"]):
            user_id = user["id"]
            # Using Flask-JWT-Extended for token creation
            access_token_identity = {"user_id": user_id, "email": email, "type": "access"} # Custom claims
            access_token = create_access_token(identity=user_id, additional_claims=access_token_identity)
            
            refresh_token_identity = {"user_id": user_id, "type": "refresh"}
            refresh_token_str = create_refresh_token(identity=user_id, additional_claims=refresh_token_identity)
            
            refresh_expires_at = datetime.now(timezone.utc) + timedelta(days=app_config.JWT_REFRESH_TOKEN_EXPIRES_DAYS) # Using timedelta from config
            store_token(
                token_id=user_id, # PK for refresh token record
                user_id=user_id,
                token_type="refresh",
                token_string=refresh_token_str, # The actual JWT refresh token
                expires_at_iso=refresh_expires_at.isoformat()
            )

            date_of_birth_db = user.get("dateOfBirth", "") # Stored as YYYY-MM-DD
            date_of_birth_api_fmt = date_of_birth_db
            if date_of_birth_db:
                try:
                    # Convert YYYY-MM-DD from DB to dd-MM-YYYY for API response
                    date_of_birth_api_fmt = datetime.strptime(date_of_birth_db, "%Y-%m-%d").strftime("%d-%m-%Y")
                except ValueError:
                    current_app.logger.warning(f"Could not parse dateOfBirth '{date_of_birth_db}' for user {user_id} during login. Sending as is.")

            return jsonify({
                "token": access_token, # API Spec: "token"
                "refreshToken": refresh_token_str, # API Spec: "refreshToken"
                "userId": user_id,
                "userFullName": user.get("userFullName", user.get("fullName", "")),
                "userType": user.get("type", "PATIENT"), # Default type, ensure this is correct
                "dateOfBirth": date_of_birth_api_fmt
            }), 200
        else:
            return jsonify({"error": "Invalid credentials"}), 401
    except Exception as e:
        current_app.logger.error(f"Login error: {e}", exc_info=True)
        return jsonify({"error": "Internal Server Error"}), 500

@refresh_bp.route('/refresh', methods=['POST'])
@jwt_required(refresh=True) # This ensures it's a refresh token
def refresh_access_token():
    try:
        current_user_id = get_jwt_identity() # This is user_id from the refresh token
        user = get_user_by_id(current_user_id)
        if not user:
            return jsonify({"error": "User not found for refresh token"}), 401

        new_access_token_identity = {"user_id": current_user_id, "email": user.get("email"), "type": "access"}
        new_access_token = create_access_token(identity=current_user_id, additional_claims=new_access_token_identity)
        
        # Optionally issue a new refresh token (good for refresh token rotation)
        new_refresh_token_str = create_refresh_token(identity=current_user_id, additional_claims={"user_id": current_user_id, "type": "refresh"})
        refresh_expires_at = datetime.now(timezone.utc) + timedelta(days=app_config.JWT_REFRESH_TOKEN_EXPIRES_DAYS)
        store_token(token_id=current_user_id, user_id=current_user_id, token_type="refresh", token_string=new_refresh_token_str, expires_at_iso=refresh_expires_at.isoformat())

        return jsonify({
            "accessToken": new_access_token, # API Spec: "accessToken"
            "refreshToken": new_refresh_token_str, # API Spec: "refreshToken" (optional new)
            "expiresIn": app_config.JWT_ACCESS_TOKEN_EXPIRES_MINUTES * 60 # In seconds
        }), 200

    except Exception as e: # Catch other errors, JWT errors are handled by Flask-JWT
        current_app.logger.error(f"Token refresh error: {e}", exc_info=True)
        return jsonify({"error": "Internal Server Error during token refresh"}), 500


# --- Password Reset Routes ---
# NOTE: Not in use! Hard coded env var (FEATURE_ENABLE_PASSWORD_RESET) is False, the following is not in use.
#       Future developers, please adapt with regard to facility needs and infrastructure regarding auth methods - phone\email\etc.

if app_config.FEATURE_ENABLE_PASSWORD_RESET: 
    @auth_bp.route("/forgot-password", methods=["POST"])
    def forgot_password():
        email = request.args.get("email")
        if not email: return jsonify({"error": "Email is required"}), 400
        user = get_user_by_email(email)
        if not user: return jsonify({"error": "Email address not registered."}), 404

        reset_token_value = generate_reset_token() 
        expiry_delta = timedelta(hours=1) 
        expires_at = datetime.now(timezone.utc) + expiry_delta
        
        store_token(
            token_id=reset_token_value, # Use token string as ID for reset tokens
            user_id=user["id"],
            token_type="password_reset",
            token_string=reset_token_value,
            expires_at_iso=expires_at.isoformat(),
            email=email # Store email for verification if needed
        )
        # ... (send email with reset_token_value) ...
        current_app.logger.info(f"Password reset token generated for {email}: {reset_token_value}")
        return jsonify({"success": True, "message": "If this email exists, a reset link has been sent."}), 200

    @auth_bp.route("/change-password", methods=["POST"]) 
    def reset_password_with_token():
        data = request.get_json()
        token_from_client = data.get("tokenSec")
        new_password = data.get("newPassword", data.get("new_password")) # Accommodate variations

        if not token_from_client or not new_password:
            return jsonify({"error": "Token and new password are required"}), 400

        token_doc = get_token_by_id(token_id=token_from_client, token_type="password_reset")
        if not token_doc: # get_token_by_id also checks expiry
            return jsonify({"error": "Invalid or expired token"}), 400 # 400 or 401

        user_id = token_doc.get("user_id")

        hashed_new_password = hash_password(new_password)
        if update_user_password(user_id, hashed_new_password): # Uses updated function
            delete_token(token_id=token_from_client) # Delete used token
            return jsonify({"success": True, "message": "Password has been reset successfully."}), 200
        else:
            return jsonify({"error": "Password reset failed. User not found or DB error."}), 500
        