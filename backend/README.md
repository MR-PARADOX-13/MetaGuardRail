# MetaGuardRail Backend

Express proxy that fronts Baseten inference for the MetaGuardRail Android app. The Android client never holds the Baseten API key.

## Overview

```
Android app  --HTTPS-->  Backend (this service)  --HTTPS + Bearer token-->  Baseten inference
     |                            |
     |  X-App-Check /             |  validates auth stub
     |  Authorization header      |  rate-limits (30 req / min)
     |                            |  enforces 4000-char budget
     |                            |  forwards to /v1/chat/completions
     |                            |  never echoes BASETEN_API_KEY
```

The backend exposes `POST /v1/moderate` and `POST /v1/chat/completions` (shared handler) which validate the request, forward it to `https://inference.baseten.co/v1/chat/completions`, and return `choices[0].message.content`. A `GET /v1/models` proxy and a `GET /health` liveness endpoint are also provided.

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `BASETEN_API_KEY` | Yes | Baseten API key. Set only in hosting env vars / secrets manager — never commit to Git. |
| `PORT` | No | Listen port. Defaults to `3000`. |
| `BASETEN_URL` | No | Override Baseten base URL if needed (default `https://inference.baseten.co/v1/chat/completions`). |
| `ALLOWED_ORIGIN` | No | Restrict CORS to a single origin (default `*`). |

See `.env.example` for a template. Copy it to `.env` for local development.

## Local Run

```bash
cd backend
npm install
cp .env.example .env
# Edit .env and set BASETEN_API_KEY
npm start
# Health check:
curl http://localhost:3000/health
```

Run tests (if any):

```bash
npm test
```

## Deploy

### Render

1. Push this repo to GitHub.
2. In Render dashboard: **New +** → **Web Service** → connect repo → set **Root Directory** to `backend`.
3. Environment: `Node`, Build command `npm install`, Start command `npm start`.
4. Add env var `BASETEN_API_KEY` under **Environment** (secret).
5. Deploy. Render assigns an `https://…onrender.com` URL.

### Fly.io

```bash
cd backend
fly launch --no-deploy   # picks up Dockerfile
fly secrets set BASETEN_API_KEY=sk-...
fly deploy
```

`fly.toml` is optional — Fly can infer config from the Dockerfile (Node 20, port 3000).

### Docker

```bash
cd backend
docker build -t metaguardrail-backend .
docker run -p 3000:3000 -e BASETEN_API_KEY=sk-... metaguardrail-backend
```

The included `Dockerfile` uses `node:20-alpine`, runs `npm ci` (or `npm install` fallback), copies `server.js`, exposes `3000`, and starts with `node server.js`.

## Android Config

Set the backend URL so the app knows where to send moderation requests:

1. Open `local.properties` in the project root (create it if it does not exist — it is `.gitignore`'d).
2. Add or update:

   ```properties
   backend.baseUrl=https://your-backend.example.com
   ```

   For local testing against an emulator, use `http://10.0.2.2:3000` (emulator loopback to host). For a physical device on the same Wi-Fi, use your machine's LAN IP, e.g. `http://192.168.1.10:3000`.

3. Rebuild / re-run the app so `BuildConfig` picks up the value. No API key is ever stored in `local.properties` or the APK.

## Security Notes

- **Key never in APK:** `BASETEN_API_KEY` lives only on the server (env var / secrets manager). The APK contains only the backend URL.
- **No screenshot storage:** Screenshots taken for optional server-side moderation are forwarded transiently and never written to disk on the server or persisted on the device beyond the in-memory frame.
- **Rate limits:** `express-rate-limit` is set to 30 requests / 60 s per IP (`standardHeaders: true`). Upstream `429` from Baseten is forwarded with a safe message.
- **Auth stub:** Requests must include `X-App-Check` or a sufficiently long `Authorization` header. Replace this stub with real Firebase App Check verification before production.
- **Payload budget:** Combined `messages[].content` length is capped at 4000 characters (`413` if exceeded). JSON body limit is `2mb`.
- **Safe errors:** Missing `BASETEN_API_KEY` returns `500 { error: "Server misconfiguration" }`. Upstream bodies are not echoed verbatim. Logs include only HTTP status, never the API key.
- **Timeout:** Upstream fetch uses `AbortController` with a 30 s timeout (`504` on expiry).
