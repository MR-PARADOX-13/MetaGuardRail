package com.example.metaguardrail.data

import java.text.Normalizer
import java.util.Locale

data class AnalysisResult(
    val isFlagged: Boolean,
    val riskLabel: String,
    val categoryMatched: SafetyCategory?,
    val explanation: String,
    val isSupportive: Boolean = false,
    val severity: Severity = if (isFlagged) Severity.REVIEW else Severity.SAFE,
    val confidence: Float? = null,
    val source: DetectionSource = DetectionSource.LOCAL_RULE,
    val recommendedAction: RecommendedAction = RecommendedAction.fromSeverity(if (isFlagged) Severity.REVIEW else Severity.SAFE),
    val shouldShield: Boolean = isFlagged && !isSupportive && (severity == Severity.WARNING || severity == Severity.HIGH_RISK)
)

class ContentSafetyAnalyzer {

    fun analyzeContent(text: String, settings: SafetySettings): AnalysisResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return AnalysisResult(
                isFlagged = false,
                riskLabel = "No Content",
                categoryMatched = null,
                explanation = "Enter text above to test safety analysis.",
                severity = Severity.SAFE,
                source = DetectionSource.LOCAL_RULE,
                recommendedAction = RecommendedAction.NONE,
                shouldShield = false
            )
        }

        val lower = normalizeForMatching(trimmed)

        // 1. Contextual Exemptions (Educational, Medical, LGBTQ+, ordinary body talk)
        val educationalOrMedicalKeywords = listOf(
            "education", "educational", "biology", "anatomy", "medical", "doctor", "clinical",
            "therapy", "therapist", "mental health", "counseling", "sex education", "health class",
            "support group", "recovery",
            "లైంగిక విద్య", "వైద్య సమాచారం", "ఆరోగ్య విద్య", "చికిత్స", "కౌన్సెలింగ్"
        )
        val hasExemptionContext = educationalOrMedicalKeywords.any { lower.contains(it) }

        if (settings.enabledCategories.contains(SafetyCategory.VULGAR_LANGUAGE)) {
            val vulgarTerms = listOf(
                "fuck", "fucking", "motherfucker", "shit", "bullshit", "bitch", "bastard",
                "asshole", "dickhead", "dick", "piss off", "wtf", "cunt", "slut", "whore",
                "tits", "boobs", "horny", "blowjob", "handjob", "dildo", "sexting",
                "దెంగు", "దెంగ", "మొడ్డ", "లంజ", "బూతు", "దరిద్రుడు", "వెధవ",
                "denge", "modda", "lanja", "boothu"
            )
            if (containsAnyTerm(lower, vulgarTerms)) {
                return AnalysisResult(
                    isFlagged = true,
                    riskLabel = "Review Suggested",
                    categoryMatched = SafetyCategory.VULGAR_LANGUAGE,
                    explanation = "A local text rule matched profanity. Review the surrounding context; slang, quotations, and reclaimed language can be misread.",
                    severity = Severity.REVIEW,
                    source = DetectionSource.LOCAL_RULE,
                    recommendedAction = RecommendedAction.REVIEW_CONTENT,
                    shouldShield = false
                )
            }
        }

        // 2. Self-Harm Check
        if (settings.enabledCategories.contains(SafetyCategory.SELF_HARM)) {
            val selfHarmKeywords = listOf(
                "suicide", "end my life", "want to die", "hurt myself", "cutting myself", "overdose",
                "ఆత్మహత్య", "చనిపోవాలనుంది", "నన్ను నేను చంప", "నాకు నేను హాని", "అధిక మోతాదు",
                // --- Expanded suicide-related references (added, originals preserved above) ---
                "suicidal thoughts", "suicidal feelings", "wanting to die", "I want to end my life",
                "I don't want to live", "I don't want to live anymore", "I wish I were dead",
                "thinking about suicide", "planning to end my life", "life is not worth living",
                "no reason to live", "better off dead", "ending everything", "take my own life",
                "I feel like dying", "I cannot continue living",
                // --- Expanded self-injury-related references ---
                "self-harm", "self-injury", "hurting myself", "harm myself", "harming myself",
                "injure myself", "burn myself", "thoughts of self-harm", "urge to hurt myself",
                "I might hurt myself", "I want to punish myself", "I deserve pain",
                "I keep hurting myself", "I want to injure myself",
                // --- Expanded overdose and immediate harm references ---
                "take too many pills", "I took too many pills", "I have hurt myself",
                "I already harmed myself", "I might overdose", "I am afraid I will hurt myself",
                "I have taken an overdose",
                // --- Expanded Telugu references ---
                "ఆత్మహత్య ఆలోచనలు", "చనిపోవాలని ఉంది", "నేను చనిపోవాలనుకుంటున్నాను",
                "నేను ఇక బతకాలని అనుకోవడం లేదు", "నా జీవితాన్ని ముగించాలనుకుంటున్నాను",
                "నేను చనిపోతే బాగుంటుంది", "నాకు జీవించాలని అనిపించడం లేదు",
                "నన్ను నేను గాయపరుచుకోవాలనుకుంటున్నాను", "నాకు నేనే హాని చేసుకోవాలనిపిస్తోంది",
                "నన్ను నేను కోసుకోవాలనుకుంటున్నాను", "నన్ను నేను బాధపెట్టుకోవాలనుకుంటున్నాను",
                "నేను నన్ను నేను గాయపరుచుకున్నాను", "ఎక్కువ మాత్రలు తీసుకున్నాను",
                "నేను ఆత్మహత్య గురించి ఆలోచిస్తున్నాను", "నాకు బతకాలని లేదు",
                "నేను చనిపోవాలని అనుకుంటున్నాను",
                // --- Expanded Romanized Telugu references ---
                "Nenu chachipovalani anukuntunnanu", "Nenu inka brathakalani anukovatledu",
                "Naku chachipovalani undi", "Nenu aatmahatya gurinchi alochistunnanu",
                "Naku nene haani chesukovalani undi", "Nenu nannu nene gayaparuchukovalani anukuntunnanu",
                "Nenu nannu nene kosukovalani anukuntunnanu", "Nenu chanipothe baguntundi",
                "Naaku jeevinchalani anipinchatledu", "Nenu ekkuva tablets teesukunnanu",
                "Naku brathakali anipinchadam ledu", "Nenu brathakalanukovatledu",
                "Naa jeevithanni mugించాలనుకుంటunnanu"
            )
            if (containsAnyTerm(lower, selfHarmKeywords)) {
                return AnalysisResult(
                    isFlagged = true,
                    riskLabel = "Supportive Resource Available",
                    categoryMatched = SafetyCategory.SELF_HARM,
                    explanation = "This text includes a self-harm phrase. If this is about you, consider reaching out to someone you trust or a crisis-support service in your region. If there is immediate danger, contact local emergency services.",
                    isSupportive = true,
                    severity = Severity.WARNING,
                    source = DetectionSource.LOCAL_RULE,
                    recommendedAction = RecommendedAction.SEEK_SUPPORT,
                    shouldShield = false
                )
            }
        }

        // 3. Solicitation & Grooming Indicators
        if (settings.enabledCategories.contains(SafetyCategory.SOLICITATION_GROOMING)) {
            val groomingKeywords = listOf(
                "keep this secret", "don't tell your parents", "meet me alone", "send photos", "secret chat", "age is just a number",
                "మీ అమ్మానాన్నలకు చెప్పొద్దు", "ఎవరికీ చెప్పొద్దు", "రహస్యంగా కలుద్దాం", "ఒంటరిగా కలువు",
                "ఫోటోలు పంపు", "నగ్న ఫోటోలు పంపు", "పిల్లలకు చెప్పొద్దు",
                "amma nannaki cheppoddu", "evariki cheppoddu", "rahasyanga kaluddam",
                "ontariga kaluddam", "nagna photolu pampu", "photos pampu"
            )
            if (containsAnyTerm(lower, groomingKeywords)) {
                return AnalysisResult(
                    isFlagged = true,
                    riskLabel = "Possible Concern (Requires Context)",
                    categoryMatched = SafetyCategory.SOLICITATION_GROOMING,
                    explanation = "Flagged as a potential grooming indicator (secrecy or inappropriate contact request). This is a possible concern for review, not a proven accusation.",
                    severity = Severity.HIGH_RISK,
                    source = DetectionSource.LOCAL_RULE,
                    recommendedAction = RecommendedAction.SHOW_SHIELD,
                    shouldShield = true
                )
            }
        }

        // 4. Harassment & Bullying
        if (settings.enabledCategories.contains(SafetyCategory.HARASSMENT)) {
            val harassmentKeywords = listOf(
                "idiot", "loser", "kill yourself", "hate you", "worthless", "ugly", "harass", "attack",
                "చచ్చిపో", "నిన్ను చంపేస్తా", "పనికిరానివాడివి", "మూర్ఖుడు", "నీచుడు"
            )
            if (containsAnyTerm(lower, harassmentKeywords)) {
                return AnalysisResult(
                    isFlagged = true,
                    riskLabel = "Review Suggested",
                    categoryMatched = SafetyCategory.HARASSMENT,
                    explanation = "A local text rule matched language that can be used for harassment or bullying. Review the surrounding context.",
                    severity = Severity.REVIEW,
                    source = DetectionSource.LOCAL_RULE,
                    recommendedAction = RecommendedAction.REVIEW_CONTENT,
                    shouldShield = false
                )
            }
        }

        // 5. Scams & Manipulation
        if (settings.enabledCategories.contains(SafetyCategory.SCAMS)) {
            val scamKeywords = listOf(
                "free crypto", "click this link to claim", "send money", "guaranteed return", "lottery winner", "urgent wire", "phishing",
                "ఉచిత క్రిప్టో", "లాటరీ గెలిచారు", "డబ్బు పంపండి", "లింక్ క్లిక్ చేయండి", "ఖచ్చితమైన లాభం",
                "uchita crypto", "lottery gelicharu", "dabbu pampandi", "link click cheyyandi"
            )
            if (containsAnyTerm(lower, scamKeywords)) {
                return AnalysisResult(
                    isFlagged = true,
                    riskLabel = "Review Suggested",
                    categoryMatched = SafetyCategory.SCAMS,
                    explanation = "A local text rule matched language sometimes used in scams or manipulative requests. Review the link and context.",
                    severity = Severity.WARNING,
                    source = DetectionSource.LOCAL_RULE,
                    recommendedAction = RecommendedAction.SHOW_WARNING,
                    shouldShield = true
                )
            }
        }

        // 6. Adult Content
        if (settings.enabledCategories.contains(SafetyCategory.ADULT_CONTENT) && !hasExemptionContext) {
            val adultKeywords = listOf(
                "explicit", "porn", "pornography", "nude", "nudity", "xxx", "sexual content",
                "sexually explicit", "sexually suggestive", "sexualized", "sexualised", "suggestive",
                "erotic", "lewd", "sexy", "thirst trap", "lingerie", "topless", "nsfw",
                "adult video", "onlyfans", "only fans", "striptease", "అశ్లీల", "పోర్న్", "నగ్న",
                "లైంగిక దృశ్యాలు", "శృంగార వీడియో", "అశ్లీల వీడియో", "సెక్సీ", "బూతు వీడియో",
                "నగ్న ఫోటో", "అర్ధనగ్న", "కామోద్దీపక", "శృంగార చిత్రం",
                "ashleela", "nagna photolu", "nagna video", "srungara video"
            )
            if (containsAnyTerm(lower, adultKeywords)) {
                return AnalysisResult(
                    isFlagged = true,
                    riskLabel = "Review Suggested",
                    categoryMatched = SafetyCategory.ADULT_CONTENT,
                    explanation = "A local text rule matched a sexual or suggestive term. Review the context; this rule cannot reliably understand intent.",
                    severity = Severity.WARNING,
                    source = DetectionSource.LOCAL_RULE,
                    recommendedAction = RecommendedAction.SHOW_SHIELD,
                    shouldShield = true
                )
            }
        }

        // 7. Custom user categories (keyword match on custom name)
        for (custom in settings.customCategories) {
            if (containsAnyTerm(lower, listOf(custom.lowercase()))) {
                return AnalysisResult(
                    isFlagged = true,
                    riskLabel = "Review Suggested",
                    categoryMatched = null,
                    explanation = "Matched your custom category \"$custom\". Review the surrounding context.",
                    severity = Severity.REVIEW,
                    source = DetectionSource.LOCAL_RULE,
                    recommendedAction = RecommendedAction.REVIEW_CONTENT,
                    shouldShield = false
                )
            }
        }

        // 8. Distressing Content
        if (settings.enabledCategories.contains(SafetyCategory.DISTRESSING_CONTENT)) {
            val distressingKeywords = listOf(
                "gore", "graphic violence", "bloody massacre", "mutilation", "beheading",
                "bloodbath", "dismemberment", "torture", "stabbing", "mass shooting",
                "shot dead", "violent attack", "graphic injury", "హింస", "రక్తపాతం", "మారణకాండ",
                "హత్య", "కత్తితో పొడిచ", "కాల్చి చంప", "తీవ్ర గాయాలు", "అవయవాలు నరిక", "చిత్రహింస",
                "himsaa", "rakthapaatam", "maranakaandam", "hatya", "kattitho podicha",
                "teevra gayalu", "avayavaalu narika"
            )
            if (containsAnyTerm(lower, distressingKeywords)) {
                return AnalysisResult(
                    isFlagged = true,
                    riskLabel = "Review Suggested",
                    categoryMatched = SafetyCategory.DISTRESSING_CONTENT,
                    explanation = "A local text rule matched a disturbing-content term. This text-only demo cannot assess imagery or reliably understand context.",
                    severity = Severity.WARNING,
                    source = DetectionSource.LOCAL_RULE,
                    recommendedAction = RecommendedAction.SHOW_WARNING,
                    shouldShield = true
                )
            }
        }

        return AnalysisResult(
            isFlagged = false,
            riskLabel = "No Matching Rule",
            categoryMatched = null,
            explanation = "No enabled local text rule matched. This is not a guarantee that the content is safe.",
            severity = Severity.SAFE,
            source = DetectionSource.LOCAL_RULE,
            recommendedAction = RecommendedAction.NONE,
            shouldShield = false
        )
    }

    private fun containsAnyTerm(text: String, terms: List<String>): Boolean {
        val normalizedText = normalizeForMatching(text)
        return terms.any { originalTerm ->
            val term = normalizeForMatching(originalTerm)
            if (term.any { it in '\u0C00'..'\u0C7F' }) {
                normalizedText.contains(term)
            } else {
                Regex("(?<![\\p{L}\\p{N}])${Regex.escape(term)}(?![\\p{L}\\p{N}])")
                    .containsMatchIn(normalizedText)
            }
        }
    }

    private fun normalizeForMatching(text: String): String = Normalizer
        .normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .filterNot { it in '\u200B'..'\u200F' || it == '\uFEFF' }
        .replace(Regex("\\s+"), " ")
        .trim()
}
