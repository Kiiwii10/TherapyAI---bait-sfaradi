# devices/routes.py

from flask import Blueprint, request, jsonify
from flask_jwt_extended import jwt_required, get_jwt_identity
from database.cosmos import register_device, unregister_device

devices_bp = Blueprint("devices", __name__)


@devices_bp.route("/register", methods=["POST"])
@jwt_required()  # Ensure the user is authenticated using JWT
def register_device_route():
    try:
        # Get the request data
        data = request.get_json()

        # Extract relevant fields
        fcm_device_token = data.get("token")  # Firebase device token from request
        platform = data.get("platform")  # Device platform (e.g., "android" or "ios"), might be useful later.
        user_id_from_payload = data.get("user_id")  # Unique user identifier from request
        user_type = data.get("user_type")  # User type (e.g., "PATIENT", "THERAPIST")
        # Validate input
        if not fcm_device_token or not platform or not user_id_from_payload or not user_type:
            return jsonify({"error": "token, platform, user_id, and user_type are required"}), 400

        # Ensure the token is registered for the correct user
        current_user_jwt_id = get_jwt_identity()  # Get user ID from JWT token

        if current_user_jwt_id != user_id_from_payload:
            return jsonify({"error": "User ID mismatch"}), 403
        
        # Call the function to register the device in the database
        device_registered_successfully = register_device(
            user_id=user_id_from_payload,
            fcm_token=fcm_device_token, # Use fcm_token as the keyword
            platform=platform,
            user_type=user_type
        )
        if not device_registered_successfully:
            return jsonify({"error": "Database error while registering the device."}), 500  

        # Return a success response with no content (204)
        return '', 204  # 204 No Content

    except Exception as e:
        print(f"Error: {str(e)}")
        return jsonify({"error": "Internal server error."}), 500  





@devices_bp.route("/unregister/<user_id>", methods=["POST"])
@jwt_required()  # Ensure the user is authenticated using JWT
def unregister_device_route(user_id):
    try:
        current_user = get_jwt_identity()
        if current_user != user_id:
            return jsonify({"error": "Forbidden: You cannot unregister devices for another user."}), 403

        device = unregister_device(user_id)

        if not device:
            return jsonify({"error": "User ID not found or device does not exist."}), 404  

        return '', 204  # No content response as per the requirements

    except Exception as e:
        print(f"Error: {str(e)}")  
        return jsonify({"error": "Internal server error."}), 500  

