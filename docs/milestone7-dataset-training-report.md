# Milestone 7 — Dataset & Experimental Training Report

## 1. DeepGHS Inspection
- URL: huggingface.co/datasets/deepghs/nsfw_detect — MIT on repo metadata only, not image-level. Card omits provenance, class counts, redistribution rights. **Conclusion: MIT ≠ image rights; do not train until maintainer confirms.**
- Size 10K–100K, sensitive flag, no integrity hash.

## 2. Swiss AI Center
- Service docs: staging only, binary safe/nsfw + subcats, MobileNetV2 transfer, 95.7% accuracy claim. Dataset research-only — **request pending, not granted**.
- Template: “Request academic access — detail institution/use, no commercial scrape.”

## 3. FGO
- 476k frames (211k obscene/239k non-obscene/26k context) from 1,451 adult + UCF101. **Academic-only, non-commercial** — email to all authors required, subject fixed. **Request pending, not granted.**

## 4. Audit Manifest
- Created `datasets/MANIFEST.md` — source, license, status all **NOT ACQUIRED**. No images downloaded, no training started (per stop condition).

## 5. Balancing
- Gaps: swimwear, fitness, medical, artistic, non-sexual skin — all under-represented; requires CC0 supplement.

## 6. Architecture & Preservation
- Current: MobileNetV2 ImageNet→5-way, 224 center-crop RGB/255, `hentai+porn+sexy≥0.70` sum, 10MB deflate → 12MB, ~40ms CPU, preserved as `nsfw-v1.tflite`.
- Experimental target: MobileNetV2-0.35 INT8 (3.5MB) on rebalanced 5-way → remap to SAFE/SUGGESTIVE/EXPLICIT/HENTAI/REVIEW per-section 3 rules. **No INT8 trained yet — pending dataset.**

## 7. Benchmark Design
- 170 images (ordinary 30, swimwear 20, skin 15, medical 15, artistic 10, anime 15, hentai 15, suggestive 20, explicit 20, video 10), 20% held-out, per-class P/R, macro F1, confusion, FPR/FNR, size/latency on Pixel device.

## 8. English Output
- Safe/Suggestive/Explicit/Hentai/Review strings verified in `ImageSafetyScreen.kt`.

## 9. Build/Test — Actual
- `assembleDebug` PASS, `testDebugUnitTest` 192 passed, `lintDebug` PASS (this milestone, no model replace).

## 10. Limitations & Next
- **No datasets acquired — all requests pending approval. Training blocked until rights confirmed.** Do not replace `nsfw-v1.tflite`.

**Explicit: Production model NOT replaced. Live monitoring disabled. Awaiting explicit approval post-dataset access.**
