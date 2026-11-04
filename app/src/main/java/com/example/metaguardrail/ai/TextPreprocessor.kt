package com.example.metaguardrail.ai

import java.text.BreakIterator
import java.util.Locale

/**
 * Conservative text preprocessing for context-aware safety.
 * Sentence segmentation, context window, negation/quotation helpers.
 */
object TextPreprocessor {

    private val NEGATION_WORDS = setOf("not", "no", "never", "n't", "don't", "doesn't", "didn't", "won't", "cannot", "can't", "isn't", "without")
    private val EDUCATIONAL_MARKERS = setOf("education", "educational", "research", "study", "history", "news reported", "movie contains", "fiction", "novel", "prevention", "social problem", "workshop", "class")
    private val FICTIONAL_MARKERS = setOf("movie", "film", "novel", "fiction", "character", "scene", "entertainment")
    private val QUOTE_PATTERN = Regex("[\"\u201C\u201D\u2018\u2019].*?[\"\u201C\u201D\u2018\u2019]", RegexOption.DOT_MATCHES_ALL)

    fun segmentSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val iter = BreakIterator.getSentenceInstance(Locale.ENGLISH).apply { setText(text) }
        val sentences = mutableListOf<String>()
        var start = iter.first()
        var end = iter.next()
        while (end != BreakIterator.DONE) {
            val s = text.substring(start, end).trim()
            if (s.isNotEmpty()) sentences.add(s)
            start = end
            end = iter.next()
        }
        return if (sentences.isEmpty()) listOf(text.trim()) else sentences
    }

    fun buildContextWindow(sentences: List<String>, targetIndex: Int, radius: Int = 2): String {
        if (sentences.isEmpty()) return ""
        val lo = (targetIndex - radius).coerceAtLeast(0)
        val hi = (targetIndex + radius).coerceAtMost(sentences.lastIndex)
        return sentences.subList(lo, hi + 1).joinToString(" ")
    }

    fun hasNegation(sentence: String): Boolean {
        val lower = sentence.lowercase(Locale.ROOT)
        return NEGATION_WORDS.any { lower.contains(it) }
    }

    fun hasQuotation(sentence: String): Boolean = QUOTE_PATTERN.containsMatchIn(sentence)

    fun hasEducationalContext(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return EDUCATIONAL_MARKERS.any { lower.contains(it) }
    }

    fun hasFictionalContext(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return FICTIONAL_MARKERS.any { lower.contains(it) }
    }

    fun isHypothetical(sentence: String): Boolean {
        val lower = sentence.lowercase(Locale.ROOT)
        return lower.contains("if ") || lower.contains("would ") || lower.contains("hypothetical") || lower.contains("what if")
    }

    fun isTelugu(text: String): Boolean = text.any { it in '\u0C00'..'\u0C7F' }
}
