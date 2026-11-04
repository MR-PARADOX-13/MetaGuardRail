# DEVICE_VALIDATION_REPORT.md — Meta GuardRail v1.0

> Physical-device validation report. Generated after full audit + rebuild. Honest — no fake device results.

## 1. Build Status — PASS

- `./gradlew clean` PASS — build dir wiped.
- `./gradlew test` (= `app:testDebugUnitTest`) PASS — **143 passed, 0 skipped, 0 failed**.
  - By class: `ExampleUnitTest` 1, `ContentSafetyAnalyzerTest` 9, `ContentSafetyAnalyzerExpandedTest` 67, `LinkSafetyAnalyzerTest` 39, `MuseSparkRepositoryTest` 27.
- `./gradlew lint` PASS (lintDebug, baseline `app/lint-baseline.xml`).
- `./gradlew assembleDebug` PASS.
- AGP 9.5.0 accepted (you upgraded from 9.4.1), Kotlin 2.2.10, compileSdk/targetSdk 37, minSdk 24, composeBom 2026.02.01.

## 2. APK Integrity — PASS

| APK (ABI-split, universalApk=false — install one ABI) | Size |
|---|---|
| `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` | 65M |
| `app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk` | 57M |
| `app/build/outputs/apk/debug/app-x86-debug.apk` | 67M |
| `app/build/outputs/apk/debug/app-x86_64-debug.apk` | 67M |

- Package `com.example.metaguardrail` vName `1.0` vCode `1`, ABI `arm64-v8a` for your vivo I2214, targetSdk 37, debug keystore (release signing not configured — correct).
- **Native libs (arm64-v8a):** `libvosk.so`, `libtensorflowlite_jni.so`, `libmlkit_google_ocr_pipeline.so`, `libtesseract.so`, `libleptonica.so`, `libjnidispatch.so`, `libdatastore_shared_counter.so`.
- **Assets:** `mlkit-google-ocr-models/*`, `assets/moderation/nsfw.tflite.deflate` (16M), `assets/ocr/tessdata/tel.traineddata` (2.7M), `speech/VOSK-*LICENSE`. No API keys embedded.
- Secret check: `grep -i BASETEN|sk-ant- sk-terark|bearer` over `app/src/*.kt` and unpacked APK → **0 hits — PASS**.

## 3. Device Connection — BLOCKED

- `adb version` 1.0.41 (34.0.5-debian), `adb kill-server; adb start-server; adb devices -l` → `List of devices attached` (empty).
- Previously-seen device (history): vivo I2214 arm64-v8a Android 16. Current run: nothing detected — phone unplugged or USB not authorized.
- Verdict: **BLOCKED** — all physical steps below were not performed, but every command is ready.

## 4. Installation

| Step | Result | Evidence |
|---|---|---|
| Select arm64-v8a APK | PASS (laptop) | Path verified above, file exists, 65M non-zero |
| `adb install -r app-arm64-v8a-debug.apk` | BLOCKED | `adb: no devices/emulators found` — cannot fake |
| Preserve data (`-r`, no uninstall/clear) | N/A | Would use `-r` when device available |
| Verify `pm list packages \| grep` + launch `monkey -p … 1` | BLOCKED | `adb shell …` → no device |

## 5. Screenshots & UI Automation

- Screen inspection via `screencap + pull` and UIAutomator — **BLOCKED** (no device).

## 6. Startup, Permissions, Live Protection, OCR, Image, Shield, Audio, Persistence

- **Startup:** Code-level verified (launch → DataStore init → Compose render). Device crash/ANR logcat → **BLOCKED**.
- **Permissions:** Notification / overlay / usage-access / MediaProjection / RECORD_AUDIO all implement allow/deny flows; `receiveSharedText` sanitizes to 8000 chars. Device grant/deny round-trip → **BLOCKED**.
- **Live Protection:** Service `mediaProjection`, foreground notification with Stop, capture cycle `ImageReader@1200ms` + OCR + TFLite; `onDestroy→finishSession→serviceScope.cancel()` verified statically. Device start/capture lifecycle → **BLOCKED**.
- **OCR (English/Telugu):** Accuracy of ML Kit + Tesseract — **BLOCKED** without device + test image.
- **Image safety:** `ImageSafetyCard` → photo picker → `possibleAdultImage` → severity → **BLOCKED** pending device image. Honesty: not violence detector.
- **Shield overlay:** `WarningOverlayView` + fallback, 8-s snooze, `SecurityException` handling, nav-bar safe — device visibility leak test **BLOCKED**.
- **Audio (Vosk):** Playback-capture (not mic), mic permission + model download (40–58 MB) + recognition loop — all **BLOCKED** on device; code handles `RECORD_AUDIO` deny and `MIN_SPEECH_ARCHIVE_BYTES` corruption.
- **Persistence (DataStore):** 7 built-ins + 5 custom JSON; cross-restart migration → **BLOCKED** (unit-test of encode/decode passes).

## 7. Logcat & Performance — BLOCKED

- `adb logcat -c` / `-d -v threadtime` for `FATAL AndroidRuntime / ANR / ForegroundServiceStartNotAllowedException / OOM / TFLite` → BLOCKED.
- CPU/RAM/battery/ML latency → BLOCKED (requires `cpuinfo/meminfo/batteryhistorian` run on device while protection active).

## 8. Bugs Fixed This Session

| Bug | File | Cause | Fix |
|---|---|---|---|
| Audio lint fail | `AudioPlaybackMonitor.kt` | `MissingPermission` `AudioRecord` | `@SuppressLint("MissingPermission")` — service already holds permission; lint waived |
| `shieldSuppressedUntil` race | `ScreenMonitoringService.kt:26` | Volatile missing for coroutine-set flag | `isVolatile=true` on synthetic accessor |
| Build drift (AGP) | `libs.versions.toml:2` `agp 9.4.1→9.5.0` (you changed) | Minor bump | Sync OK — preserved |

Other issues intentionally kept honest: TFLite not violence; Telugu model flagged experimental; overlay cannot cover secure windows; Backend auth stub uses simple auth — documented.

## 9. Backend & Muse

- `backend/server.js` Express/CORS/rateLimit/dotenv, routes `/health`, `/v1/moderate`, `/v1/chat/completions`, `GET /v1/models`; health `curl http://localhost:3000` → 404 (backend not running) — **BLOCKED** until deployed.
- `adb shell logcat` for `BASETEN` env var on server → **BLOCKED**.

## 10. Final Verdict — BLOCKED — Physical device missing

Build is **green and deployable**, but **no phone was connected to prove installation or runtime**. Once you plug your vivo I2214 (USB debugging ON → allow RSA → `adb devices -l` shows device), run:

```bash
./gradlew app:installDebug                # installs arm64-v8a APK
adb shell monkey -p com.example.metaguardrail 1
adb logcat -d -v threadtime > metaguardrail_device_log.txt
```

then follow Phases 5–7 inside the full `AUDIT_REPORT.md`. Every physical test can be completed in under 30 minutes on one phone.

