# Milestone 3 — Physical Device Validation Report

## Files Inspected and Changed

- Inspected: `local-proxy/server.js`, `local-proxy/README.md`, `ai/CloudVisualViolenceClassifier.kt`, `data/UserPreferencesRepository.kt`, `monitor/VisualContentClassifier.kt`, `monitor/ScreenMonitoringService.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`, `network_security_config`, `benchmark/*`
- Changed: `app/src/main/res/xml/network_security_config.xml` (new: base cleartext false, debug+emulator 10.0.2.2 allow), `app/src/main/AndroidManifest.xml` (networkSecurityConfig), `benchmark/README.md`+`run.py` scaffold, `docs/milestone3-physical-validation-report.md`

## Security Findings

- **Binding 0.0.0.0:** Required for LAN (phone cannot reach 127.0.0.1). Mitigated: no public routes, API key env-only, validate MIME/size, never log base64/image.
- **Release cleartext:** Fixed — `network_security_config.xml` denies cleartext by default; debug+emulator allowlisted only. Release cannot silently use HTTP.
- **Proxy routes:** Only `/v1/lg4/visual` + `/health`; no extra exposure.
- **Client allowlist:** `CloudVisualViolenceClassifier.proxyBaseUrl()` allows only localhost/10.* /192.168.* private ranges; rejects production hosts.
- **Keys:** `NVIDIA_API_KEY` in env, `local-proxy/.env` gitignored, `DEV_BASETEN_API_KEY` checkReleaseNoApiKey guard.

## LAN Security Guidance

- Find LAN IP: `ip route get 1.1.1.1 | awk '{print $7}'` or `hostname -I`
- Restrict: `sudo ufw allow from <PHONE_IP>/32 to any port 8787` (prefer single IP over /24)
- No router port forwarding / tunnels.
- Stop: `Ctrl+C` proxy or `pm2 stop proxy` + `sudo ufw delete allow 8787`

## Physical-Device Tests

- Automated (192 unit): consent OFF→VALIDATION_FAILED, invalid MIME→unsupported_type, large→image_too_large, malformed/empty→REVIEW, safe/unsafe S1 parsing, no HIGH_RISK from single response — all PASS.
- Physical (14 checklist): `PENDING` — requires device on same LAN + `local.properties backendBaseUrl=http://<LAN_IP>:8787` + `NVIDIA_API_KEY` + proxy running. No fabrication; marked pending per stop condition.

**Results:** No physical device executed in this CI environment; manual tests pending user LAN setup.

1 Consent OFF (unit) — PASS
2 Consent ON — PENDING (needs proxy)
3 Revoked before — PENDING
4 Revoked mid-flight — PENDING
5 Fruit/veg — PENDING
6 Raw meat — PENDING
7 Harmless knife — PENDING
8 Ambiguous — PENDING
9 Unsupported MIME — PASS (unit)
10 Oversized — PASS (unit)
11 Proxy unavailable — PASS (unit: UNAVAILABLE http_*)
12 Internet unavailable — PASS (network_error)
13 Timeout — PASS (timeout branch)
14 Rate limit — PASS (rate_limited)

## Benchmark Preparation

- Plan: 120 images (fruit/veg 25, meat 25, knife 20, fiction 25, violence/ambiguous 25), 60 prompt-dev / 60 held-out split documented in `benchmark/README.md` + runner placeholder `benchmark/run.py`. Confusion/precision/recall/latency computed post-collection. **No accuracy claimed.**

## Build/Test/Lint — Actual

- `assembleDebug` PASS
- `testDebugUnitTest` 192 passed, 0 failed
- `lintDebug` PASS

## Known Limitations

- NVIDIA trial rate limit / data retention disclosure (may log requests).
- Heuristic fallback still active for text LG3 INT4 (no .pte).
- Physical LAN test pending.

## Live Monitoring Confirmation

**Live screen monitoring remains DISABLED.** LG4 image analysis is manual `Analyze > Image > Check visual violence (cloud)` only, gated by `cloudImageAnalysisEnabled` OFF default, checked before every upload, revocation handled, no `ScreenMonitoringService` connection, no automatic HIGH_RISK.
