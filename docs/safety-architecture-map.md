# Safety Architecture Map — MetaGuardRail Baseline (Pre-Upgrade)

## End-to-End Pipeline

```
┌──────────────────────────────────────────────────────────────────────┐
│  CAPTURE                                                            │
│  ScreenMonitoringService (MediaProjection → VirtualDisplay →          │
│    ImageReader @1200ms, UsageStatsManager package gating             │
│    [Instagram, FB, Chrome])  +  AudioPlaybackMonitor (Vosk)          │
│         │                                                            │
│  OCR    ├─ ML Kit TextRecognition (Latin) + TeluguTextRecognizer    │
│         │   (Tesseract4Android / tel.traineddata)                   │
│         │   combinedText = latin + telugu (join \n)                 │
│         │   deduplication: contentHash 5s window / 100-entry LRU    │
│         │                                                            │
│  LOCAL  ├─ ContentSafetyAnalyzer.analyzeContent(text, settings)      │
│  ENGINE │   keyword dictionaries per SafetyCategory (8 categories)   │
│         │   containsAnyTerm() + normalizeForMatching() → isFlagged   │
│         │   → severity / shouldShield / categoryMatched              │
│         │                                                            │
│  IMAGE  ├─ VisualContentClassifier (moderation/nsfw.tflite.deflate)  │
│         │   224x224 center-crop, TFLite CPU, threshold 0.70          │
│         │   (hentai+porn+sexy) → DetectionSource.VISION_MODEL        │
│         │                                                            │
│  CLOUD  ├─ MuseSparkRepository.analyzeWithMuse(text, settings)      │
│  (gated)│   POST chat/completions via BackendProxyConfig             │
│         │   model=muse-spark-open-1026, gated by                     │
│         │   isCloudTextAnalysisAllowed() + isBackendConfigured()     │
│         │   Manual Analyze remains LOCAL-only                        │
│         │                                                            │
│  FINDING├─ updateFinding(text|image|audio|muse) → LiveProtectionState│
│         │   flagged(label, source) / clearFinding() / flagged overlay│
│         │          │                                                 │
│         │   MainActivity.LaunchedEffect(finding) →                  │
│         │     ActivityRepository.addEvent(ActivityEvent)             │
│         │          │                                                 │
│         │     DataStore prefs "meta_guard_rail_activity"             │
│         │     MAX_EVENTS=50 FIFO                                     │
│         └──────────┬─────────────────────────────────────────────────┘
│                    ↓                                                 │
│  UI/ HISTORY  Home (Hero + DailySummary + TrendAnalytics + Recent)  │
│               Activity (filters: severity/category/source, detail,   │
│                         export SAF CreateDocument, share FileProvider│
│                         + Category/Source FilterChips)                │
│               Analyze (text/link/image local checks)                │
│               Rules (modes BALANCED/STRICT/CUSTOM, 7 categories)    │
│               Settings (Privacy & Data Controls, cloud consent OFF)  │
│                                                                    │
│  NOTIFY  IncidentNotifier (HIGH→IMPORTANCE_HIGH, WARNING→DEFAULT)  │
│          persistence: notified_incident_ids stringSet               │
│          title/body sanitized, VISIBILITY_PRIVATE, dedup + prime   │
└──────────────────────────────────────────────────────────────────────┘
```

## Keyword Dictionaries

| Category | Terms (sample) | Severity on hit |
|----------|----------------|-----------------|
| VULGAR_LANGUAGE | fuck, shit, bitch, ... (EN+TE) | REVIEW |
| SELF_HARM | suicide, want to die, ... | WARNING (supportive) |
| SOLICITATION_GROOMING | keep secret, don't tell parents, ... | HIGH_RISK |
| HARASSMENT | idiot, kill yourself, ... | REVIEW |
| SCAMS | free crypto, guaranteed return, ... | WARNING |
| ADULT_CONTENT | porn, nude, onlyfans, ... | WARNING (if no exemption) |
| DISTRESSING_CONTENT | gore, massacre, beheading, ... | WARNING |
| Custom | user-defined, max 5 | REVIEW |

All use `Regex("(?<![\\p{L}\\p{N}])term(?![\\p{L}\\p{N}])")` word-boundary matching.

## ActivityEvent Generation

- **Live:** `MainActivity.LaunchedEffect(protectionStatus.finding)` + dedup `title==Live: f && <5s`
- **Manual:** `AnalyzeScreen` Check text → `onAddEvent(ActivityEvent(title=text.take(80), category=label, severity, source, explanation))`

## DataStore

- `meta_guard_rail_activity` key `activity_events_json` JSONArray manual org.json
- `meta_guard_rail_preferences` keys: safety_mode, speech_language, category_*, custom_categories_json, theme_mode, notify_high_risk/warning, notified_ids, cloud_text_analysis_enabled

## Tests

- `ContentSafetyAnalyzerExpandedTest.kt` — per-category keyword hits
- `LinkSafetyAnalyzerTest.kt`
- `MuseSparkRepositoryTest.kt` — MockWebServer, consent OFF/ON
- `MuseSparkRepositoryTest_patch.kt` — extra consent coverage
- No corpus evaluation yet (Phase 6 to add)

## 5 Tabs

Home | Activity | Analyze | Rules | Settings  (GuardRailNav.kt, MainActivity Scaffold)
