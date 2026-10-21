"""Every failure the client raises is a MiniS3Error."""


class MiniS3Error(Exception):
    """Any failure calling miniS3.

    status_code is the HTTP status, or 0 when no response was received
    (connection refused, timeout, DNS failure).
    """

    def __init__(self, status_code: int, message: str):
        super().__init__(message)
        self.status_code = status_code
        self.message = message

    def __repr__(self) -> str:
        return f"{type(self).__name__}(status_code={self.status_code}, message={self.message!r})"


class NotFoundError(MiniS3Error):
    """404: the bucket or object doesn't exist (bucket names are looked up among the key owner's buckets)."""


class AccessDeniedError(MiniS3Error):
    """401 (missing, invalid, expired or revoked API key) or 403 (key lacks the scope or bucket access)."""


class QuotaExceededError(MiniS3Error):
    """507: the upload would take the bucket owner over their storage quota."""


class PayloadTooLargeError(MiniS3Error):
    """413: the single upload is larger than the server's per-file limit."""
