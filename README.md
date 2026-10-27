# Meta Guard Rail

Meta Guard Rail is an opt-in Android safety prototype. While a user has started a screen-sharing session, it samples the display, checks whether Instagram or Facebook is in the foreground, and can cover the screen after a possible match. It is a best-effort warning layer; it does not modify Meta apps or remove posts.

## Open and run the app

1. Install Android Studio with Android SDK Platform 37 and the matching build tools.
2. Open this project folder in Android Studio: `MetaGuardRail` (the folder containing `settings.gradle.kts`).
3. Let Android Studio finish Gradle sync. If it asks to install an SDK component, install the project’s requested component.
4. Connect an Android phone with USB debugging enabled, or start an emulator. Select it in the device menu, then click **Run** for the `app` configuration.
5. In the app, choose the safety categories and tap **Start protection**. Grant the requested permissions and approve Android’s screen-sharing prompt. Open Instagram or Facebook to try the live checks. Stop protection from the app or its ongoing notification.

The screen checks require Android’s screen-capture, app-usage, and display-over-other-apps approvals. Android 13 and later may also ask for notification permission. Playback speech checks are optional; Android asks for the audio permission and Meta/Android must allow playback capture. The code captures app playback, not room microphone audio. The first use of speech checking downloads one model over HTTPS (roughly 40 MB for English or 58 MB for Telugu) and keeps it in private app storage.

The app supports Android API 24 and later. Playback capture needs Android 10 or later. For a command-line debug build, run `./gradlew :app:assembleDebug` from this project folder. ABI-specific APKs are written beneath `app/build/outputs/apk/debug/`; APKs and build output are intentionally excluded from Git.

## What currently works

- Manual text checking in the app, including text shared to the app from another Android app.
- Local phrase rules for English, Telugu script, and a limited set of common Romanized Telugu phrases. These rules can flag profanity, adult terms, distressing language, harassment, self-harm references, scams, and possible grooming indicators.
- Opt-in live screen sampling while Instagram, Facebook, or Facebook Lite is in the foreground. The service samples about every 1.2 seconds, resizes wide frames to at most 1,280 pixels, and runs English OCR and Telugu-script OCR on the captured frame.
- A bundled, compressed TensorFlow Lite model that checks a sampled image for possible adult/suggestive cues. A possible match can show a full-screen warning overlay with an option to continue briefly or leave for the home screen.
- Optional on-device speech transcription for one selected language (English or experimental Telugu), if Android and the Meta app allow playback capture. The model downloads on first use.
- Local preferences for safety mode, category toggles, and selected speech language.

## Current limits

This prototype does **not** have an image classifier for violence. Violence is currently flagged only when related words are readable in the OCR text or speech transcript. The adult-image model is a rough cue, not a reliable determination; it may miss content or flag ordinary images. OCR and phrase rules can also miss slang, spelling variants, image text, and context.

The overlay appears only after a sampled frame or transcript has been analyzed. It can hide the screen, but it cannot prevent the first exposure, remove the post, scroll the feed, or stop audio that is already playing. Audio transcription does not classify music, screams, or other non-speech sounds. Android, device makers, and Meta can restrict screen sharing or playback capture. The Telugu speech model is marked experimental; Telugu support does not mean complete Telugu UI translation or dependable speech recognition.

According to the current implementation, frames and transcripts are processed on-device and are not saved by the app. Speech models are downloaded on first use. Recheck this privacy statement whenever capture, networking, analytics, or storage code changes.

## Code map

| Path | Responsibility |
| --- | --- |
| `app/src/main/java/com/example/metaguardrail/MainActivity.kt` | Compose UI, settings, permission sequence, and service start/stop |
| `data/ContentSafetyAnalyzer.kt` | Local text normalization and phrase-based category checks |
| `data/UserPreferencesRepository.kt` | Local settings and category definitions |
| `monitor/ScreenMonitoringService.kt` | User-started screen capture, foreground Meta-app check, OCR coordination, and warning overlay |
| `monitor/VisualContentClassifier.kt` | Runs the bundled adult/suggestive-image cue model |
| `monitor/TeluguTextRecognizer.kt` | Offline Telugu-script OCR |
| `monitor/AudioPlaybackMonitor.kt` | Optional playback capture and local Vosk speech transcription |
| `monitor/LiveProtectionState.kt` | State shared between the monitoring service and UI |
| `app/src/main/assets/` | Tesseract Telugu data, compressed image model, and model license/notice files |

Before changing or redistributing any model asset, read the adjacent license and attribution files. Keep model download URLs, versions, checksums, and license notices in source control; never put signing keys, passwords, or personal tokens in the repository. No root-level license has been selected for the Meta Guard Rail application code; public visibility alone does not grant reuse rights.

## Next developer

Start with [`HACKATHON_MVP_GUIDE.md`](HACKATHON_MVP_GUIDE.md). It describes the current boundary of the prototype, a prioritized plan for a credible hackathon MVP, validation criteria, privacy requirements, and a handoff workflow.
