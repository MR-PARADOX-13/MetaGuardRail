# Meta Model Evaluation — Llama Guard 3 / 4, Prompt Guard 2, ExecuTorch

## Llama Guard 3 1B INT4 (On-Device Primary)

- **Model:** `meta-llama/Llama-Guard-3-1B` (Hugging Face gated, Meta Llama 3 Community License)
- **INT4 quantization:** Provided via `meta-llama/Llama-Guard-3-1B-INT4` ExecuTorch export (doc: https://github.com/meta-llama/PurpleLlama/tree/main/Llama-Guard3/1B)
- **Runtime:** ExecuTorch Android `org.pytorch:executorch` AAR + `Module.load(.pte)` + SentencePiece tokenizer (BPE). `compileSdk 37 / minSdk 24` compatible; add via `gradle/libs.versions.toml` only after validation.
- **Target bench (Pixel 7):** P95 inference <800ms for 512-token context, RAM spike <900MB. Fallback to keyword-only if unavailable.
- **APK policy:** `.pte` + tokenizer **not bundled** — on-demand download to `filesDir/llama-guard/` with SHA256 + resume (same pattern as Vosk). Feature flag `BuildConfig.ENABLE_LLAMA_GUARD` gates loading.
- **Licensing:** Requires Hugging Face license acceptance; on-device use inherits Llama 3 Community License.

## Llama Guard 4 12B (Optional Cloud)

- **Model:** `meta-llama/Llama-Guard-4-12B` multimodal text+image (https://github.com/meta-llama/PurpleLlama/tree/main/Llama-Guard4)
- **Deployment:** Server-side only — never APK-bundled. Reuses existing `BackendProxyConfig` / Muse Spark proxy shape with `model = llama-guard-4-12b`. Gated by `cloudTextAnalysisEnabled` consent (OFF by default).
- **Decision:** Keep Muse Spark as secondary until Llama Guard 4 cloud verified; add LG4 model ID only after backend validation.

## Prompt Guard 2

- **Scope:** Jailbreak / prompt-injection detection only. Repo: https://github.com/meta-llama/PurpleLlama/tree/main/Llama-Prompt-Guard-2
- **Policy:** Separate `PromptGuardResult(isInjection, confidence)`; do NOT conflate with content safety severity.

## PurpleLlama / LlamaFirewall

- Policy templates & evaluation harnesses only — no code import. Taxonomy customization via `llama-cookbook` system prompts.

## ExecuTorch Android Compatibility

- AAR `executorch-android` supports `minSdk 24`, `compileSdk 34+` (we use 37). Native libs `arm64-v8a, x86_64`. Add only after Phase 6 evaluation confirms no regression (baseline stays green).
