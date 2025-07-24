# auth/utils.py
import secrets
import string
import datetime
import bcrypt
def generate_reset_token(length: int = 64) -> str:
    """
    Generates a secure reset token with default length 64.
    """
    chars = string.ascii_letters + string.digits
    return ''.join(secrets.choice(chars) for _ in range(length))


def get_token_expiry(hours_valid=1):
    """
    Returns an ISO timestamp 1 hour from now.
    """
    return (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=hours_valid)).isoformat()

def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')





def verify_password(password: str, hashed: str) -> bool:
    """
    Verify the entered password against the bcrypt-hashed version.
    """
    return bcrypt.checkpw(password.encode('utf-8'), hashed.encode('utf-8'))

