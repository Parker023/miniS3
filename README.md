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

All endpoints except **register** and **login** require `Authorization: Bearer <token>`.
Unauthenticated requests get `401 {"message":"Unauthorized", ...}`.

### Users & auth

| Method | Path                        | Body                                   | Success |
|--------|-----------------------------|----------------------------------------|---------|
| POST   | `/api/v1/users/register`    | `{ "name", "email", "password" }`      | `201` + `{ name, email, userId }` |
| POST   | `/api/v1/auth/login`        | `{ "email", "password" }`              | `200` + `{ name, message, token }` (token also in `Authorization` header) |

Validation: name 4–50 chars, valid email, password 8–25 chars.

### Buckets

| Method | Path                         | Body                    | Success |
|--------|------------------------------|-------------------------|---------|
| POST   | `/api/v1/bucket/`            | `{ "bucketName" }` (3–20 chars) | `201` + `{ message, bucketName, bucketPublicId }` |
| GET    | `/api/v1/bucket/`            | —                       | `200` + page of `{ bucketName, bucketId, createdAt }` (your buckets only) |
| DELETE | `/api/v1/bucket/{bucketId}`  | —                       | `200` + `{ bucketName }` |

Bucket names are unique per owner. Listing supports `?page=&size=&sort=` (default 20 per page, newest first).

### Files

| Method | Path                                                     | Body                              | Success |
|--------|----------------------------------------------------------|-----------------------------------|---------|
| POST   | `/api/v1/file/buckets/{bucketId}/files`                  | multipart, field `multipartFile`  | `201` + `{ fileName, size, contentType, filePublicId }` |
| GET    | `/api/v1/file/buckets/{bucketId}/files`                  | —                                 | `200` + page of file details |
| GET    | `/api/v1/file/buckets/{bucketId}/files/{fileId}`         | —                                 | `200` + file bytes as an attachment |

File names are unique (case-insensitive) within a bucket.

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
```

### Errors

Errors are returned as `{ "message": "...", "timestamp": "..." }` by [GlobalExceptionHandler](src/main/java/com/anirudh/storage/minis3/exception/GlobalExceptionHandler.java):

| Status | When |
|--------|------|
| 400    | Malformed JSON, empty file |
| 401    | Missing/invalid token, wrong credentials |
| 403    | Accessing a bucket you don't own |
| 404    | Bucket or file not found |
| 409    | Duplicate email, bucket name or file name |

## Project layout

```
src/main/java/com/anirudh/storage/minis3/
├── auth/            login, JWT service, JWT filter, security config
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

- [ ] Delete a single file
- [ ] Delete a bucket's files (disk + metadata) when the bucket is deleted
- [ ] Enable method security so `@PreAuthorize` ownership checks are enforced
- [ ] Ownership check on file download
- [ ] Handle `FileSizeLimitExceededException`, `StorageException` and `MaxUploadSizeExceededException` with proper status codes
- [ ] Tests for the file module
- [ ] Pluggable remote storage backend (e.g. S3/MinIO) behind `StorageService`
- [ ] Docker Compose for PostgreSQL + app
