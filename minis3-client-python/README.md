# minis3 — Python client

Python client for [miniS3](../README.md), a self-hosted S3-style object store. It covers the v2 object API and pre-signed URLs, and authenticates with an **API key** (`ms3_...`). It's the Python counterpart of the Java [`minis3-client`](../minis3-client).

- Upload from bytes, text, file objects, iterables of chunks or paths; files are **streamed**, never read fully into memory
- Download whole, as a stream, or straight to a file
- Lazy, cursor-based listing by prefix
- Pre-signed GET/PUT links
- Typed exceptions for every failure

Requires Python 3.10+ and [httpx](https://www.python-httpx.org/).

## Install

From this folder (editable, for development):

```bash
pip install -e minis3-client-python
```

From another project, straight from GitHub:

```bash
pip install "minis3 @ git+https://github.com/Parker023/miniS3.git#subdirectory=minis3-client-python"
```

## Quick start

```python
from minis3 import MiniS3Client

with MiniS3Client("https://<your-minis3-host>/storage/minis3", api_key="ms3_...") as storage:
    storage.put_object("photos", "2026/cat.png", open("cat.png", "rb"), content_type="image/png")

    data = storage.get_object_bytes("photos", "2026/cat.png")

    for obj in storage.list_all_objects("photos", prefix="2026/"):
        print(obj.key, obj.size, obj.last_modified)

    link = storage.presign_get("photos", "2026/cat.png", expires_in=600)
    print(link.url)  # works in any browser for 10 minutes, no API key needed
```

The endpoint is the server's base URL **including** its context path (`/storage/minis3`). Create the API key with `POST /api/v1/keys` while logged in (see the [main README](../README.md#api-keys-jwt-only)), and keep it out of your code, e.g. `api_key=os.environ["MINIS3_API_KEY"]`.

The client is thread-safe: create one and reuse it, and close it when done (or use `with`).

## API

### Upload

```python
storage.put_object(bucket, key, data, content_type=None) -> ObjectInfo
```

Creates the object, or replaces it if the key exists. `data` can be:

| Type | Sent as | Default Content-Type |
| --- | --- | --- |
| `bytes` / `bytearray` | as is | `application/octet-stream` |
| `str` | UTF-8 | `text/plain; charset=utf-8` |
| binary file object (`open(..., "rb")`, `io.BytesIO`) | streamed in 64 KB chunks | `application/octet-stream` |
| `pathlib.Path` | the file, streamed | `application/octet-stream` |
| iterable of `bytes` (e.g. a generator) | streamed chunk by chunk | `application/octet-stream` |

Pass `content_type` to override the default. Keys can contain `/`, spaces and non-ASCII characters; each segment is percent-encoded for you.

### Download

```python
storage.get_object_bytes(bucket, key) -> bytes
storage.download_file(bucket, key, "local/path") -> ObjectInfo   # streams to disk

with storage.get_object(bucket, key) as obj:       # MiniS3Object
    print(obj.info.size, obj.info.content_type)
    for chunk in obj.iter_bytes():                  # or obj.read()
        ...
```

Always close a `MiniS3Object` (the `with` block does it) so the connection is released.

### Metadata, existence, delete

```python
storage.head_object(bucket, key) -> ObjectInfo | None   # None if it doesn't exist
storage.object_exists(bucket, key) -> bool
storage.delete_object(bucket, key)                       # NotFoundError if it doesn't exist
```

### List

```python
storage.list_objects(bucket, prefix="", cursor=None, limit=1000) -> ObjectPage
storage.list_all_objects(bucket, prefix="", page_size=1000) -> Iterator[ObjectInfo]
```

Keys come back in key order. `list_all_objects` fetches the next page only when you iterate past the current one; with `list_objects`, pass `page.next_cursor` back while `page.truncated` is true.

### Pre-signed URLs

```python
storage.presign_get(bucket, key, expires_in=900) -> PresignedUrl   # needs the READ scope
storage.presign_put(bucket, key, expires_in=900) -> PresignedUrl   # needs the WRITE scope
```

`expires_in` is seconds or a `timedelta`, up to 7 days. The returned `url` works without any credentials until `expires_at`. Anyone holding it can use it, so share it like a password. Links issued with an API key stop working as soon as that key is revoked.

### Types

| Type | Fields |
| --- | --- |
| `ObjectInfo` | `key`, `size`, `content_type`, `etag` (hex MD5, no quotes), `last_modified` (timezone-aware `datetime`) |
| `ObjectPage` | `bucket`, `prefix`, `objects`, `next_cursor`, `truncated` |
| `PresignedUrl` | `url`, `method`, `expires_at` |

## Errors

Every failure raises `MiniS3Error` or a subclass, with `status_code` and `message`:

| Exception | Status | When |
| --- | --- | --- |
| `NotFoundError` | 404 | bucket or object doesn't exist (bucket names are resolved among the key owner's buckets) |
| `AccessDeniedError` | 401 / 403 | bad, expired or revoked key, or missing scope / bucket access |
| `PayloadTooLargeError` | 413 | one upload is over the server's per-file limit (20 MB by default) |
| `QuotaExceededError` | 507 | the upload would exceed the owner's storage quota |
| `MiniS3Error` | other | any other error response |
| `MiniS3Error` | `0` | no response at all: connection refused, timeout, DNS failure |

```python
from minis3 import NotFoundError

try:
    data = storage.get_object_bytes("photos", "missing.png")
except NotFoundError:
    data = None
```

Blank bucket names or keys raise `ValueError` before any request is sent.

## Configuration

```python
MiniS3Client(endpoint, api_key, *, timeout=30.0, http_client=None)
```

- `timeout`: seconds per network operation (connect, each read, each write); `None` disables it.
- `http_client`: your own `httpx.Client` for proxies, custom TLS or retries. The client doesn't close one you pass in.

## Limits to know

- Keys: 1–255 characters, no control characters. The server's firewall also rejects keys containing `%`, `;`, `//` or `..` segments.
- One upload: 20 MB by default (server setting `minis3.upload.max-size`).
- Total per user: 5 GB by default (server setting `MINIS3_USER_QUOTA`).
