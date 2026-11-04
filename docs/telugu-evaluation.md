# Telugu Evaluation — Llama Guard 3 Gap

## Finding

Llama Guard 3 1B does **not** officially list Telugu among supported languages. Per Phase 5:

- Do not claim native Telugu classification.
- Preserve original Telugu text (`teluguText` separate in `ScreenMonitoringService.processLatestFrame`).
- If `isTelugu(text)` and model==LG3-1B → return `REVIEW` with `uncertainLanguage=true` (not SAFE, not HIGH_RISK).
- Do not silently translate all Telugu and assume equivalent accuracy.

## Strategy

1. Keep `TeluguTextRecognizer` + `tel.traineddata` (Apache 2.0) as-is; pass `originalTelugu` + `languageHint` to `ContextAwareSafetyEngine`.
2. Evaluate transliteration → LG3 separately; measure against human-labeled Telugu corpus (Phase 6 group K).
3. Language detection failure → `REVIEW`, never `HIGH_RISK`.

## Bench

- Group K (≈8 Telugu samples) measured independently.
- Target: no false HIGH_RISK from language misclassification.
