"""Client for the miniS3 v2 object API and pre-signed URLs, authenticated with an API key."""

from __future__ import annotations

import io
from datetime import timedelta
from os import PathLike
from pathlib import Path
from typing import Any, BinaryIO, Iterable, Iterator, Optional, Union

import httpx

from minis3._encoding import encode_key, encode_segment
from minis3.errors import (
    AccessDeniedError,
    MiniS3Error,
    NotFoundError,
    PayloadTooLargeError,
    QuotaExceededError,
)
from minis3.models import ObjectInfo, ObjectPage, PresignedUrl, parse_http_date

Content = Union[bytes, bytearray, str, BinaryIO, Iterable[bytes], "PathLike[str]"]

_DEFAULT_CONTENT_TYPE = "application/octet-stream"
_CHUNK = 64 * 1024


class MiniS3Object:
    """A downloaded object: metadata plus a stream of its content, read straight from the connection.

    Use it as a context manager (or call close()) so the connection is released.
    """

    def __init__(self, info: ObjectInfo, response: httpx.Response):
        self.info = info
        self._response = response

    def iter_bytes(self, chunk_size: int = _CHUNK) -> Iterator[bytes]:
        """Stream the content in chunks, e.g. to write a large file to disk."""
        yield from self._response.iter_bytes(chunk_size)

    def read(self) -> bytes:
        """Read the whole content into memory."""
        return self._response.read()

    def close(self) -> None:
        self._response.close()

    def __enter__(self) -> "MiniS3Object":
        return self

    def __exit__(self, *exc: object) -> None:
        self.close()


class MiniS3Client:
    """Client for a miniS3 server.

    >>> with MiniS3Client("https://host/storage/minis3", api_key="ms3_...") as storage:
    ...     storage.put_object("photos", "2026/cat.png", open("cat.png", "rb"), content_type="image/png")

    Thread-safe; create one and share it. Every failure raises MiniS3Error (or a subclass).
    """

    def __init__(
        self,
        endpoint: str,
        api_key: str,
        *,
        timeout: Optional[float] = 30.0,
        http_client: Optional[httpx.Client] = None,
    ):
        """
        :param endpoint: base URL including the context path, e.g. "http://localhost:2399/storage/minis3"
        :param api_key: an API key (ms3_...) created with POST /api/v1/keys
        :param timeout: seconds per network operation (connect, each read, each write); None = no limit
        :param http_client: your own httpx.Client (proxies, TLS, transport); it is not closed by close()
        """
        if not endpoint or not endpoint.strip():
            raise ValueError("endpoint must not be blank")
        if not api_key or not api_key.strip():
            raise ValueError("api_key must not be blank")
        self.endpoint = endpoint.rstrip("/")
        self._headers = {"Authorization": f"Bearer {api_key}", "User-Agent": "minis3-python"}
        self._owns_client = http_client is None
        self._http = http_client or httpx.Client(timeout=timeout)

    # ---------- upload ----------

    def put_object(self, bucket: str, key: str, data: Content, content_type: Optional[str] = None) -> ObjectInfo:
        """Create or replace an object.

        data can be bytes, str (sent as UTF-8), a binary file object, an iterable of byte chunks,
        or a filesystem path. Files and iterables are streamed, never read fully into memory.
        """
        body, default_type = _body(data)
        headers = {"Content-Type": content_type or default_type}
        response = self._send("PUT", self._object_url(bucket, key), headers=headers, content=body)
        return ObjectInfo.from_json(response.json())

    # ---------- download ----------

    def get_object(self, bucket: str, key: str) -> MiniS3Object:
        """Stream an object. Close the result (or use `with`) to release the connection.

        :raises NotFoundError: if the bucket or object doesn't exist
        """
        request = self._http.build_request("GET", self._object_url(bucket, key), headers=self._headers)
        response = self._transport(request, stream=True)
        if not response.is_success:
            response.read()
            response.close()
            raise _error(response)
        return MiniS3Object(_info_from_headers(key, response.headers), response)

    def get_object_bytes(self, bucket: str, key: str) -> bytes:
        with self.get_object(bucket, key) as obj:
            return obj.read()

    def download_file(self, bucket: str, key: str, destination: Union[str, "PathLike[str]"]) -> ObjectInfo:
        """Stream an object to a local file and return its metadata."""
        with self.get_object(bucket, key) as obj, open(destination, "wb") as out:
            for chunk in obj.iter_bytes():
                out.write(chunk)
            return obj.info

    def head_object(self, bucket: str, key: str) -> Optional[ObjectInfo]:
        """Metadata of an object, or None if it doesn't exist."""
        request = self._http.build_request("HEAD", self._object_url(bucket, key), headers=self._headers)
        response = self._transport(request)
        if response.status_code == 404:
            return None
        if not response.is_success:
            raise _error(response)
        return _info_from_headers(key, response.headers)

    def object_exists(self, bucket: str, key: str) -> bool:
        return self.head_object(bucket, key) is not None

    # ---------- delete ----------

    def delete_object(self, bucket: str, key: str) -> None:
        """:raises NotFoundError: if the object doesn't exist"""
        self._send("DELETE", self._object_url(bucket, key))

    # ---------- list ----------

    def list_objects(
        self, bucket: str, prefix: str = "", cursor: Optional[str] = None, limit: int = 1000
    ) -> ObjectPage:
        """One page of keys under prefix, in key order (limit 1-1000)."""
        params: dict[str, Any] = {"limit": limit}
        if prefix:
            params["prefix"] = prefix
        if cursor:
            params["cursor"] = cursor
        response = self._send("GET", f"{self._bucket_url(bucket)}/objects", params=params)
        return ObjectPage.from_json(response.json())

    def list_all_objects(self, bucket: str, prefix: str = "", page_size: int = 1000) -> Iterator[ObjectInfo]:
        """Every object under prefix, fetching further pages lazily as you iterate."""
        cursor: Optional[str] = None
        while True:
            page = self.list_objects(bucket, prefix, cursor, page_size)
            yield from page.objects
            if not page.truncated:
                return
            cursor = page.next_cursor

    # ---------- pre-signed URLs ----------

    def presign_get(self, bucket: str, key: str, expires_in: Union[int, timedelta] = 900) -> PresignedUrl:
        """A link anyone can use to download the object until it expires (max 7 days). Needs READ."""
        return self._presign(bucket, key, "GET", expires_in)

    def presign_put(self, bucket: str, key: str, expires_in: Union[int, timedelta] = 900) -> PresignedUrl:
        """A link anyone can use to upload the object until it expires (max 7 days). Needs WRITE."""
        return self._presign(bucket, key, "PUT", expires_in)

    def _presign(self, bucket: str, key: str, method: str, expires_in: Union[int, timedelta]) -> PresignedUrl:
        _require(key, "key")
        seconds = int(expires_in.total_seconds()) if isinstance(expires_in, timedelta) else int(expires_in)
        body = {"key": key, "method": method, "expiresInSeconds": seconds}
        response = self._send("POST", f"{self._bucket_url(bucket)}/presign", json=body)
        return PresignedUrl.from_json(response.json())

    # ---------- lifecycle ----------

    def close(self) -> None:
        if self._owns_client:
            self._http.close()

    def __enter__(self) -> "MiniS3Client":
        return self

    def __exit__(self, *exc: object) -> None:
        self.close()

    # ---------- plumbing ----------

    def _bucket_url(self, bucket: str) -> str:
        return f"{self.endpoint}/api/v2/buckets/{encode_segment(_require(bucket, 'bucket'))}"

    def _object_url(self, bucket: str, key: str) -> str:
        return f"{self._bucket_url(bucket)}/objects/{encode_key(_require(key, 'key'))}"

    def _send(self, method: str, url: str, *, headers: Optional[dict[str, str]] = None, **kwargs: Any) -> httpx.Response:
        request = self._http.build_request(method, url, headers={**self._headers, **(headers or {})}, **kwargs)
        response = self._transport(request)
        if not response.is_success:
            raise _error(response)
        return response

    def _transport(self, request: httpx.Request, stream: bool = False) -> httpx.Response:
        try:
            return self._http.send(request, stream=stream)
        except httpx.HTTPError as e:
            raise MiniS3Error(0, f"Request to miniS3 failed: {request.method} {request.url}: {e}") from e


def _require(value: str, name: str) -> str:
    if not value or not value.strip():
        raise ValueError(f"{name} must not be blank")
    return value


def _body(data: Content) -> tuple[Any, str]:
    """Turn the supported inputs into an httpx request body plus a default Content-Type."""
    if isinstance(data, (bytes, bytearray)):
        return bytes(data), _DEFAULT_CONTENT_TYPE
    if isinstance(data, str):
        return data.encode("utf-8"), "text/plain; charset=utf-8"
    if isinstance(data, PathLike):
        return _iter_path(Path(data)), _DEFAULT_CONTENT_TYPE
    if isinstance(data, io.IOBase) or hasattr(data, "read"):
        return _iter_file(data), _DEFAULT_CONTENT_TYPE  # type: ignore[arg-type]
    return iter(data), _DEFAULT_CONTENT_TYPE


def _iter_file(f: BinaryIO) -> Iterator[bytes]:
    while chunk := f.read(_CHUNK):
        yield chunk


def _iter_path(path: Path) -> Iterator[bytes]:
    with open(path, "rb") as f:
        yield from _iter_file(f)


def _unquote_etag(etag: Optional[str]) -> Optional[str]:
    if not etag:
        return None
    value = etag[2:] if etag.startswith("W/") else etag
    return value[1:-1] if len(value) >= 2 and value[0] == value[-1] == '"' else value


def _info_from_headers(key: str, headers: httpx.Headers) -> ObjectInfo:
    length = headers.get("Content-Length")
    return ObjectInfo(
        key=key,
        size=int(length) if length is not None else -1,
        content_type=headers.get("Content-Type"),
        etag=_unquote_etag(headers.get("ETag")),
        last_modified=parse_http_date(headers.get("Last-Modified")),
    )


def _error(response: httpx.Response) -> MiniS3Error:
    """Map an error response ({"message": ..., "timestamp": ...}) to the matching exception."""
    message = f"HTTP {response.status_code}"
    try:
        body = response.json()
        if isinstance(body, dict) and isinstance(body.get("message"), str):
            message = body["message"]
    except ValueError:
        text = response.text.strip()
        if text:
            message = text
    status = response.status_code
    if status == 404:
        return NotFoundError(status, message)
    if status in (401, 403):
        return AccessDeniedError(status, message)
    if status == 507:
        return QuotaExceededError(status, message)
    if status == 413:
        return PayloadTooLargeError(status, message)
    return MiniS3Error(status, message)
