# Getting access to miniS3

miniS3 is a self-hosted object store. You can reach it from anywhere at **minis3.nullcanvas.blog**, and you need an **invite code** from the owner to create an account. This guide takes you from nothing to your first upload in about 5 minutes.

| | |
|---|---|
| **Server URL** | `https://minis3.nullcanvas.blog/storage/minis3` |
| **API docs (Swagger)** | https://minis3.nullcanvas.blog/storage/minis3/swagger-ui.html |
| **Who to ask** | Anirudh, the server owner, for an invite code |

---

## Step 0 — for the server owner: send the invite code

On `ani-srv`, run:

```bash
sudo grep MINIS3_INVITE_CODE /etc/minis3/minis3.env
```

Send the value privately, along with a link to this guide. Anyone with the code can register, so if it leaks, change it in the env file and run `sudo systemctl restart minis3`. Existing accounts keep working.

## Step 1 — check you can reach the server

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://minis3.nullcanvas.blog/storage/minis3/api/v1/bucket/
```

`401` means it works: you reached the server, and it's asking you to log in. You can also open the [Swagger UI](https://minis3.nullcanvas.blog/storage/minis3/swagger-ui.html) in a browser.

---

## Step 2 — create your miniS3 account

Pick any email and a password of 8–25 characters. The name must be 4–50 characters. Use the invite code from Step 0.

```bash
BASE=https://minis3.nullcanvas.blog/storage/minis3

curl -X POST $BASE/api/v1/users/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Your Name","email":"you@example.com","password":"choose-a-password","inviteCode":"<invite code>"}'
```

You get `201` back. `403` means the invite code is wrong or missing, and `409` means that email is already registered.

## Step 3 — log in

```bash
TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"choose-a-password"}' | jq -r .token)
```

The token is valid for 1 hour; log in again to get a new one. In Swagger, click **Authorize** and paste the token. After 5 wrong passwords, that email is locked for 15 minutes (`429`).

## Step 4 — create a bucket and upload a file

Bucket names are 3–20 characters: letters, digits, `.`, `_` and `-` only. They only have to be unique among **your** buckets.

```bash
curl -X POST $BASE/api/v1/bucket/ -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"bucketName":"my-files"}'

# upload (any file; the key can contain slashes)
curl -X PUT $BASE/api/v2/buckets/my-files/objects/docs/hello.txt \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: text/plain' --data-binary 'hello miniS3'

# download
curl $BASE/api/v2/buckets/my-files/objects/docs/hello.txt -H "Authorization: Bearer $TOKEN"

# list
curl "$BASE/api/v2/buckets/my-files/objects?prefix=docs/" -H "Authorization: Bearer $TOKEN"
```

Each file can be up to 20 MB, and each account gets 5 GB in total (`507` when full).

## Step 5 — an API key for your apps and scripts

Login tokens expire after an hour. For a service or script, create an **API key** instead:

```bash
curl -X POST $BASE/api/v1/keys -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"my-app","scopes":["READ","WRITE"],"expiresInDays":90}'
```

The response contains `apiKey` (`ms3_...`). **It's shown only once, so store it somewhere safe.** Use it exactly like a token: `Authorization: Bearer ms3_...`.

- **Scopes:** `READ` (download and list), `WRITE` (upload and create buckets), `DELETE`.
- **Limit to certain buckets:** add `"bucketIds": ["<bucketPublicId>"]`.
- **Revoke:** `DELETE /api/v1/keys/{keyId}`. This needs your login token, not the key itself.

## Step 6 (optional) — share a file with a time-limited link

```bash
curl -X POST $BASE/api/v2/buckets/my-files/presign -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"key":"docs/hello.txt","method":"GET","expiresInSeconds":600}'
```

The returned `url` works without logging in, in any browser, until it expires. Anyone who has the link can download the file, so share it only with people who should see it.

---

## Using miniS3 from Java

Add the client library from GitHub Packages. You need a GitHub token with the `read:packages` scope; create one at https://github.com/settings/tokens. See the [README](../README.md#java-client-minis3-client) for the full setup.

```yaml
minis3:
  client:
    endpoint: https://minis3.nullcanvas.blog/storage/minis3
    api-key: ${MINIS3_API_KEY}
```

---

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `530` / Cloudflare error page | `ani-srv` or its tunnel is offline (powered off, or its home internet is down). Ask the owner. |
| `403` when registering | Missing or wrong invite code. |
| `429` when logging in | Too many wrong passwords for that email. Wait 15 minutes (see the `Retry-After` header). |
| `507` on upload | Your account's 5 GB is used up. Delete objects, or ask the owner to raise `MINIS3_USER_QUOTA`. |
| `401 Unauthorized` | Missing, expired or wrong token or API key. Log in again (tokens last 1 hour) or check the key wasn't revoked. |
| `403 Forbidden` | The API key lacks the scope (e.g. `READ`-only trying to upload) or isn't allowed on that bucket. |
| `404 Not Found` on a bucket you created | Bucket names are per user: you can only see your own buckets. Check the spelling. |
| `413` | The file is bigger than 20 MB. |
| Swagger "Try it out" fails for keys with `/` | Swagger encodes `/` as `%2F`, which the server rejects. Use curl or Postman for keys like `docs/a.txt`. |

## Leaving

- **Delete your miniS3 account and everything in it:**
  ```bash
  curl -X DELETE $BASE/api/v1/users/me -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' -d '{"password":"choose-a-password"}'
  ```

---

## Appendix — private access over Tailscale (owner and trusted devices)

The server is also reachable privately at `https://ani-srv.tail1678db.ts.net/storage/minis3`, without going through Cloudflare. This route only works for devices on the owner's tailnet.

1. **Owner:** at https://login.tailscale.com/admin/machines, open **ani-srv → ⋯ → Share…** and invite the person by email. Sharing gives access to this one machine only.
2. **User:** install Tailscale (`brew install --cask tailscale`, https://tailscale.com/download, or the App Store / Play Store), sign in with your own account, and accept the invite.
3. Use `https://ani-srv.tail1678db.ts.net/storage/minis3` in place of the public URL. Pre-signed links still point at the public address.

To remove access, the owner revokes the share.
