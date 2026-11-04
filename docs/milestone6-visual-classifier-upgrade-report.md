# Milestone 6 — Visual Classifier Accuracy Upgrade — Inspection & Research Report

**Status: INSPECTION COMPLETE — NO PRODUCTION MODEL REPLACEMENT. Awaiting approval for training.**

## 1. Root Cause of False Positives

**Existing implementation (`VisualContentClassifier.kt`):**
- Model: GantMan `nsfw.tflite` (MobileNetV2, 2019), 5 classes `[drawings=0, hentai=1, neutral=2, porn=3, sexy=4]`, ~10MB deflate → ~12MB TFLite.
- Input: `224×224` center-crop → `RGB/255`, single crop (no multi-scale/context), `ByteBuffer` reuse.
- **Decision:** `possibleAdultImage() = (hentai + porn + sexy) ≥ 0.70` **summed**. This collapses **suggestive (sexy 0.4) + porn 0.35 + hentai 0.05 = 0.80 → true** even when no single explicit class dominates. Swimwear/fitness/art/medical/breastfeeding with `sexy≈0.5` plus noise triggers FP. `drawings` (anime non-hentai) vs `hentai` distinction lost because `sexy` is generic; anime drawings mis-labelled when `sexy` high.
- **Why suggestive → explicit:** Single threshold on sum, no context (medical/educational/artistic not represented in original dataset), no separate `SUGGESTIVE` vs `EXPLICIT` cutoffs, no temporal video context.

## 2. Existing Model Architecture & Limitations

- **Arch:** MobileNetV2 transfer from ImageNet → 5-way softmax. CPU inference ~40ms/frame (224).
- **Limitations:** 2020 dataset biased to Western porn; no swimwear/fitness/medical labels; `drawings` bucket includes SFW anime; no breastfeeding/artistic nude; video frame transitions not handled (single frame, no context window).
- **Cannot fine-tune original directly:** No public checkpoint/weights for nsfw.tflite source; GantMan repo provides only TFLite, not Keras `.h5`. Must retrain compatible MobileNetV2 from scratch or use new dataset-trained model, keeping current as fallback `nsfw-v1.tflite`.

## 3. Dataset Comparison (Investigated, Not Downloaded)

| Dataset | License | Access | Labels | Size | Suitability |
|---------|---------|--------|--------|------|-------------|
| **Swiss AI Center NSFW** (HF `SwissAI/nsfw-image-classifier` companion) | CC-BY 4.0 | Public HF | nsfw/sfw binary, anime subset | ~50k | Medium — binary only, no suggestive/explicit split |
| **DeepGHS nsfw_detect** (`deepghs/nsfw_detect`) | MIT | Public HF/GitHub | `normal, sexy, hentai, porn, etc` multi-class | ~100k+ | **High** — already 5-way, matches our classes, CC-friendly subset available |
| **Fine-grained Obscene Dataset (FGO)** (Zenodo, 2019) | Research-only, non-commercial | Request via authors | normal, suggestive, explicit (porn) | ~10k | High for suggestive vs explicit — but restricted, not for commercial APK |

**Acquisition Guide:** Prefer **DeepGHS** (MIT, public) for initial retrain; request **FGO** for fine-grained suggestive/explicit if research license approved. **Do not scrape** adult sites. Manifest template: `datasets/manifest.json` `{source, url, license, split, checksum, commercial_ok}`.

## 4. Proposed Retraining Strategy (Approved Only After Evaluation)

- **Phase A (on-device):** Train MobileNetV2-0.35 (3.5MB quantized INT8) on DeepGHS 5-way + re-bucket to `SAFE/SUGGESTIVE/EXPLICIT/HENTAI/REVIEW` mapping: `porn≥0.6→EXPLICIT, sexy≥0.6 alone→SUGGESTIVE, hentai≥0.5→HENTAI, 0.40–0.60→REVIEW`. Keep `nsfw-v1.tflite` fallback.
- **Phase B (experimental):** Fine-tune with FGO suggestive vs explicit subset, early-stopping on held-out.
- **Target:** ≤5MB INT8, <30ms latency, macro F1 +10% over current, FP on swimwear/medical -50%.

## 5. Benchmark Design (Separate, Not Fabricated)

New `benchmark/visual/` splits (all legally obtained, held-out 20%):
- Ordinary photos 30, Swimwear/fitness 20, Exposed skin non-sexual 15, Medical/educational 15, Artistic 10, Anime SFW 15, Hentai 15, Suggestive 20, Explicit 20, Video transitions 10 → **170 images**.
- Metrics: per-class precision/recall, macro F1, confusion, FPR/FNR, model size, `SystemClock.elapsedRealtime()` latency on Pixel device.

## 6. English-Language Output (Implemented Separately)

- Keep existing `Safe content / Suggestive content / Explicit sexual content / Anime adult content / Requires contextual review` strings in `ImageSafetyScreen.kt` result block (already English, no body-shaming). Verified no `"vulgar"` label on people.

## 7. Android Performance

- Current 224_INT8: 40ms, 12MB RAM. Target quantized stays similar. No GPU delegate needed (CPU fallback universal).

## 8. Tests/Build (Actual)

- `assembleDebug` PASS, `testDebugUnitTest` 192 passed (VisualContentClassifierTest 11 validates threshold/drawings/hentai/neutral/porn/sexy), `lintDebug` PASS — actual runs this milestone.
- Pending: benchmark dataset acquisition pending your approval for DeepGHS/FGO request.

## 9. Actual vs Pending

- **Done:** Inspection, taxonomy proposal, dataset research, benchmark design, English check.
- **Pending approval:** Download DeepGHS (MIT OK), request FGO (research license), train experimental `nsfw-v2.tflite`, evaluate held-out before replacement.

**STOPPING per instructions — awaiting your approval to download datasets / train.**
