# miniS3

A small, monolithic object-storage service inspired by Amazon S3. Users register, log in with a JWT, create **buckets**, and upload, list and download **files** inside those buckets. File bytes live on the local filesystem; metadata lives in PostgreSQL.

## Tech stack

| Area        | Choice                                                      |
|-------------|-------------------------------------------------------------|
| Language    | Java 21                                                     |
| Framework   | Spring Boot 4.0.3 (Web MVC, Data JPA, Validation, Security)  |
| Auth        | Stateless JWT (jjwt 0.12.6, HMAC-SHA), BCrypt passwords     |
| Database    | PostgreSQL (runtime), H2 in-memory (tests)                  |
| Storage     | Local disk, behind a `StorageService` interface             |
| Mapping     | ModelMapper, Lombok                                         |
| Build       | Gradle (wrapper included)                                   |

## Getting started

### Prerequisites

- JDK 21
- PostgreSQL listening on `localhost:5433` with a database named `minis3`

### Environment variables

| Variable          | Required | Description                                                         |
|-------------------|----------|---------------------------------------------------------------------|
| `DB_USERNAME`     | yes      | PostgreSQL user                                                     |
| `DB_PASSWORD`     | yes      | PostgreSQL password                                                 |
| `JWT_SECRET`      | yes      | **Base64-encoded** HMAC key, at least 256 bits (32 bytes) once decoded |
| `JWT_EXPIRATION`  | no       | Token lifetime in ms (default `3600000` = 1 hour)                   |

Generate a secret with:

```bash
openssl rand -base64 32
```

### Run

```bash
./gradlew bootRun
```

The service starts on **port 2399** under the context path **`/storage/minis3`**, so every endpoint below is prefixed with:

```
http://localhost:2399/storage/minis3
```

### Test

```bash
./gradlew test
```

Tests use H2 via `application-test.yaml`.

## Configuration

Key settings from [application.yaml](src/main/resources/application.yaml):

| Property                          | Value              | Meaning                                  |
|-----------------------------------|--------------------|------------------------------------------|
| `server.port`                     | `2399`             |                                          |
| `server.servlet.context-path`     | `/storage/minis3`  |                                          |
| `spring.servlet.multipart.max-file-size` | `20MB`      | Hard limit enforced by Spring            |
| `minis3.upload.max-size`          | `5MB`              | Application-level per-file upload limit  |
| `minis3.app.storage.location`     | `./storage`        | Root directory for stored file bytes     |

Files are stored on disk as `<storage-root>/<bucketPublicId>/<filePublicId>`; the original filename and content type are kept in the database.

## API

All endpoints except **register** and **login** require an `Authorization: Bearer <credential>` header. Two kinds of credential are accepted:

| Credential | Who uses it | Looks like | Can do |
|------------|-------------|------------|--------|
| **JWT** from `/auth/login` | people, the console | `eyJhbGciOi...` | everything on buckets they own, plus manage API keys |
| **API key** from `/keys` | other services | `ms3_<prefix>_<secret>` | only its granted scopes (`READ`, `WRITE`, `DELETE`), optionally only on chosen buckets |

Unauthenticated requests (missing, invalid, expired or revoked credential) get `401 {"message":"Unauthorized", ...}`.
Authenticated requests without the needed scope or bucket access get `403 {"message":"Access Denied", ...}`.

### Users & auth

| Method | Path                        | Body                                   | Success |
|--------|-----------------------------|----------------------------------------|---------|
| POST   | `/api/v1/users/register`    | `{ "name", "email", "password" }`      | `201` + `{ name, email, userId }` |
| POST   | `/api/v1/auth/login`        | `{ "email", "password" }`              | `200` + `{ name, message, token }` (token also in `Authorization` header) |

Validation: name 4–50 chars, valid email, password 8–25 chars.

### API keys (JWT only)

| Method | Path                    | Body | Success |
|--------|-------------------------|------|---------|
| POST   | `/api/v1/keys`          | `{ "name", "scopes": ["READ","WRITE","DELETE"], "bucketIds"?: [...], "expiresInDays"?: 1-365 }` | `201` + key details **and the raw `apiKey`** (shown only this once) |
| GET    | `/api/v1/keys`          | — | `200` + page of key details (never the raw key) |
| DELETE | `/api/v1/keys/{keyId}`  | — | `200` + key details with `active: false` (revoked; takes effect immediately) |

- Omit `bucketIds` to let the key use every bucket you own; you can only list buckets you own.
- Omit `expiresInDays` for a key that never expires.
- Only a SHA-256 hash of the secret is stored. A lost key can't be recovered, so revoke it and create a new one.
- API keys can't call `/api/v1/keys` themselves, so a leaked key can't create more keys.

Scope needed per endpoint:

| Endpoint | Scope |
|----------|-------|
| List buckets, list files, download file | `READ` |
| Create bucket, upload file | `WRITE` |
| Delete bucket | `DELETE` |

A key restricted with `bucketIds` only sees those buckets when listing, and gets `403` on any other bucket.

### Buckets

| Method | Path                         | Body                    | Success |
|--------|------------------------------|-------------------------|---------|
| POST   | `/api/v1/bucket/`            | `{ "bucketName" }` (3–20 chars) | `201` + `{ message, bucketName, bucketPublicId }` |
| GET    | `/api/v1/bucket/`            | —                       | `200` + page of `{ bucketName, bucketId, createdAt }` (your buckets only) |
| DELETE | `/api/v1/bucket/{bucketId}`  | —                       | `200` + `{ bucketName }` |

Bucket names are unique per owner and may contain only letters, digits, `.`, `_` and `-`, because they appear in v2 URLs. Listing supports `?page=&size=&sort=` (default 20 per page, newest first).

### Files

| Method | Path                                                     | Body                              | Success |
|--------|----------------------------------------------------------|-----------------------------------|---------|
| POST   | `/api/v1/file/buckets/{bucketId}/files`                  | multipart, field `multipartFile`  | `201` + `{ fileName, size, contentType, filePublicId }` |
| GET    | `/api/v1/file/buckets/{bucketId}/files`                  | —                                 | `200` + page of file details |
| GET    | `/api/v1/file/buckets/{bucketId}/files/{fileId}`         | —                                 | `200` + file bytes as an attachment |

File names are unique (case-insensitive) within a bucket.

### Objects (v2) — for services

The v2 API addresses objects by **bucket name** and an **object key** you choose (e.g. `invoices/2026/42.pdf`), instead of UUIDs. Bucket names are looked up among the caller's own buckets, so two users can each have a bucket called `photos`.

| Method | Path | Scope | Success |
|--------|------|-------|---------|
| PUT    | `/api/v2/buckets/{bucket}/objects/{key...}` | `WRITE` | `201` (new) or `200` (replaced) + `{ key, size, contentType, etag, lastModified }`, `ETag` header |
| GET    | `/api/v2/buckets/{bucket}/objects/{key...}` | `READ` | `200` + raw bytes with `Content-Type`, `ETag`, `Last-Modified` |
| HEAD   | `/api/v2/buckets/{bucket}/objects/{key...}` | `READ` | `200` + the same headers and `Content-Length`, no body |
| DELETE | `/api/v2/buckets/{bucket}/objects/{key...}` | `DELETE` | `204` |
| GET    | `/api/v2/buckets/{bucket}/objects?prefix=&cursor=&limit=` | `READ` | `200` + `{ bucket, prefix, objects: [...], nextCursor, truncated }` |

- **Upload** by sending the file as the raw request body (not multipart), with its `Content-Type`; this defaults to `application/octet-stream`. The `minis3.upload.max-size` limit applies, and anything larger gets a `413`. `application/x-www-form-urlencoded` bodies are rejected.
- **Replacing is safe**: the new bytes are fully written before the object points at them, and the old bytes are removed afterwards. If two clients write the same key at the same time, the last write wins.
- **`ETag`** is the MD5 of the content. `GET` handles `If-None-Match` / `If-Modified-Since` (returns `304`) and `Range: bytes=...` (returns `206`).
- **Keys**: 1–255 characters, slashes allowed, no control characters. Spring Security also rejects URLs containing `//`, `/../`, encoded slashes or `;`. Keys are case-sensitive and never used as file paths on disk.
- **Listing** returns keys in order. Pass `nextCursor` back as `cursor` to get the next page. `limit` defaults to 100 (max 1000).
- Objects uploaded through v2 also appear in the v1 file listing, and v1 files can be read through v2 under their file name, without an ETag.

```bash
# upload, read back, list, delete — works with a JWT or an API key
curl -X PUT $BASE/api/v2/buckets/photos/objects/2026/cat.png \
  -H "Authorization: Bearer $API_KEY" -H 'Content-Type: image/png' --data-binary @cat.png
curl $BASE/api/v2/buckets/photos/objects/2026/cat.png -H "Authorization: Bearer $API_KEY" -o cat.png
curl "$BASE/api/v2/buckets/photos/objects?prefix=2026/" -H "Authorization: Bearer $API_KEY"
curl -X DELETE $BASE/api/v2/buckets/photos/objects/2026/cat.png -H "Authorization: Bearer $API_KEY"
```

### Example session

```bash
BASE=http://localhost:2399/storage/minis3

curl -X POST $BASE/api/v1/users/register -H 'Content-Type: application/json' \
  -d '{"name":"alice","email":"alice@example.com","password":"supersecret"}'

TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"supersecret"}' | jq -r .token)

BUCKET=$(curl -s -X POST $BASE/api/v1/bucket/ -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"bucketName":"photos"}' | jq -r .bucketPublicId)

curl -X POST $BASE/api/v1/file/buckets/$BUCKET/files -H "Authorization: Bearer $TOKEN" \
  -F multipartFile=@./cat.png

curl $BASE/api/v1/file/buckets/$BUCKET/files -H "Authorization: Bearer $TOKEN"

# issue a read-only key for another service, limited to this bucket
API_KEY=$(curl -s -X POST $BASE/api/v1/keys -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"gallery-service\",\"scopes\":[\"READ\"],\"bucketIds\":[\"$BUCKET\"]}" | jq -r .apiKey)

# the service then calls the API with the key
curl $BASE/api/v1/file/buckets/$BUCKET/files -H "Authorization: Bearer $API_KEY"
```

### Errors

Errors are returned as `{ "message": "...", "timestamp": "..." }` by [GlobalExceptionHandler](src/main/java/com/anirudh/storage/minis3/exception/GlobalExceptionHandler.java):

| Status | When |
|--------|------|
| 400    | Malformed JSON, empty file, invalid object key or cursor, form-encoded object upload |
| 401    | Missing/invalid/expired/revoked token or API key, wrong credentials |
| 403    | Bucket you don't own, bucket outside a key's restriction, or missing scope |
| 404    | Bucket, file or object not found |
| 409    | Duplicate email, bucket name or file name |
| 413    | Upload larger than `minis3.upload.max-size` (or the multipart limit for v1) |
| 500    | Storage (disk) failure |

## Project layout

```
src/main/java/com/anirudh/storage/minis3/
├── auth/            login, JWT service + filter, API keys (auth/apikey), security config
├── user/            registration
├── bucket/          bucket CRUD + ownership check (BucketAuthService)
├── file/            file metadata, upload/download, StorageService + LocalStorageService
├── exception/       custom exceptions + global handler
├── entity/          BaseEntity (id, createdAt, updatedAt via JPA auditing)
├── configuration/   beans (BCrypt, ModelMapper, ObjectMapper), JPA auditing
└── util/            UUID + security-context helpers, password helper
```

Every entity exposes a random **public UUID** (`userPublicId`, `bucketPublicId`, `filePublicId`) to clients; internal numeric IDs are never returned.

## Roadmap

- [x] Delete a single file (v2 `DELETE`)
- [ ] Delete a bucket's files (disk + metadata) when the bucket is deleted
- [x] Enable method security so `@PreAuthorize` ownership checks are enforced
- [x] Ownership check on file download
- [x] API keys with scopes and bucket restrictions for service-to-service use
- [x] v2 object API addressed by bucket name + object key (PUT/GET/HEAD/DELETE, list by prefix)
- [ ] Pre-signed URLs
- [ ] `minis3-client` Java SDK + Spring Boot starter
- [x] Handle `FileSizeLimitExceededException`, `StorageException` and `MaxUploadSizeExceededException` with proper status codes
- [x] Tests for the v2 object API
- [ ] Tests for the v1 file endpoints
- [ ] Optimistic locking so concurrent writes to one key can't leave orphaned bytes
- [ ] `delimiter` support in listings (folder-style browsing)
- [ ] Pluggable remote storage backend (e.g. S3/MinIO) behind `StorageService`
- [ ] Docker Compose for PostgreSQL + app
