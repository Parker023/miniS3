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
| `DB_URL`          | no       | JDBC URL (default `jdbc:postgresql://localhost:5433/minis3`) |
| `MINIS3_STORAGE_LOCATION` | no | Directory for object bytes (default `./storage`; use an absolute path in production, e.g. `/var/lib/minis3/storage`) |
| `SERVER_ADDRESS`  | no       | Interface to bind, e.g. `127.0.0.1` behind `tailscale serve` |
| `MINIS3_PUBLIC_URL` | no     | Base URL put into pre-signed links, e.g. `https://laptop.tailnet.ts.net/storage/minis3`. If empty, it's built from the incoming request |

`JWT_SECRET` also signs pre-signed URLs (a separate key is derived from it), so changing it invalidates every outstanding link. The app won't start without it.

Generate a secret with:

```bash
openssl rand -base64 32
```

### Run

```bash
./gradlew :service:bootRun
```

It runs from the repository root, so `./storage` (uploaded bytes) stays at `<repo>/storage`.

The service starts on **port 2399** under the context path **`/storage/minis3`**, so every endpoint below is prefixed with:

```
http://localhost:2399/storage/minis3
```

### Test

```bash
./gradlew test
```

This runs the tests of all three modules. The service tests use H2 and a fixed test secret from [application-test.yaml](service/src/test/resources/application-test.yaml); the client is also tested end to end against the real service on a random port.

## Configuration

Key settings from [application.yaml](service/src/main/resources/application.yaml):

| Property                          | Value              | Meaning                                  |
|-----------------------------------|--------------------|------------------------------------------|
| `server.port`                     | `2399`             |                                          |
| `server.servlet.context-path`     | `/storage/minis3`  |                                          |
| `spring.servlet.multipart.max-file-size` | `20MB`      | Hard limit enforced by Spring            |
| `minis3.upload.max-size`          | `20MB`             | Application-level per-file upload limit (v1 and v2) |
| `minis3.app.storage.location`     | `${MINIS3_STORAGE_LOCATION:./storage}` | Root directory for stored file bytes; created at startup, which fails if it isn't writable |
| `minis3.presign.base-url`         | `${MINIS3_PUBLIC_URL:}` | Base URL for pre-signed links       |

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
| DELETE | `/api/v1/users/me`          | `{ "password" }`                       | `204`; deletes the account with all its buckets, files and API keys (login token only, not an API key) |

Validation: name 4–50 chars, valid email, password 8–25 chars. Deleting the account needs the password again (`401` if wrong). Login tokens already issued keep working until they expire, but they no longer own anything.

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
| DELETE | `/api/v1/bucket/{bucketId}`  | —                       | `200` + `{ bucketName }`; also deletes every file in it (metadata at once, bytes on disk after the transaction commits) |

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

### Pre-signed URLs

A pre-signed URL is a time-limited link for **one object and one method** (`GET` or `PUT`) that works **without an `Authorization` header**. A backend can give it to a browser or another client, so the file goes straight to or from miniS3 and never passes through that backend.

**Issue** (JWT or API key):

```
POST /api/v2/buckets/{bucket}/presign
{ "key": "2026/cat.png", "method": "GET", "expiresInSeconds": 600 }

200 { "url": "https://.../api/v2/presigned/{bucketId}/2026/cat.png?expires=...&signature=...", "method": "GET", "expiresAt": "..." }
```

- A `GET` link needs the `READ` scope to issue, and a `PUT` link needs `WRITE`. The caller must also be allowed to use the bucket.
- `expiresInSeconds` defaults to 15 minutes, with a maximum of 7 days.
- The object doesn't have to exist yet for a `PUT` link.

**Use** — plain HTTP, no credentials:

| Method | Path | Behaves like |
|--------|------|--------------|
| GET    | `/api/v2/presigned/{bucketId}/{key...}?expires=&signature=[&credential=]` | v2 `GET` (ETag, 304, Range/206) |
| PUT    | same | v2 `PUT` (raw body, size limit, `201`/`200`) |

**Security**
- The `signature` is an HMAC-SHA256 over the method, bucket, key, expiry and issuing key. Changing any of them, or using a `GET` link to `PUT`, gives a `403`.
- Expired links get a `403`.
- Links issued **with an API key** include that key's id as `credential`. They stop working as soon as the key is revoked or expires, or if it loses the scope or bucket access.
- Links issued **with a JWT** can't be revoked individually; they're valid until they expire. Keep them short.
- Anyone holding the link can use it until then, so treat it like a password.

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

Errors are returned as `{ "message": "...", "timestamp": "..." }` by [GlobalExceptionHandler](service/src/main/java/com/anirudh/storage/minis3/exception/GlobalExceptionHandler.java):

| Status | When |
|--------|------|
| 400    | Malformed JSON, empty file, invalid object key or cursor, form-encoded object upload |
| 401    | Missing/invalid/expired/revoked token or API key, wrong credentials |
| 403    | Bucket you don't own, bucket outside a key's restriction, missing scope, or an invalid/expired/revoked pre-signed URL |
| 404    | Bucket, file or object not found |
| 409    | Duplicate email, bucket name or file name |
| 413    | Upload larger than `minis3.upload.max-size` (or the multipart limit for v1) |
| 500    | Storage (disk) failure |

## Java client (`minis3-client`)

A small SDK other Java services use instead of writing HTTP calls by hand. It covers the v2 object API and pre-signed URLs, and authenticates with an **API key**. It depends only on the JDK `HttpClient` and Jackson 3, and doesn't need Spring.

### Install

Releases are published to **GitHub Packages** by the [Publish client](.github/workflows/publish-client.yml) workflow whenever a `v*` tag is pushed (or when it's run by hand with a version):

```bash
git tag v0.1.0 && git push origin v0.1.0
```

GitHub Packages needs a token even for public packages. Create a classic personal access token with the `read:packages` scope and put it in `~/.gradle/gradle.properties`:

```properties
gpr.user=<your GitHub username>
gpr.key=<token with read:packages>
```

Then in the consuming project (Gradle):

```groovy
repositories {
    mavenCentral()
    maven {
        url = uri('https://maven.pkg.github.com/Parker023/miniS3')
        credentials {
            username = findProperty('gpr.user')
            password = findProperty('gpr.key')
        }
    }
}

dependencies {
    // Spring Boot 4 apps: auto-configured client bean
    implementation 'com.anirudh.storage:minis3-spring-boot-starter:0.1.0'
    // or plain Java
    implementation 'com.anirudh.storage:minis3-client:0.1.0'
}
```

For local development without publishing, use `./gradlew publishToMavenLocal`, then `mavenLocal()` and version `0.0.1-SNAPSHOT`.

### Spring Boot

```yaml
minis3:
  client:
    endpoint: https://<laptop>.<tailnet>.ts.net/storage/minis3
    api-key: ${MINIS3_API_KEY}
    # connect-timeout: 10s   (default)
    # request-timeout: 60s   (default: none, so large uploads aren't cut off)
```

```java
@Service
@RequiredArgsConstructor
class InvoiceStorage {
    private final MiniS3Client storage;   // auto-configured

    void save(String id, Path pdf) {
        storage.putObject("invoices", "2026/" + id + ".pdf", pdf, "application/pdf");
    }

    URI shareLink(String id) {
        return storage.presignGet("invoices", "2026/" + id + ".pdf", Duration.ofMinutes(10)).url();
    }
}
```

The bean is only created when `minis3.client.endpoint` is set. Startup fails if `api-key` is missing, and the starter backs off if you define your own `MiniS3Client` bean.

### Plain Java

```java
try (MiniS3Client storage = MiniS3Client.builder()
        .endpoint("http://localhost:2399/storage/minis3")
        .apiKey(System.getenv("MINIS3_API_KEY"))
        .build()) {
    storage.putObject("photos", "2026/cat.png", Path.of("cat.png"), "image/png");
}
```

### API

| Method | Does |
|--------|------|
| `putObject(bucket, key, InputStream \| byte[] \| Path, contentType)` | create or replace; streams, never buffers the whole file. Returns `ObjectInfo` |
| `getObject(bucket, key)` | `MiniS3Object` with `info()` and a streaming `content()`; close it (try-with-resources) |
| `getObjectBytes(bucket, key)` | whole object as `byte[]` |
| `headObject(bucket, key)` / `objectExists(...)` | `Optional<ObjectInfo>`, empty if missing |
| `deleteObject(bucket, key)` | delete; `MiniS3NotFoundException` if missing |
| `listObjects(bucket, prefix, cursor, limit)` | one `ObjectPage` |
| `listAllObjects(bucket, prefix)` | `Stream<ObjectInfo>` that fetches pages lazily |
| `presignGet` / `presignPut(bucket, key, Duration)` | `PresignedUrl` (url, method, expiresAt) |

`ObjectInfo` holds `key`, `size`, `contentType`, `etag` (hex MD5, no quotes) and `lastModified` (an `Instant`).

**Errors** are all `MiniS3Exception` with `statusCode()`:
- `MiniS3NotFoundException`: 404
- `MiniS3AccessDeniedException`: 401 (bad, expired or revoked key) or 403 (missing scope or bucket)
- anything else, e.g. 413 for an upload that's too large
- `statusCode() == 0` means there was no response (connection refused, timeout or interrupted)

The client is thread-safe; create one and share it. Keys are percent-encoded per segment, so spaces, `+` and non-ASCII characters work. The server's firewall still rejects keys containing `%`, `;`, `//` or `..` segments.

## Deploying to Ubuntu

[deploy/ubuntu](deploy/ubuntu) installs miniS3 as a systemd service on Ubuntu 22.04+:
- Java 21 and PostgreSQL from apt
- a `minis3` database and user with a generated password
- the system user `minis3`
- `/var/lib/minis3/storage` for objects
- `/etc/minis3/minis3.env` for secrets, readable only by root and `minis3`

The service listens on `127.0.0.1:2399` only.

**One-time**: SSH key access from your Mac:

```bash
ssh-keygen -t ed25519
```
```bash
ssh-copy-id anirudh@192.168.0.5
```

**Deploy or update** (builds the jar, copies it over, runs `setup.sh` with sudo, waits until the service answers):

```bash
deploy/ubuntu/deploy.sh anirudh@192.168.0.5
```

The first run generates the DB password and JWT secret and prints the PostgreSQL credentials; later runs keep them, replace the jar, and don't print the password again. Setup also tells systemd-logind to ignore the laptop lid (`/etc/systemd/logind.conf.d/minis3-lid.conf`), so closing it doesn't suspend the server. To read them again on the server:

```bash
sudo cat /etc/minis3/minis3.env
```

**Expose on your tailnet** (HTTPS, nothing opened to the LAN or internet), then set `MINIS3_PUBLIC_URL` in the env file and restart:

```bash
sudo tailscale serve --bg 2399
```

Useful commands on the server: `systemctl status minis3`, `journalctl -u minis3 -f`, `sudo systemctl restart minis3`.

## Project layout

```
service/                       the Spring Boot storage service
deploy/ubuntu/                 systemd unit + setup/deploy scripts
minis3-client/                 plain-Java SDK (MiniS3Client)
minis3-spring-boot-starter/    auto-configuration for minis3.client.*

service/src/main/java/com/anirudh/storage/minis3/
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
- [x] Delete a bucket's files (disk + metadata) when the bucket is deleted
- [x] Delete-account endpoint
- [x] Enable method security so `@PreAuthorize` ownership checks are enforced
- [x] Ownership check on file download
- [x] API keys with scopes and bucket restrictions for service-to-service use
- [x] v2 object API addressed by bucket name + object key (PUT/GET/HEAD/DELETE, list by prefix)
- [x] Pre-signed URLs (GET/PUT, revocable through the issuing API key)
- [x] `minis3-client` Java SDK + Spring Boot starter
- [x] Handle `FileSizeLimitExceededException`, `StorageException` and `MaxUploadSizeExceededException` with proper status codes
- [x] Tests for the v2 object API
- [ ] Tests for the v1 file endpoints
- [ ] Optimistic locking so concurrent writes to one key can't leave orphaned bytes
- [ ] `delimiter` support in listings (folder-style browsing)
- [ ] Pluggable remote storage backend (e.g. S3/MinIO) behind `StorageService`
- [ ] Docker Compose for PostgreSQL + app
- [x] Publish the client to GitHub Packages (workflow on `v*` tags)
- [ ] Backups: nightly `pg_dump` + rsync of the storage directory
