# Getting access to miniS3

miniS3 runs on a private server (`ani-srv`) that isn't on the public internet. You reach it through **Tailscale**, a private network that works from any Wi-Fi or mobile connection. This guide takes you from nothing to your first upload in about 10 minutes.

| | |
|---|---|
| **Server URL** | `https://ani-srv.tail1678db.ts.net/storage/minis3` |
| **API docs (Swagger)** | https://ani-srv.tail1678db.ts.net/storage/minis3/swagger-ui.html |
| **Who to ask** | Anirudh, the server owner |

---

## Step 0 — for the server owner: share `ani-srv`

Do this once per new person. Sharing gives them access to **this one machine only**, not to the rest of your tailnet.

1. Open the Tailscale admin console: https://login.tailscale.com/admin/machines
2. Find **ani-srv**, then click **⋯ → Share…**
3. Choose **Invite via email** and enter their email, or **Copy share link** and send it to them.
4. Once they accept, they appear under **Users → Shared with**. To remove access later, revoke the share in the same place.

---

## Step 1 — install Tailscale

Install it on every device you'll use to reach miniS3.

| Platform | Install |
|---|---|
| **macOS** | `brew install --cask tailscale`, or download from https://tailscale.com/download/mac. Open the app and allow the VPN configuration when macOS asks. |
| **Windows** | https://tailscale.com/download/windows |
| **Linux** | `curl -fsSL https://tailscale.com/install.sh \| sh`, then `sudo tailscale up` |
| **iPhone / Android** | "Tailscale" in the App Store / Play Store |

Sign in with **your own** Google, Microsoft, GitHub or email account. You don't need the owner's account.

## Step 2 — accept the share

Open the invite email or share link from Step 0 and click **Accept**. `ani-srv` now shows up in your Tailscale machine list.

## Step 3 — check you can reach the server

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://ani-srv.tail1678db.ts.net/storage/minis3/api/v1/bucket/
```

`401` means it works: you reached the server, and it's asking you to log in. You can also just open the [Swagger UI](https://ani-srv.tail1678db.ts.net/storage/minis3/swagger-ui.html) in a browser.

Always use the full name `ani-srv.tail1678db.ts.net`. The short name `ani-srv` may not resolve on shared machines.

---

## Step 4 — create your miniS3 account

Pick any email and a password of 8–25 characters. The name must be 4–50 characters.

```bash
BASE=https://ani-srv.tail1678db.ts.net/storage/minis3

curl -X POST $BASE/api/v1/users/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Your Name","email":"you@example.com","password":"choose-a-password"}'
```

You get `201` back. `409` means that email is already registered.

## Step 5 — log in

```bash
TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"choose-a-password"}' | jq -r .token)
```

The token is valid for 1 hour; log in again to get a new one. In Swagger, click **Authorize** and paste the token.

## Step 6 — create a bucket and upload a file

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

Uploads are limited to 20 MB per file.

## Step 7 — an API key for your apps and scripts

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

## Step 8 (optional) — share a file with a time-limited link

```bash
curl -X POST $BASE/api/v2/buckets/my-files/presign -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"key":"docs/hello.txt","method":"GET","expiresInSeconds":600}'
```

The returned `url` works without logging in until it expires. **It only opens on devices connected to Tailscale with access to `ani-srv`.**

---

## Using miniS3 from Java

Add the client library from GitHub Packages. You need a GitHub token with the `read:packages` scope; create one at https://github.com/settings/tokens. See the [README](../README.md#java-client-minis3-client) for the full setup.

```yaml
minis3:
  client:
    endpoint: https://ani-srv.tail1678db.ts.net/storage/minis3
    api-key: ${MINIS3_API_KEY}
```

The machine running your app also needs Tailscale with access to `ani-srv` (Steps 1–2).

---

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `curl: (6) Could not resolve host` | Tailscale isn't running or isn't signed in, or the share wasn't accepted. Check the Tailscale icon says *Connected* and `ani-srv` is in your machine list. |
| Times out / `HTTP 000` | `ani-srv` is offline (powered off, or its home internet is down). Ask the owner. |
| `401 Unauthorized` | Missing, expired or wrong token or API key. Log in again (tokens last 1 hour) or check the key wasn't revoked. |
| `403 Forbidden` | The API key lacks the scope (e.g. `READ`-only trying to upload) or isn't allowed on that bucket. |
| `404 Not Found` on a bucket you created | Bucket names are per user: you can only see your own buckets. Check the spelling. |
| `413` | The file is bigger than 20 MB. |
| Swagger "Try it out" fails for keys with `/` | Swagger encodes `/` as `%2F`, which the server rejects. Use curl or Postman for keys like `docs/a.txt`. |
| Slow on office/hotel Wi-Fi | Tailscale is relaying traffic because the network blocks direct connections. It still works, just slower. |

## Leaving

- **Delete your miniS3 account and everything in it:**
  ```bash
  curl -X DELETE $BASE/api/v1/users/me -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' -d '{"password":"choose-a-password"}'
  ```
- **Remove Tailscale access:** the owner revokes the share (Step 0), or you remove `ani-srv` from your machine list.
