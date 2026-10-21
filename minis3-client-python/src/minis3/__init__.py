"""Python client for miniS3, a self-hosted S3-style object store."""

from minis3.client import MiniS3Client, MiniS3Object
from minis3.errors import (
    AccessDeniedError,
    MiniS3Error,
    NotFoundError,
    PayloadTooLargeError,
    QuotaExceededError,
)
from minis3.models import ObjectInfo, ObjectPage, PresignedUrl

__version__ = "0.1.0"

__all__ = [
    "MiniS3Client",
    "MiniS3Object",
    "ObjectInfo",
    "ObjectPage",
    "PresignedUrl",
    "MiniS3Error",
    "NotFoundError",
    "AccessDeniedError",
    "QuotaExceededError",
    "PayloadTooLargeError",
]
