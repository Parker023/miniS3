# minis3 (Python client) — placeholder

**Status: not implemented yet.** This folder reserves the layout and the planned API. Every method raises `NotImplementedError`.

The plan is a Python counterpart of the Java [`minis3-client`](../minis3-client): the v2 object API and pre-signed URLs, authenticated with an API key (`ms3_...`).

## Planned usage

```python
from minis3 import MiniS3Client

with MiniS3Client("https://minis3.nullcanvas.blog/storage/minis3", api_key="ms3_...") as storage:
    storage.put_object("invoices", "2026/42.pdf", open("42.pdf", "rb"), content_type="application/pdf")
    data = storage.get_object_bytes("invoices", "2026/42.pdf")
    for obj in storage.list_all_objects("invoices", prefix="2026/"):
        print(obj.key, obj.size)
    link = storage.presign_get("invoices", "2026/42.pdf", expires_in=600)
```

## To build it out
- **HTTP:** `httpx` (sync first, async later), with streaming uploads and downloads.
- **Mirror the Java client:**
  - `put_object`, `get_object` (streaming), `get_object_bytes`, `head_object`, `object_exists`, `delete_object`, `list_objects`, `list_all_objects` (lazy paging), `presign_get` and `presign_put`
  - percent-encode each key segment, keeping the `/` between segments
- **Errors:** `MiniS3Error(status_code, message)`, with `NotFoundError` (404) and `AccessDeniedError` (401/403) as subclasses; `status_code == 0` means there was no response.
- **Tests:** run against the service started locally (`./gradlew :service:bootRun`).
- **Publish:** to PyPI, or to GitHub Packages, from a `py-v*` tag workflow.
