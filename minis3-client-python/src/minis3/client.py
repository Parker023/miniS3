"""Planned API, mirroring the Java MiniS3Client. Not implemented yet."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import BinaryIO, Iterator, Optional

_TODO = "minis3 Python client is a placeholder; see minis3-client-python/README.md"


@dataclass(frozen=True)
class ObjectInfo:
    key: str
    size: int
    content_type: Optional[str]
    etag: Optional[str]  # hex MD5, no quotes
    last_modified: Optional[datetime]


@dataclass(frozen=True)
class ObjectPage:
    bucket: str
    prefix: str
    objects: list[ObjectInfo]
    next_cursor: Optional[str]
    truncated: bool


@dataclass(frozen=True)
class PresignedUrl:
    url: str
    method: str
    expires_at: datetime


class MiniS3Client:
    def __init__(self, endpoint: str, api_key: str, timeout: Optional[float] = None):
        self.endpoint = endpoint.rstrip("/")
        self.api_key = api_key
        self.timeout = timeout

    def put_object(self, bucket: str, key: str, data: bytes | BinaryIO, content_type: Optional[str] = None) -> ObjectInfo:
        raise NotImplementedError(_TODO)

    def get_object(self, bucket: str, key: str) -> BinaryIO:
        raise NotImplementedError(_TODO)

    def get_object_bytes(self, bucket: str, key: str) -> bytes:
        raise NotImplementedError(_TODO)

    def head_object(self, bucket: str, key: str) -> Optional[ObjectInfo]:
        raise NotImplementedError(_TODO)

    def object_exists(self, bucket: str, key: str) -> bool:
        raise NotImplementedError(_TODO)

    def delete_object(self, bucket: str, key: str) -> None:
        raise NotImplementedError(_TODO)

    def list_objects(self, bucket: str, prefix: str = "", cursor: Optional[str] = None, limit: int = 1000) -> ObjectPage:
        raise NotImplementedError(_TODO)

    def list_all_objects(self, bucket: str, prefix: str = "") -> Iterator[ObjectInfo]:
        raise NotImplementedError(_TODO)

    def presign_get(self, bucket: str, key: str, expires_in: int = 900) -> PresignedUrl:
        raise NotImplementedError(_TODO)

    def presign_put(self, bucket: str, key: str, expires_in: int = 900) -> PresignedUrl:
        raise NotImplementedError(_TODO)

    def close(self) -> None:
        pass

    def __enter__(self) -> "MiniS3Client":
        return self

    def __exit__(self, *exc) -> None:
        self.close()
