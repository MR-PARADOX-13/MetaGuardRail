# Meta Guard Rail: developer handoff and hackathon MVP plan

## Scope to promise

Treat this as an opt-in Android screen-safety prototype for Instagram and Facebook. It samples what is visible, analyzes locally, and covers the screen after a possible match. A realistic hackathon demo can show detection and a user-controlled warning; it should not promise that every harmful post is blocked or that content is removed from Meta.

The existing implementation already has text, image-cue, and optional speech paths, but their coverage is uneven. The biggest product gap is violence detection in images/video. Resolve that gap with a suitable, licensed model and measured results before claiming visual violence detection.

## First-run checklist for a new developer

1. Clone the repository and open its root folder in Android Studio.
2. Install Android SDK 37 if Android Studio requests it, then wait for Gradle sync.
3. Run the `app` configuration on a physical Android phone. Use Android 10 or later to explore optional playback capture; keep audio checks off while first validating text and image checks.
4. Start protection and grant permissions one by one: notifications where required, optional audio permission, display over other apps, app-usage access, then Android’s screen-capture approval. Leave protection running only while testing.
5. Confirm the app reports that it is monitoring, that it waits when a non-Meta app is foregrounded, and that its persistent notification has a working Stop action.
6. Test with benign, consented sample screens first. Record device model, Android version, app versions, enabled categories, language, result, and time-to-warning. Do not use private messages or collect people’s screen content for a demo dataset without consent.
7. Before changing capture, permissions, model assets, or networking, read this guide and the license/notice files in `app/src/main/assets/`.

The Gradle wrapper is checked in. A local debug build can be made with `./gradlew :app:assembleDebug`. The repository uses `main` as its default branch. Do not commit generated APKs, AABs, build folders, `local.properties`, signing credentials, or machine-specific Android Studio settings. The `.gitignore` covers these local files; check `git status` before each commit and review any newly added model assets and their licenses.

## Current behavior and boundaries

| Area | In the project now | Do not claim yet |
| --- | --- | --- |
| Text | Phrase-based rules analyze pasted/shared text and OCR output. English and Telugu script are recognized; a limited list of Romanized Telugu phrases is matched. | General language understanding, full Telugu coverage, or reliable context/intent recognition. |
| Screen | A user-started MediaProjection session samples frames about every 1.2 seconds. Usage access gates analysis to Instagram, Facebook, and Facebook Lite package names. | Immediate interception, all Meta clients, stories/DMs on every device, or zero exposure before warning. |
| Adult imagery | A local TFLite model checks each sampled frame for a combined adult/suggestive cue. | A definitive sexual-content decision, precise location of a post in the frame, or violence-image detection. |
| Violence | Text-related violence terms can be matched in OCR or recognized speech. | Visual violence classification; there is no violence-image/video model yet. |
| Audio | Optional Vosk speech transcription for one selected language. It captures eligible app playback when Android and the app permit it; the room microphone is not used. | Sound-event detection, music/scream analysis, always-available capture, or strong Telugu speech accuracy. |
| Warning | A possible match can cover the screen with a brief continue option or exit-to-home option. | Removing a post, controlling Meta’s feed, or stopping already-playing audio. |
| Privacy | Current OCR, local phrase rules, image inference, and speech recognition run on-device. The app does not write sampled frames or transcripts. Speech models download on first use. | A promise that future integrations, OS logging, or all downloaded model packages have been independently audited. Revalidate after changes. |

False positives and missed items are expected in this prototype. A finding means “review this,” not “this content is harmful.”

## Recommended delivery plan

### 1. Make the demo predictable

- Pick and document one primary test phone and Android version; keep a second device as a compatibility check.
- Add a clear status for every setup state: permissions missing, waiting for a supported app, models loading, checking, warning shown, and stopped.
- Make capture and audio opt-in, keep an always-visible stop action, and clean up projection, recognizers, overlay, and worker threads on every exit path.
- Show which modality raised the warning and a plain-language confidence/uncertainty explanation. Avoid showing captured post text in logs or analytics.
- Handle protected/blank frames and capture failures as “unable to check,” not “safe.”

### 2. Measure the current detector before replacing pieces

- Build a small consented evaluation set for each supported category and each modality. Include neutral hard negatives such as medical/educational wording, ordinary swimwear, non-sexual body imagery, common Telugu spelling variants, and harmless quoted language.
- Keep English and Telugu-script samples separate in the report; record Romanized Telugu separately. Do not combine all languages into one headline score.
- Record per-category precision, recall, false-positive rate, missed examples, and p50/p90 time from first visible sample to warning. Record the device, battery/thermal state, and screen size used.
- Start with proposed hackathon targets such as at least 85% recall and 80% precision on the explicitly defined demo set, with p90 warning time under 5 seconds on the primary device. These are targets to measure, not current results or guarantees. Report the sample size and misses beside the scores.
- Keep a reproducible manual demo script with both positive and benign examples. Never use “100% safe,” “blocks all,” or equivalent claims.

### 3. Close the visual violence gap

- Choose a violence image/video model only after confirming its Android runtime, model size, commercial/hackathon license, attribution, and suitability for on-device use.
- Run it on sampled frames through a small, explicit detector interface shared with the existing adult-content model. Return category, score, model version, and inference time.
- Calibrate using a consented labeled set containing non-graphic examples and realistic benign hard negatives. Measure misses and false alerts separately from adult-content results.
- Do not infer violence from “aggressive-looking” people or objects alone; use the model’s documented classes and conservative thresholds. Keep manual review in the warning flow.

### 4. Improve text and Telugu coverage

- Keep OCR and text classification separable so the same labeled phrases can test pasted text, shared text, OCR, and speech transcripts.
- Add OCR confidence and, where practical, identify text regions to avoid analyzing irrelevant UI labels. Preserve enough context to distinguish educational or reporting content from promotion.
- Expand Telugu script and transliteration with a native Telugu reviewer. Track spelling, spacing, transliteration, and code-mixed examples. Phrase lists alone are not a language understanding system.
- Add explicit evaluations for common benign contexts and make rules editable/versioned rather than accumulating unreviewed keyword lists.

### 5. Make audio optional, safe, and useful

- Document the Android and app restrictions on playback capture. On unsupported devices or protected streams, show “audio unavailable” and continue visible-text/image checks.
- Keep one language selected at a time and disclose the first-use download size, network use, storage, and memory cost. Add versioned HTTPS downloads with a trusted checksum and an atomic install/rollback path before public distribution.
- Evaluate English and Telugu word error rates on consented clips, plus category precision/recall after transcription. Report speech transcription as experimental until it meets the same disclosed acceptance process.
- Do not call this violence-audio detection: the current path transcribes speech only and does not recognize screams, music, or other sound events.

### 6. Improve warning behavior and privacy

- Treat the warning as a user safety prompt. Make it accessible, dismissible by a clear action, and understandable when a detector is wrong. Keep the existing warning’s statement that it hides the screen but does not remove the post or stop audio.
- Test interaction with system bars, rotation, split screen, incoming calls, permission revocation, app switching, and service termination on the target devices.
- Keep raw frames, OCR text, and transcripts in memory only. Avoid log statements, crash breadcrumbs, analytics, or bug reports that can capture them. If a future feature stores examples, add explicit consent, retention, deletion, and encryption before implementing it.
- Maintain a privacy/data-flow note and review Android/Play policy requirements for screen capture, overlays, usage access, microphone permission wording, and model downloads before public release.

## Hackathon MVP acceptance checklist

Call the demo MVP-ready only when all applicable items below are shown and documented:

- A fresh clone syncs and builds with the documented Android Studio/SDK setup; no local machine files or secret keys are needed.
- The user explicitly starts and can stop protection. A persistent notification communicates that screen monitoring is active.
- The app clearly states that it analyzes a sampled screen only for supported Meta packages and explains when Android or the app prevents capture.
- English and Telugu-script visible text are evaluated separately with measured precision/recall; Telugu audio remains labeled experimental unless it independently passes evaluation.
- Adult/suggestive image results and any violence image results are reported as possible matches with their own measured scores. If no licensed violence model is integrated and evaluated, the demo explicitly says visual violence is not checked.
- The team can show a benign hard negative and explain one known limitation without implying complete blocking.
- No raw screen frame, recognized text, or transcript is uploaded or persisted by the app. Model downloads are versioned and license notices are included.
- Demo results include dataset size, device, Android version, measured warning latency, false positives, and misses.

## Git and handoff workflow

The project already contains a local `.git` directory but currently has no commit and no remote. To publish it, the owner should create a private repository and provide its clone URL, then explicitly authorize the initial commit and push. Before that first commit:

1. Review `git status` and inspect every staged file. Keep source, Gradle wrapper/configuration, necessary licensed model assets, and documentation.
2. Remove machine-specific IDE state from the index if it is already staged; `.gitignore` only prevents future additions and does not unstage existing files.
3. Confirm `local.properties`, generated APKs/AABs, build/cache folders, signing keys, and credentials are excluded.
4. Create the first commit with a clear message, add the agreed remote, and push the agreed branch. Keep the repository private unless the owner chooses public visibility.
5. For ongoing work, create a short-lived branch per feature, keep commits focused, and open a pull request with the behavior changed, device tested, permissions involved, and detector evaluation results.

Do not place Git credentials, access tokens, keystore passwords, or signing files in source code, Gradle files, project properties, chat, or commits.
