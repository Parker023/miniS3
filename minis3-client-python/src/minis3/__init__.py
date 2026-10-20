"""Python client for miniS3. Placeholder: the API below is planned, not implemented."""

from minis3.client import MiniS3Client, ObjectInfo, ObjectPage, PresignedUrl
from minis3.errors import AccessDeniedError, MiniS3Error, NotFoundError

__all__ = [
    "MiniS3Client",
    "ObjectInfo",
    "ObjectPage",
    "PresignedUrl",
    "MiniS3Error",
    "NotFoundError",
    "AccessDeniedError",
]
