package com.example.metaguardrail

import android.content.Intent
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.metaguardrail", appContext.packageName)
    }

    @Test
    fun sharedCaptionWaitsForUserCheckAndShowsRuleMatch() {
        val caption = "You are an idiot and worthless."
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.receiveSharedText(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, caption)
            )
        }

        composeTestRule.onNodeWithText("Shared text is ready. Review it, then tap Check text.").assertExists()
        composeTestRule.onNodeWithText(caption).assertExists()
        composeTestRule.onNode(hasClickAction() and hasText("Check text")).performClick()
        composeTestRule.onNodeWithText("Review Suggested").assertExists()
        composeTestRule.onNodeWithText("A local text rule matched language that can be used for harassment or bullying. Review the surrounding context.").assertExists()
    }

    @Test
    fun linkOnlyShareExplainsThatPostTextIsUnavailable() {
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.receiveSharedText(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "https://www.instagram.com/p/example/")
            )
        }

        composeTestRule.onNodeWithText(
            "This share contains a link only. Guard Rail does not open links. Paste the post text or caption here to check it."
        ).assertExists()
        composeTestRule.onNodeWithText("No Matching Rule").assertDoesNotExist()
    }
}
