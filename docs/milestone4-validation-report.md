# Milestone 4 — Physical Device Validation & Accuracy Report

## Preflight Audit

- `app/src/main/res/xml/network_security_config.xml`: base `cleartextTrafficPermitted=false`; domain-config allow `10.0.2.2/localhost/127.0.0.1` only. Debug LAN `192.168.*` is NOT in release allowlist; debug physical LAN HTTP works only when `local.properties backendBaseUrl=http://192.168.x.x:8787` is set and device on same LAN — debug-only, release remains restrictive (no `usesCleartextTraffic` true).
- `local-proxy/server.js` listens `0.0.0.0:8787` only for LAN necessity; routes `/v1/lg4/visual` + `/health` only, not `/*`; firewall doc restricts to phone IP `/32` (not `0.0.0.0/0`), no router forwarding/tunnel recommended, stop via Ctrl+C.
- API keys: `NVIDIA_API_KEY` env-only, never to app, `local-proxy/.env` gitignored, no `BuildConfig` embedding, `checkReleaseNoApiKey` guard.
- Consent double-check: `CloudVisualViolenceClassifier.assess()` checks `isConsentEnabled()` before proxy URL and again after stripped image before base64 upload; revocation blocks.

## Physical Device Tests — Instructions

**Setup:** `ip route get 1.1.1.1 | awk '{print $7}'` → LAN IP; `sudo ufw allow from <PHONE_IP>/32 to any port 8787`; `export NVIDIA_API_KEY=...; cd local-proxy && npm i && npm start`; `echo "backendBaseUrl=http://<LAN_IP>:8787" >> local.properties`; launch app on phone same Wi-Fi, Settings Privacy → Cloud Image Analysis ON (consent ON).
**Teardown:** `Ctrl+C` proxy + `sudo ufw delete allow from <PHONE_IP> to any port 8787` + set consent OFF.

| # | Test | Expected | Result |
|---|------|----------|--------|
| 1 | Consent OFF pick image → Check visual violence | `consent_off` no upload | **PENDING** — requires phone + proxy |
| 2 | Consent ON → Check | `SAFE_ASSESSED`/`REVIEW` with model `meta/llama-guard-4-12b` | **PENDING** |
| 3 | Revoked before submit (toggle OFF then Check) | `consent_off` | **PENDING** |
| 4 | Revoked mid-pending (toggle OFF during spinner) | `consent_revoked` or prior completes, second blocked | **PENDING** |
| 5 | Fruit/veg prep (apple chopping) | SAFE_ASSESSED (not S1) | **PENDING** |
| 6 | Raw meat (chicken on board) | SAFE_ASSESSED | **PENDING** |
| 7 | Harmless knife (person holding knife, no threat) | SAFE_ASSESSED | **PENDING** |
| 8 | Ambiguous knife/person occluded | REVIEW | **PENDING** |
| 9 | Apparent violent (staged assault) | REVIEW with S1 preserved | **PENDING** |
| 10 | Unsupported MIME (gif) | `unsupported_type` | **PASS** (unit: validateImage) |
| 11 | Oversized (11MB) | `image_too_large` | **PASS** (unit) |
| 12 | Proxy unavailable (stop proxy) | `proxy_not_configured`/`network_error` | **PENDING** |
| 13 | Internet unavailable (airplane) | `network_error` | **PENDING** |
| 14 | Rate limit (429) | `rate_limited` UNAVAILABLE | **PENDING** |
| 15 | Timeout (25s) | `timeout` UNAVAILABLE | **PENDING** |

**No physical tests claimed — all PENDING until user executes on phone. No HIGH_RISK escalation; TFLite unchanged.**

## Benchmark Dataset Status

- `benchmark/README.md`: 120 images planned (fruit/veg 25, meat 25, knife 20, fictional 25, violence/ambiguous 25), 60 prompt-dev / 60 held-out splits documented, stored outside repo, `labels.jsonl`/`predictions.jsonl` separate, no private images to trial.
- `benchmark/run.py`: computes confusion/precision/recall/F1/FPR/FNR/latency from `labels.jsonl` + `predictions.jsonl`; fails with “Missing dataset” if files absent — no fabricated results.
- **Status:** Scaffold only — no images collected, no predictions, no metrics claimed (correct per restriction).

## Regression Results (actual)

- `assembleDebug` **PASS**
- `testDebugUnitTest` **192 passed, 0 failed** (CloudVisualViolenceClassifier 12 incl. consent/parse/validation, VisualContentClassifier 11, ContextAware 11, etc.)
- `lintDebug` **PASS**

## Privacy/Security Observations

- Text/image consents independent OFF, never auto-enabled, revocation respected, no alternate upload path (proxy allowlist `localhost/127.0.0.1/192.168.*/10.*` only), no image/base64/API-key logging or persistence, `network_security_config.xml` debug-only HTTP.

## Known Limitations

- NVIDIA trial may rate-limit/record; physical tests pending user LAN setup; LG3 INT4 heuristic fallback remains; accuracy not claimed.

## Live Monitoring Confirmation

**Live screen monitoring remains DISABLED** — `ScreenMonitoringService` not connected to `CloudVisualViolenceClassifier`, no background uploads, no automatic HIGH_RISK from single S1 response.
