# Local LG4 Proxy (zero-budget, dev-only) — Milestone 2

- Reads `NVIDIA_API_KEY` from env: `export NVIDIA_API_KEY=...; npm start` (port 8787, bind `0.0.0.0`)
- Forwards `image_base64 + prompt` to `https://integrate.api.nvidia.com/v1/chat/completions` with `model: meta/llama-guard-4-12b`, `messages:[{role:user,content:[{type:text},{type:image_url}]}]`, `max_tokens:512, stream:false`
- Never logs API key, never returns it to Android, never saves images, never logs base64
- Validates size/MIME, returns `proxy_not_configured` if no key, 429/5xx normalized

## Physical Device Setup

- Emulator: `local.properties` `backendBaseUrl=http://10.0.2.2:8787`
- Physical phone: `backendBaseUrl=http://<laptop-LAN-IP>:8787` (e.g., `192.168.1.x:8787`), proxy `app.listen(0.0.0.0)` + `ufw allow from 192.168.1.0/24 to any port 8787`
- Firewall limited to trusted LAN, no public exposure, API key stays on laptop
- Debug `usesCleartextTraffic` only in `debug` buildType (see `app/build.gradle.kts` debug flag), release enforces HTTPS/Cleartext off

## Manual Tests (on device)

1. Consent OFF → no upload, `consent_off`
2. Consent ON → assess OK
3. Revoke before submit → `consent_off`
4. Revoke mid-pending → `consent_revoked`
5-10. Vegetable/fruit/meat/knife/fiction/ambiguous images (see evaluation)
11. Large (11MB) → `image_too_large`
12. Unsupported gif → `unsupported_type`
13. Proxy down → `network_error`
14. 429 → `rate_limited`
15. Timeout 25s → `timeout`

## Benchmark

Use `scripts/run_accuracy.py` with `benchmark/images/` (120 expected). Do not claim accuracy before run.
