#profiles/routes.py

from flask import Blueprint, request, jsonify
from flask_jwt_extended import jwt_required, get_jwt_identity
from database.cosmos import search_patients_by_query, get_user_by_id

profiles_bp = Blueprint("profiles", __name__)

@profiles_bp.route("/search", methods=["GET"])
@jwt_required()
def search_profiles():
    try:
        # Get current user ID
        current_user_id = get_jwt_identity()
        current_user = get_user_by_id(current_user_id)

        if not current_user:
            return jsonify({"error": "Unauthorized"}), 401

        # Restrict access to therapists only
        if current_user.get("type", "").lower() != "therapist":
            return jsonify({"error": "Forbidden: Patients cannot use this endpoint"}), 403

        # Get query parameter
        query = request.args.get("query", "").strip()
        if not query:
            return jsonify([]), 200  # No query returns empty list

        # Search for patient profiles
        raw_results = search_patients_by_query(query)
        formatted_results = []
        for patient in raw_results:
            formatted_results.append({
                "id": patient.get("id"),
                "fullName": patient.get("userFullName", patient.get("fullName")),
                "email": patient.get("email"),
                "dateOfBirth": patient.get("dateOfBirth"),
                "pictureUrl": patient.get("pictureUrl", "")
            })
        return jsonify(formatted_results), 200

    except Exception as e:
        print(f"[SEARCH PROFILES] Error: {str(e)}")
        return jsonify({"error": "Internal server error"}), 500



@profiles_bp.route("/me", methods=["GET"])
@jwt_required()
def get_own_profile():
    try:
        user_id = get_jwt_identity()
        user = get_user_by_id(user_id)

        if not user:
            return jsonify({"error": "User not found"}), 404

        # Build the response format as expected
        profile = {
            "id": user.get("id"),
            "fullName": user.get("userFullName"),
            "email": user.get("email"),
            "dateOfBirth": user.get("dateOfBirth"),
            "pictureUrl": user.get("pictureUrl")
        }

        return jsonify(profile), 200

    except Exception as e:
        print(f"[GET OWN PROFILE] Error: {str(e)}")
        return jsonify({"error": "Internal server error"}), 500
