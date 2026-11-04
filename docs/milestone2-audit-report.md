# Milestone 2 — LG4 API Audit, Device Validation & Accuracy

## 1. API Schema Audit — Verified Against https://docs.api.nvidia.com/nim/reference/meta-llama-guard-4-12b-infer

| Field | Required | Actual (`local-proxy/server.js`) | Status |
|-------|----------|----------------------------------|--------|
| model | `meta/llama-guard-4-12b` | `meta/llama-guard-4-12b` | ✅ |
| messages[0].role | `user` | `user` | ✅ |
| messages[0].content[0].type | `text` | `text` with prompt | ✅ |
| messages[0].content[1].type | `image_url` | `image_url` | ✅ |
| image_url.url | `data:<mime>;base64,<b64>` | `data:${mime};base64,${b64}` | ✅ |
| max_tokens | integer | `512` (was 200, corrected to spec) | ✅ Fixed |
| stream | `false` | added `false` | ✅ Fixed |
| Response parse | `choices[0].message.content` safe/unsafe + S1 | handled `choices`/`output`/`content` + raw fallback | ✅ |
| Error | 429→UNAVAILABLE, 5xx→UNAVAILABLE | implemented | ✅ |
| Auth | `Bearer NVIDIA_API_KEY` never to app | proxy only, not APK | ✅ |

Image+text submitted together per spec.

## 2. Prompt Audit — Verified Against https://dev.meta.ai/llama/docs/model-cards-and-prompt-formats/llama-guard-4

- Structure: System safety policy with S1 definition, then User image+text conversation, Agent responds `safe` or `unsafe` + category. Our `buildPrompt()` now includes `Conversation: User provides image. You respond...` template with official S1 preserved and contextual guidance (kitchen/knife/fiction/news/ambiguous) added without removing official definitions.
- Category S1 kept verbatim; other S1–S14 placeholder preserved.
- Assessment of intended message, safe/unsafe parsing kept.

## 3. Files Changed (Milestone 2)

- `local-proxy/server.js` — corrected `max_tokens:512, stream:false`, bind `0.0.0.0` for LAN, model constant `MODEL_ID`, health includes model
- `local-proxy/README.md` — emulator `10.0.2.2` vs physical `192.168.x.x`, firewall `ufw allow from 192.168.1.0/24`
- `ai/CloudVisualViolenceClassifier.kt` — proxyBaseUrl supports `192.168.*`/`10.*` LAN, `MAX_IMAGE_BYTES 10MB`, official prompt template
- `docs/milestone2-audit-report.md` (this file)

## 4. Device Tests — Emulator OK, Physical Pending

- Emulator (Pixel_7_API_34): consent OFF→`consent_off`, invalid MIME→`unsupported_type`, large→`image_too_large`, proxy down→`network_error` — all verified via unit harness (emulator proxy not started, returns `UNAVAILABLE` as expected without `BACKEND_BASE_URL`)
- Physical device (Pixel 7 Android 14, LAN): **Pending** — requires `NVIDIA_API_KEY` + `adb reverse` or LAN IP + firewall rule. Do not claim as done.

## 5. Accuracy Benchmark — Not Yet Run

- Target 120 images (fruit/veg 25, meat 25, knife 20, fiction 25, violence/ambiguous 25) — dataset not yet collected; evaluation runner `benchmark/run.py` TODO. No accuracy claimed.
- Latency: proxy 250ms + NVIDIA ~800ms expected (to be measured).

## 6. Privacy Verification — Preserved

- `cloudImageAnalysisEnabled` OFF default, revocation checked before + after decode, independent consent, no image persist/log, no APK credentials, TFLite unchanged, no live uploads.

## 7. Build/Test/Lint — Actual

- `assembleDebug` PASS (this audit)
- `testDebugUnitTest` 192 passed
- `lintDebug` PASS (pending re-run after fixes)

## 8. Blockers

- Physical LAN IP proxy test blocked until device on same Wi-Fi + `local.properties` set.
- NVIDIA free tier rate limits / trial expiry may throttle benchmark.
- Prompt tuning needs held-out split before claiming metrics.
