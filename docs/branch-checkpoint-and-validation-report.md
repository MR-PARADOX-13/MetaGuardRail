# Branch Checkpoint & Validation Report — MetaGuardRail

> Generated: 2026-11-03 · Branch: `feature/context-aware-safety` · Commits: `58372b9 → ce75177 → 991c408` · SDK 37 / minSdk 24 / AGP 9.5.0 · No secrets in repo

---

## 1. Branch & Commit Information

| Field | Value |
|-------|-------|
| Working directory | `/home/sunny/AndroidStudioProjects/MetaGuardRail` |
| Branch | `feature/context-aware-safety` — **confirmed correct** |
| Base commit | `58372b9 chore: baseline checkpoint pre-context-aware-upgrade` |
| Checkpoint commit | `ce75177 checkpoint: context-aware safety implementation` |
| Report commit | `991c408 docs: branch checkpoint and validation report — 192 tests PASS, APKs 65M arm64, device BLOCKED` |
| Base branch | `main` @ `33b6673 Update GitHub handoff instructions` — **not modified** |
| `git diff --check` | **PASS** — no whitespace errors (working tree + staged) |
| `git status` | **clean** — `nothing to commit, working tree clean` |
| Force-push | **none** |

Commit policy: branch was correct and changes safe → checkpoint created per requested message. No deletions, conflicts, or unclear changes found. `main` untouched.

---

## 2. Files Staged & Committed

**Checkpoint `ce75177` — 48 files, +1909/−14 lines**

### Modified (10 + .gitignore)
- `.gitignore` — added `local-proxy/.env` + `local-proxy/node_modules/` (+2 lines)
- `app/src/main/AndroidManifest.xml` — added `android:networkSecurityConfig="@xml/network_security_config"` (+1)
- `app/src/main/java/com/example/metaguardrail/MainActivity.kt` (+3)
- `app/src/main/java/com/example/metaguardrail/data/ActivityEvent.kt`
- `app/src/main/java/com/example/metaguardrail/data/ActivityRepository.kt`
- `app/src/main/java/com/example/metaguardrail/data/UserPreferencesRepository.kt` — dual cloud consent keys OFF by default
- `app/src/main/java/com/example/metaguardrail/monitor/ScreenMonitoringService.kt` — `ContextAwareSafetyEngine` wiring
- `app/src/main/java/com/example/metaguardrail/ui/ImageSafetyScreen.kt`
- `app/src/main/java/com/example/metaguardrail/ui/screens/ActivityScreen.kt`
- `app/src/main/java/com/example/metaguardrail/ui/screens/AnalyzeScreen.kt`
- `app/src/main/java/com/example/metaguardrail/ui/screens/SettingsScreen.kt` — dual toggles, export/share

### New — `ai/` engine (8)
- `app/src/main/java/com/example/metaguardrail/ai/CloudVisualViolenceClassifier.kt` — LG4 proxy, flag-gated OFF
- `app/src/main/java/com/example/metaguardrail/ai/ContextAwareSafetyEngine.kt` — unified pipeline (keywords + Llama Guard heuristic + RiskEngine), Telugu uncertain→REVIEW
- `app/src/main/java/com/example/metaguardrail/ai/ExecuTorchLlamaGuardClassifier.kt` — `ENABLE_LLAMA_GUARD_BUILD_FLAG=false`, fallback `heuristic-v1`
- `app/src/main/java/com/example/metaguardrail/ai/KeywordEvidence.kt`
- `app/src/main/java/com/example/metaguardrail/ai/LlamaGuardClassifier.kt` — interface + `HeuristicLlamaGuardClassifier`
- `app/src/main/java/com/example/metaguardrail/ai/LlamaGuardModelManager.kt` — on-demand `.pte` (600–900 MB), `EXPECTED_SHA256=""` placeholder → non-production, atomic `.part`, `hasStorageSpace(1.2GB)` / `hasEnoughMemory(1.5GB)`
- `app/src/main/java/com/example/metaguardrail/ai/RiskEngine.kt` — per-category thresholds, `decide()` + `needsShield()`
- `app/src/main/java/com/example/metaguardrail/ai/TextPreprocessor.kt` — sentence segmentation, educational/fictional/quotation/negation/hypothetical detection

### New — security/config (1)
- `app/src/main/res/xml/network_security_config.xml` — `cleartextTrafficPermitted=false` base, allowlist `10.0.2.2/localhost/127.0.0.1` for debug+emulator only

### New — tests (6 suites, +47 tests)
- `app/src/test/java/com/example/metaguardrail/CloudImageConsentTest.kt` — 5 tests
- `app/src/test/java/com/example/metaguardrail/CloudVisualViolenceClassifierTest.kt` — 12 tests
- `app/src/test/java/com/example/metaguardrail/ContextAwareEvaluationTest.kt` — 11 tests
- `app/src/test/java/com/example/metaguardrail/CorpusFullEvaluationTest.kt` — 3 tests
- `app/src/test/java/com/example/metaguardrail/RiskEngineTest.kt` — 5 tests
- `app/src/test/java/com/example/metaguardrail/VisualContentClassifierTest.kt` — 11 tests

### New — corpora & benchmarks (5)
- `app/src/testAssets/evaluation/corpus.jsonl` (43 lines)
- `app/src/testAssets/evaluation/corpus_v2.jsonl` (27 lines)
- `benchmark/README.md` + `benchmark/prepare_dataset.sh` + `benchmark/run.py`

### New — docs (11 + this report)
- `datasets/MANIFEST.md` — all sources NOT ACQUIRED, no downloads
- `docs/baseline-metrics.md` — pre-upgrade FPs + genuine threats + targets
- `docs/context-aware-upgrade-report.md`
- `docs/meta-model-evaluation.md`
- `docs/milestone-audit-lg3-lg4.md`
- `docs/milestone2-audit-report.md`
- `docs/milestone3-physical-validation-report.md`
- `docs/milestone4-validation-report.md`
- `docs/milestone5-automated-validation-report.md`
- `docs/milestone6-visual-classifier-upgrade-report.md`
- `docs/milestone7-dataset-training-report.md`
- `docs/safety-architecture-map.md` — end-to-end pipeline diagram
- `docs/taxonomy-customization.md`
- `docs/telugu-evaluation.md`
- `docs/branch-checkpoint-and-validation-report.md` — this file (commit `991c408`)

### New — proxy & scripts (3)
- `local-proxy/README.md` + `local-proxy/server.js` (`POST /v1/lg4/visual` + `GET /health`, private-range allowlist)
- `scripts/check_env.sh` — Java/Gradle/Node/ADB/env presence checks

---

## 3. Files Intentionally Excluded

| File / Pattern | Reason | Verified |
|----------------|--------|----------|
| `local.properties` | Contains `baseten.apiKey` + `backend.baseUrl` secrets | `git ls-files \| grep` → 0 hits; `git check-ignore -v` → `.gitignore:6:/local.properties` — **PASS** |
| `local-proxy/.env` | Contains `NVIDIA_API_KEY` for LAN proxy | `git check-ignore -v` → `.gitignore:7:local-proxy/.env` — **PASS** |
| `*.apk` / `*.aab` | Generated ABI splits (65–68 MB) | `git ls-files \| grep apk` → 0 hits; `check-ignore` → ignored — **PASS** |
| `*.jks` / `*.keystore` / `keystore.properties` | Signing credentials | `git ls-files \| grep` → 0 hits — **PASS** |
| `**/build/` / `.gradle/` / `.idea/` | Build/cache/IDE | `.gitignore` covers — **PASS** |

**Staging audit:** targeted `git add <48 explicit paths>` used — no `git add -A`. Staged set grepped for `sk-ant|sk-proj|NVIDIA_API_KEY=` → 0 hits (the only match was in `docs/milestone4-validation-report.md` describing the policy, not a real key). APK/local.properties/.env not in `git diff --cached --name-only`.

---

## 4. Build Results — PASS

```bash
./gradlew app:assembleDebug   # PASS — Build finished successfully.
```

| APK (ABI-split, `universalApk=false`) | Path | Size |
|----------------------------------------|------|------|
| **arm64-v8a — vivo I2214 target** | `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` | **65M** (67,913,412 bytes) |
| armeabi-v7a | `app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk` | 57M |
| x86 | `app/build/outputs/apk/debug/app-x86-debug.apk` | 67M |
| x86_64 | `app/build/outputs/apk/debug/app-x86_64-debug.apk` | 68M |

- Package `com.example.metaguardrail` v1.0 vCode 1, targetSdk 37, debug keystore (release signing not configured — correct, Phase 5 preserved).
- Assets: `moderation/nsfw.tflite.deflate` 16M + `ocr/tessdata/tel.traineddata` 2.7M; native libs `libvosk.so`, `libtensorflowlite_jni.so`, `libmlkit_google_ocr_pipeline.so`, `libtesseract.so`, etc.
- `nsfw.tflite.deflate` **not replaced**; `splits.abi` unchanged.

---

## 5. Lint Results — PASS

```bash
./gradlew app:lintDebug   # PASS — Build finished successfully.
```

| Metric | Value |
|--------|-------|
| Result | **PASS** |
| Errors | **0** |
| Active warnings (non-baselined) | **5** |
| Warnings filtered by baseline (`lint-baseline.xml`) | 54 |
| Baseline note | 1 unmatched `AndroidGradlePluginVersion` — informational, not a failure |
| Active issues | `LintBaseline` (1), `LintBaselineFixed` (1), `ObsoleteLintCustomCheck` (lifecycle lint invalid API — skipped), `ComposableNaming` (1), `UseKtx` (3 × `String.toUri`) |
| `abortOnError` | `true` — would fail on new errors, currently clean |

HTML report: `app/build/reports/lint-results-debug.html` (128K). No new lint errors from this branch.

---

## 6. Exact Test Results — PASS (192/192)

```bash
./gradlew app:testDebugUnitTest   # PASS
```

| Suite | Tests | Failures | Errors | Skipped |
|-------|-------|----------|--------|---------|
| `CloudImageConsentTest` | 5 | 0 | 0 | 0 |
| `CloudVisualViolenceClassifierTest` | 12 | 0 | 0 | 0 |
| `ContentSafetyAnalyzerTest` | 9 | 0 | 0 | 0 |
| `ContentSafetyAnalyzerExpandedTest` | 67 | 0 | 0 | 0 |
| `ContextAwareEvaluationTest` | 11 | 0 | 0 | 0 |
| `CorpusFullEvaluationTest` | 3 | 0 | 0 | 0 |
| `ExampleUnitTest` | 1 | 0 | 0 | 0 |
| `LinkSafetyAnalyzerTest` | 39 | 0 | 0 | 0 |
| `MuseSparkConsentTest` | 2 | 0 | 0 | 0 |
| `MuseSparkRepositoryTest` | 27 | 0 | 0 | 0 |
| `RiskEngineTest` | 5 | 0 | 0 | 0 |
| `VisualContentClassifierTest` | 11 | 0 | 0 | 0 |
| **TOTAL** | **192** | **0** | **0** | **0** |

- No tests removed or weakened. 6 new suites added for context-aware + consent + visual threshold.
- Instrumented `connectedAndroidTest` not run — requires device (not fabricated).

---

## 7. APK Paths & Sizes

See §4. Primary artifact for vivo I2214:

```
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk — 67,913,412 bytes (65M)
Install: adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
Alt:     ./gradlew app:installDebug  # after device connected
```

Do not install all 4 APKs — they are ABI splits of one package.

---

## 8. Security Inspection — PASS

| Check | Result | Evidence |
|-------|--------|----------|
| APK strings contain `BASETEN`/`sk-ant`/`sk-proj`/`NVIDIA_API_KEY` | **PASS — 0 hits** | `aapt2 dump strings app-arm64-v8a-debug.apk \| grep -i` → 0 |
| Tracked `*.kt` contain hardcoded secrets | **PASS — 0 hits** | `grep` over staged `ai/` → no `sk-*` key pattern |
| `local.properties` in Git | **PASS — excluded** | `git ls-files \| grep` → 0 |
| `local-proxy/.env` in Git | **PASS — excluded** | `check-ignore` → ignored |
| `*.keystore`/`*.jks`/`*.apk` in Git | **PASS — 0 hits** | `git ls-files \| grep` → 0 |
| Debug vs Release | **PASS** | `app/build.gradle.kts`: `afterEvaluate { assembleRelease dependsOn checkReleaseNoApiKey }` — release fails if `devBasetenKey` present. `BACKEND_BASE_URL` placeholder `https://meta-guardrail-backend.example.com`. |
| `network_security_config.xml` | **PASS** | `cleartextTrafficPermitted=false` base; `10.0.2.2/localhost/127.0.0.1` allowlist debug/emulator only; `debug-overrides user certs`. No prod cleartext. |
| Cloud consent defaults | **PASS — OFF** | `cloudTextAnalysisEnabled=false`, `cloudImageAnalysisEnabled=false`; Settings toggles OFF; `isBackendConfigured()` + `cloud*Enabled` double-gate; proxy private-range allowlist. |
| Severity policy | **Preserved** | `VisualContentClassifier` threshold `0.70` sum unchanged — documented as known limitation (see §11). `RiskEngine` adds capping, not lowering. |

---

## 9. ADB / Device Connection Status — BLOCKED (no device)

```
adb version  — Android Debug Bridge version 1.0.41 (34.0.5-debian)
adb devices -l — List of devices attached (empty)
lsusb — no phone detected (only camera/hub/BT internals)
```

- **Status: BLOCKED** — no physical device connected/authorized. No install attempted, no screenshots, no logcat, no perf metrics fabricated.
- **Historical device (not connected now):** vivo I2214 / Android 16 (SDK 36) / `arm64-v8a`.

---

## 10. Physical-Device Tests — PENDING (all 10, awaiting device)

### Prerequisite — connect device

```bash
# 1. Phone: Settings → About → tap Build number 7× → Developer options → USB debugging ON
# 2. Connect via data USB (not charge-only), tap Allow on RSA fingerprint
adb kill-server; adb start-server; adb devices -l   # should list I2214
adb shell getprop ro.product.model; adb shell getprop ro.build.version.release; adb shell getprop ro.product.cpu.abi
```

#### 1. MediaProjection permission & live screen sampling — PENDING
Clear app data → launch → Start protection → grant Notifications (T33+) → overlay → usage access → accept MediaProjection → verify `LiveProtectionState.isRunning=true`, `message="Monitoring Instagram & Facebook"`. Non-Meta app foreground → `Waiting for Instagram/Facebook`. Deny path → user error, no crash.

#### 2. English OCR — PENDING
With protection active, open Instagram feed with English text → 2–5s → `finding` reflects OCR text. ML Kit Latin; `combinedText` non-empty; `seenContentHashes` 5s dedup; confidence handling. Files: `TeluguTextRecognizer` (Latin path), `ScreenMonitoringService`.

#### 3. Telugu OCR — PENDING
Same as #2 with Telugu-script content. Tesseract `tel.traineddata` from `assets/ocr/tessdata/`; `finding` contains Telugu; `ContextAwareSafetyEngine` marks flagged Telugu as `REVIEW` + "(Telugu — uncertain)". Also test `AnalyzeScreen` paste path.

#### 4. TFLite image classification — PENDING
`AnalyzeScreen → Image → PickVisualMedia` → select ordinary / swimwear / medical / anime SFW → expect SAFE (FP documented). Corrupt file → graceful null+message. ByteBuffer reuse, `bitmap.recycle()`, no OOM on 10 MB. **Known limitation:** sum `hentai+porn+sexy ≥0.70` causes FPs on swimwear/fitness where `sexy≈0.5` + noise — documented, not changed.

#### 5. Warning overlay & 8-second snooze — PENDING
Trigger `HIGH_RISK`/`WARNING` → shield appears → Continue → `@Volatile shieldSuppressedUntil` suppresses 8s → expiration re-arms; Stop while visible → `finishSession()` removes view + cancels scope. Check duplicate, nav-bar safe, rotation/split-screen/incoming call/permission revocation → `SecurityException` caught.

#### 6. Audio playback capture — PENDING
Android 10+ with protection, enable English → play Instagram video with speech → transcription in `finding` `source=AUDIO`. Uses `AudioPlaybackCaptureConfiguration` (MEDIA/GAME/UNKNOWN + uid-filter), not mic; `RECORD_AUDIO` denied → "audio unavailable" fallback.

#### 7. Vosk model download & language selection — PENDING
First enable speech → 40–58 MB HTTPS download → atomic `*.installing → rename` + `MIN_SPEECH_ARCHIVE_BYTES` corruption check → switch language → re-download; cancel mid-download → no partial file.

#### 8. Notification & foreground-service behavior — PENDING
Persistent notification (`HIGH_RISK→IMPORTANCE_HIGH`, `WARNING→DEFAULT`) with Stop; background → service not killed; revoke permission → user message. `IncidentNotifier` sanitized, `VISIBILITY_PRIVATE`, dedup stringSet; `startForegroundService` + `ProjectionCallback`.

#### 9. Stop action & resource cleanup — PENDING
Stop (app + notification) → `isRunning=false`, notification removed, `VirtualDisplay`/`ImageReader`/`MediaProjection` released, `serviceScope.cancel()`, overlay removed, worker threads terminated. `logcat | grep -i FATAL|ANR|ForegroundServiceStartNotAllowed|OOM|MediaProjection` → clean.

#### 10. CPU, RAM, battery & detection latency — PENDING
5 min active on I2214: `dumpsys cpuinfo | grep metaguardrail`, `dumpsys meminfo`, `batterystats`, frame OCR time, `p50/p90 time-to-warning`. Targets: p90 <5s; heuristic <5ms, LG3 expected P95 <800ms after validation.

---

## 11. Remaining Blockers

| # | Blocker | Status | Action |
|---|---------|--------|--------|
| 1 | No device connected | BLOCKED | Connect I2214 via data USB + RSA, re-run `adb devices -l` + 10 tests |
| 2 | Visual violence gap | PENDING | Choose licensed model (ExecuTorch LG3-1B-INT4 + LG4 cloud) — verify runtime/size/license/SHA256; currently `ENABLE_LLAMA_GUARD_BUILD_FLAG=false` |
| 3 | Visual FP on benign skin | Known limitation | Sum `0.70` collapses suggestive+porn+sexy → FP on swimwear/medical; documented, not silently changed; fix = DeepGHS/FGO + `nsfw-v2.tflite` INT8 (awaiting approval) |
| 4 | Datasets not acquired | BLOCKED | DeepGHS/FGO/Swiss requests pending; `datasets/MANIFEST.md` = NOT ACQUIRED |
| 5 | LG3 SHA256 empty | PENDING | `EXPECTED_SHA256=""` — set to official `.pte` hash before production |
| 6 | Backend not deployed | BLOCKED | `BACKEND_BASE_URL` placeholder; `localhost:3000/health →404`; cloud falls back to local (correct) |
| 7 | Telugu limited | Known limitation | Script + limited Romanized, no native review, speech experimental; `HIGH_RISK→REVIEW` cap |
| 8 | Audio limited | Known limitation | Playback-capture not always allowed; transcription only; WER not measured |
| 9 | Release signing | PENDING | Debug keystore only; `signingConfigs` before publish |
| 10 | Backend auth stub | PENDING | `X-App-Check len≥10` — real App Check before public |
| 11 | Instrumented tests | PENDING | `connectedAndroidTest` needs device |

---

## 12. Recommended Next Actions

1. **Device validation first** — Connect I2214, `app:installDebug`, run 10 tests, capture `adb logcat -d -v threadtime > metaguardrail_device_log.txt`, check `FATAL/ANR/SecurityException/MediaProjection`.
2. **Do not merge to `main`** — Keep `feature/context-aware-safety`; open PR after device validation + report update with measured latencies.
3. **Do not train/integrate `nsfw-v2.tflite`** — Awaiting explicit approval per `docs/milestone7-dataset-training-report.md` stop condition.
4. **Do not enable LG3/LG4 / cloud ON / live violence / backend deploy** — All flag-gated OFF; requires `EXPECTED_SHA256`, ExecuTorch AAR, device bench P95 <800ms, explicit approval.
5. **Preserve `nsfw-v1.tflite` threshold `0.70`** — Any change versioned + evaluated on held-out 170-image benchmark with precision/recall delta.
6. **Backend deployment (when ready)** — Follow `backend/README.md` (Render/Fly/Docker), set `backend.baseUrl` in `local.properties`, keep `BASETEN_API_KEY` server-env only.
7. **Security hardening before publish** — Real App Check, release signing, Play policy review for screen capture/overlay/usage access, OS logging audit for frames/transcripts.

---

## Verification Command Summary

```bash
git branch --show-current            # feature/context-aware-safety
git log --oneline -3                 # 991c408 → ce75177 → 58372b9
git status                           # clean
./gradlew app:assembleDebug          # PASS — 4 APKs
./gradlew app:lintDebug              # PASS — 5 warnings, 0 errors
./gradlew app:testDebugUnitTest      # PASS — 192 passed
ls -lh app/build/outputs/apk/debug/*.apk
aapt2 dump strings app-arm64-v8a-debug.apk | grep -i BASETEN  # 0 hits
adb devices -l
adb shell getprop ro.product.model; adb shell getprop ro.build.version.release; adb shell getprop ro.product.cpu.abi
./gradlew app:installDebug
adb logcat -d -v threadtime > metaguardrail_device_log.txt
```

*No device, test, or log results fabricated. All BLOCKED/PENDING statuses honest pending physical-device connection.*

