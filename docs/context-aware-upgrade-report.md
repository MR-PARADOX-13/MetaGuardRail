# Context-Aware Upgrade Report — MetaGuardRail (Ordered, Complete)

## 1. Ordered Execution (No Approval Gaps)

1. Phase 0 — Baseline + `feature/context-aware-safety` checkpoint `58372b9`
2. Phase 1 — Meta scaffolding (LG3/LG4/Prompt Guard 2/ExecuTorch docs)
3. Phases 2–5 — `ContextAwareSafetyEngine.analyzeSync()` + `TextPreprocessor` + `RiskEngine` + `HeuristicLlamaGuardClassifier` wired to `ScreenMonitoringService` + `AnalyzeScreen`
4. Phases A–C — `VisualContentClassifierTest`, `LlamaGuardModelManager` (progress/cancel/SHA256/atomic), `ExecuTorchLlamaGuardClassifier` flag-gated, `cloudImageAnalysisEnabled` OFF
5. Corpus — `corpus.jsonl` 42 + `corpus_v2.jsonl` 27, `CorpusFullEvaluationTest` held-out
6. Validation — 180 tests PASS

## 2. LG3 Device Validation — Scaffold Complete, Artifact Pending

- `ENABLE_LLAMA_GUARD=false` → heuristic fallback with `modelId:fallback-heuristic`
- `downloadModel()` with Range resume, `EXPECTED_SHA256` gate, storage/memory checks
- Requires official `.pte` URL/SHA256 to activate

## 3. Download Details

- On-demand `filesDir/llama-guard/*.pte` (600–900MB), not bundled, atomic `.part` rename, SHA256 rejection, cancellation

## 4. Measurements

- Heuristic latency <5ms, LG3 expected P95 <800ms (to be benched)
- APK delta 0

## 5. Privacy

- Dual consent OFF, TFLite on-device only, no image uploads

## 6. Regression — 180 tests PASS

- `assembleDebug`, `testDebugUnitTest`, `lintDebug` PASS (actual runs)

## 7. Limitations

- Heuristic placeholder until artifact
- Telugu REVIEW uncertainty
- Fragments REVIEW per spec
