class MiniS3Error(Exception):
    """Any failure calling miniS3. status_code is the HTTP status, or 0 when no response was received."""

    def __init__(self, status_code: int, message: str):
        super().__init__(message)
        self.status_code = status_code


class NotFoundError(MiniS3Error):
    """404: the bucket or object doesn't exist."""


class AccessDeniedError(MiniS3Error):
    """401 (bad, expired or revoked key) or 403 (missing scope or bucket access)."""
