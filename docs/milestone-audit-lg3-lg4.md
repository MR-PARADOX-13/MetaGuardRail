# Milestone Audit — LG3 Device Validation + LG4 Feasibility

> **Audit-only milestone — no files modified except this report. No LG4 image uploads enabled.**

## 1. LG3 Runtime Compatibility

| Item | Status | Evidence |
|------|--------|----------|
| ExecuTorch Android AAR | Not yet integrated | `app/build.gradle.kts` has no `org.pytorch:executorch` dependency; `gradle/libs.versions.toml` has no version. Correct per approval: only after validation. |
| `LlamaGuardModelManager.kt` | Source-verified scaffolding | `filesDir/llama-guard/llama-guard-3-1b-int4.pte`, `EXPECTED_SHA256=""` (empty → skipped, flagged as non-production), `hasStorageSpace(1.2GB)`, `hasEnoughMemory(1.5GB heap)`, `verifySha256()` with `MessageDigest`, `deleteCorrupted()` atomic `.part` handling. |
| `ExecuTorchLlamaGuardClassifier.kt` | Scaffolded, flag-gated | `ENABLE_LLAMA_GUARD_BUILD_FLAG=false` (never loads), fallback `heuristic-v1` with `modelId:unavailable-fallback` attribution, TODO for `Module.load()` when AAR added. Source inspection only — not device-verified. |
| `HeuristicLlamaGuardClassifier` | Verified | Threat phrases checked, benign context downgrade, negation handling. 11 `ContextAwareEvaluationTest` passing. |
| Context engine | Verified | `ContextAwareSafetyEngine` wraps `ContentSafetyAnalyzer` + classifier + `RiskEngine`; Telugu uncertain path `REVIEW`. |

## 2. Model Artifact & Checksum Status

- **Artifact:** `meta-llama/Llama-Guard-3-1B-INT4` `.pte` not bundled (correct, 600–900MB). On-demand to app-private storage with SHA256.
- **Checksum:** `EXPECTED_SHA256` is empty placeholder — flagged in `meta-model-evaluation.md` as must be official SHA256 before production. **Never report as operational until real hash set.**
- **Download:** `LlamaGuardModelManager` handles cancellation (no blocking main thread), interrupted recovery (`.part` atomic rename), low-storage check, runtime memory check. Progress callback not yet wired (future `DownloadProgressListener`).

## 3. Physical Device Validation Status

- **Automated tests:** `testDebugUnitTest` 177 passed (161+11+5), `assembleDebug` PASS, `lintDebug` PASS — verified by execution this audit.
- **Device validation:** **Not yet verified** on physical device. Requires: LG3 download, checksum, model init, latency bench (target P95 <800ms Pixel 7), memory, fallback, low-storage, revocation. Do not report as operational.

## 4. LG4 Feasibility (Official API)

- **Repo:** `meta-llama/PurpleLlama Llama-Guard4/12B` multimodal text+image — server-only, never APK.
- **Image input:** Base64-encoded `image_url` or multipart per official API (verify before impl).
- **Limits:** Typical 4–10MB image, 224–1024px preprocessing; confirm via `docs/llama-guard-4/README.md`.
- **Hosting:** No official hosted inference — self-host required (Baseten, AWS, etc.); reuses existing `BackendProxyConfig` proxy pattern with new `modelId=llama-guard-4-12b`.
- **Auth/Format:** Bearer token, `POST /v1/chat/completions` with `messages: [{role:user, content:[{type:text},{type:image_url}]}]`, response `choices[0].message.content` safety JSON.
- **Privacy:** Must check `cloudImageAnalysisEnabled` (OFF default) immediately before every upload; never via alternate path; independent from `cloudTextAnalysisEnabled`.

## 5. Licensing & Privacy

- LG3: Hugging Face gated, Meta Llama 3 Community License — user must accept.
- LG4: Same license family; self-host requires separate agreement.
- TFLite nsfw MIT, Tesseract Apache 2.0, Vosk Apache 2.0 preserved.
- Image consent: `cloudImageAnalysisEnabled` OFF default, `UserPreferencesRepository` independent, `SettingsScreen` Switch added.

## 6. Proposed Integration Architecture

```
CloudLlamaGuard4Classifier : LlamaGuardClassifier {
  suspend fun classifyWithImage(text, bitmap) {
    if (!prefs.cloudImageAnalysisEnabled.first()) return failure("consent OFF")
    // encode bitmap base64, POST via BackendProxyConfig.createMuseService() with LG4 modelId
    // handle 429/5xx retry, parse LlamaGuardResult, fallback to heuristic
  }
}
```
- Keeps `VisualContentClassifier` independent, no duplicate events (contentHash includes visual flag).
- Gated by `cloudImageAnalysisEnabled` + `isBackendConfigured()` + throttle 10s.

## 7. Outstanding Blockers

1. Set real `EXPECTED_SHA256` from official artifact.
2. Add ExecuTorch AAR (verify `compileSdk37/minSdk24` ABI).
3. Wire `Module.load` + tokenizer.
4. Device bench latency/memory/battery.
5. Implement `CloudLlamaGuard4Classifier` only after blockers 1-4 and explicit Phase D approval.

## 8. Recommended Next Steps

1. Obtain official LG3-1B-INT4 `.pte` + tokenizer + SHA256; set in `LlamaGuardModelManager`.
2. Add `org.pytorch:executorch:0.6.0+` to catalog, build, device-test init/inference.
3. Validate download cancellation/recovery/low-storage on device.
4. Then propose LG4 backend route with image format verification.

---
No image uploads activated. Awaiting explicit approval.
