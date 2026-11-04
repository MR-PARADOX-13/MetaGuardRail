package com.example.metaguardrail.monitor

import android.graphics.Bitmap
import androidx.annotation.WorkerThread

/**
 * Throttles sampled video/screen frames for analysis.
 *
 * Intended for video or Reels sampling: at most one frame every 3 seconds is
 * forwarded to [VisualContentClassifier] to keep on-device inference affordable.
 *
 * Threading: [shouldSample] is cheap but [hashBitmapSimple] allocates a small
 * 8x8 bitmap and reads pixels — call it **off the main thread** (IO / Default).
 * This class is not synchronized; confine an instance to a single background
 * handler thread or guard externally if shared across threads.
 *
 * Deduplication: [perceptualHashDedup] / [hashBitmapSimple] provide a best-effort
 * average-hash (aHash) to skip nearly-identical consecutive frames. This does
 * not guarantee perceptual uniqueness and can still produce false duplicates or
 * miss near-duplicates under heavy crop / color shift.
 */
class VideoFrameSampler {

    @Volatile
    private var lastSampleAt = 0L

    /**
     * Returns true at most once per 3000 ms.
     * Updates [lastSampleAt] on the sampling edge so callers can gate expensive work.
     *
     * @param now current time in millis, defaults to [System.currentTimeMillis] for production
     *            callers; pass a fake clock in tests.
     */
    fun shouldSample(now: Long = System.currentTimeMillis()): Boolean {
        return if (now - lastSampleAt >= 3000) {
            lastSampleAt = now
            true
        } else {
            false
        }
    }

    /** Reset throttle — useful when foreground app changes or shield is dismissed. */
    fun reset() {
        lastSampleAt = 0L
    }

    /**
     * Simple deduplication check: equal hash => likely duplicate.
     * Caller should compute hashes via [hashBitmapSimple] and compare consecutive frames.
     */
    fun perceptualHashDedup(currentHash: Int, lastHash: Int): Boolean {
        return currentHash == lastHash
    }

    /**
     * Computes a 64-bit average-hash (aHash) folded to [Int] for cheap comparison.
     *
     * Steps:
     * 1. Downscales [bitmap] to 8x8 (ignores aspect; bilinear filter).
     * 2. Converts each pixel to luma (Rec. 601: 0.299 R + 0.587 G + 0.114 B).
     * 3. Averages luma across 64 pixels.
     * 4. Sets bit i where luma >= average, building a Long, then folds to Int.
     *
     * Call off the main thread. Returns 0 for degenerate bitmaps.
     * The resize allocation is small (8x8) and the source bitmap is not recycled.
     */
    @WorkerThread
    fun hashBitmapSimple(bitmap: Bitmap): Int {
        if (bitmap.width == 0 || bitmap.height == 0) return 0
        // Small downscale; filter=true gives smoother average than nearest.
        val scaled = Bitmap.createScaledBitmap(bitmap, HASH_SIZE, HASH_SIZE, true)
        try {
            val pixels = IntArray(HASH_SIZE * HASH_SIZE)
            scaled.getPixels(pixels, 0, HASH_SIZE, 0, 0, HASH_SIZE, HASH_SIZE)

            // Compute average luma.
            var sum = 0L
            val lumas = IntArray(pixels.size)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                // Fast integer luma: 299*R + 587*G + 114*B divided by 1000
                val luma = (299 * r + 587 * g + 114 * b) / 1000
                lumas[i] = luma
                sum += luma
            }
            val avg = (sum / lumas.size).toInt()

            // Build 64-bit aHash
            var hashLong = 0L
            for (luma in lumas) {
                hashLong = (hashLong shl 1) or (if (luma >= avg) 1L else 0L)
            }
            // Fold 64-bit to 32-bit for storage/comparison: xor high and low halves.
            // Equality of Int still means equality of underlying Long in most cases;
            // collision risk is acceptable for best-effort dedup.
            return (hashLong xor (hashLong ushr 32)).toInt()
        } finally {
            // Scaled is a temporary; original bitmap ownership stays with caller.
            if (scaled != bitmap) {
                scaled.recycle()
            }
        }
    }

    companion object {
        private const val HASH_SIZE = 8
    }
}
