package com.example.metaguardrail.monitor

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
/**
 * Factory for the full-screen warning overlay shown by [ScreenMonitoringService].
 *
 * Extracted from [ScreenMonitoringService.showWarning] for testability so the view
 * hierarchy, guidance text, and accessibility labels can be unit-tested without
 * a [android.view.WindowManager].
 *
 * The returned [View] is a vertical [LinearLayout] styled to match the app's dark theme.
 * Callers are responsible for adding it to [android.view.WindowManager] with
 * `TYPE_APPLICATION_OVERLAY` and removing it when dismissed.
 *
 * WindowInsets / navigation bar:
 * The view itself does not apply insets; callers should handle insets when laying out
 * inside WindowManager. When used as an overlay with MATCH_PARENT, apply
 * `WindowInsetsCompat.Type.navigationBars()` insets as padding so content is not
 * obscured by the gesture / 3-button nav bar. A note is included where relevant.
 */
class WarningOverlayView private constructor() {

    companion object {

        /**
         * Creates the overlay view.
         *
         * @param context Android context (prefer service or activity context).
         * @param category finding label, e.g. "sexual or suggestive imagery in image check".
         * @param onSnooze snooze for 8 seconds — caller should suppress shield and hide overlay.
         * @param onExitHome navigate to home screen — caller should start HOME intent.
         * @param onOpenSettings open safety settings — caller should launch settings destination.
         */
        fun create(
            context: Context,
            category: String,
            onSnooze: () -> Unit,
            onExitHome: () -> Unit,
            onOpenSettings: () -> Unit
        ): View {
            val guidance = getGuidance(category)

            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                // Dark scrim + card padding. Colors match app dark theme (container 0xFF151D26, bg 0xFF0C1117).
                setPadding(dp(context, 28), dp(context, 36), dp(context, 28), dp(context, 36))
                setBackgroundColor(0xF20C1117.toInt())
                isClickable = true
                isFocusable = true
                // Accessibility: overlay announces itself.
                contentDescription = "Safety warning: $category"
                // Note: to avoid covering the navigation bar, the caller should apply
                // WindowInsetsCompat.Type.navigationBars() as bottom padding when the view
                // is attached to WindowManager. Example:
                // ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                //   val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                //   v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, nav.bottom + v.paddingBottom)
                //   insets
                // }
                // Kept as comment to avoid adding androidx.core:core view compat dependency if not present
                // but documents expected handling.

                addView(TextView(context).apply {
                    text = "META GUARD RAIL"
                    textSize = 13f
                    letterSpacing = 0.08f
                    setTextColor(0xFF8DD8D0.toInt())
                    gravity = Gravity.CENTER
                    contentDescription = "Meta Guard Rail"
                }, layoutParamsFullWidthWrap())

                addView(TextView(context).apply {
                    text = "This content is covered"
                    textSize = 26f
                    setTextColor(0xFFF2F5F8.toInt())
                    gravity = Gravity.CENTER
                    setPadding(0, dp(context, 18), 0, dp(context, 12))
                }, layoutParamsFullWidthWrap())

                addView(TextView(context).apply {
                    text = "$guidance\n\nThe cover hides the screen; it does not remove the post or stop its audio."
                    textSize = 16f
                    setTextColor(0xFFCED7DF.toInt())
                    gravity = Gravity.CENTER
                    setLineSpacing(dp(context, 4).toFloat(), 1f)
                    // Guidance text is important for accessibility; ensure it is not ellipsized.
                }, layoutParamsFullWidthWrap())

                // Button 1: Snooze / continue — shows content for 8 seconds via caller.
                addView(Button(context).apply {
                    text = "Continue to next item (show for 8 sec)"
                    contentDescription = "Continue to next item, show for 8 seconds"
                    setOnClickListener { onSnooze() }
                }, layoutParamsFullWidthWrap().apply { topMargin = dp(context, 28) })

                // Button 2: Exit to home screen.
                addView(Button(context).apply {
                    text = "Exit to home screen"
                    contentDescription = "Exit to home screen"
                    setOnClickListener { onExitHome() }
                }, layoutParamsFullWidthWrap().apply { topMargin = dp(context, 8) })

                // Button 3: Open safety settings.
                addView(Button(context).apply {
                    text = "Open safety settings"
                    contentDescription = "Open safety settings"
                    setOnClickListener { onOpenSettings() }
                }, layoutParamsFullWidthWrap().apply { topMargin = dp(context, 8) })
            }
        }

        /**
         * Returns user-facing guidance for [category].
         *
         * Special-cases self-harm to provide supportive messaging; otherwise returns
         * a generic automation-limitation disclaimer.
         */
        fun getGuidance(category: String): String {
            return if (category.contains("self-harm", ignoreCase = true)) {
                "If this may be about you, reach out to someone you trust or local crisis support. " +
                    "If anyone is in immediate danger, contact local emergency services."
            } else {
                "A possible $category was found in Instagram or Facebook. This automated check can be wrong."
            }
        }

        private fun dp(context: Context, value: Int): Int =
            (value * context.resources.displayMetrics.density).toInt()

        private fun layoutParamsFullWidthWrap(): LinearLayout.LayoutParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
    }
}
