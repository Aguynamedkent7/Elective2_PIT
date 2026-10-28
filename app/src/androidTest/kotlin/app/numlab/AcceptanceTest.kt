package app.numlab

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** On-device versions of PLAN acceptance scripts A–D (the parts a UI test can drive). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AcceptanceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String, substring: Boolean = false) =
        rule.waitUntilAtLeastOneExists(hasText(text, substring = substring), 10_000)

    private fun home() = rule.waitUntilAtLeastOneExists(hasContentDescription("Show graph"), 10_000)

    @Test
    fun scriptA_rootsInputToGraph() {
        home()
        rule.onNodeWithContentDescription("Show graph").assertIsNotEnabled()
        rule.onNodeWithText("Type a function or tap an example to unlock the graph").assertExists()
        rule.onNodeWithText("x³ − x − 2").performClick()
        waitFor("17 iterations · last change 7.63e-6")
        rule.onAllNodesWithText("Converged").onFirst().assertExists()
        rule.onNodeWithContentDescription("1.521385").assertExists()
        rule.onNodeWithContentDescription("Show graph").assertIsEnabled().performClick()
        rule.waitUntilAtLeastOneExists(hasContentDescription("Graph of f(x). Root at x ≈ 1.521385."), 10_000)
        rule.onNodeWithContentDescription("Back").performClick()
        home()
    }

    @Test
    fun scriptA_newtonSecantAndErrors() {
        home()
        rule.onNodeWithText("x³ − x − 2").performClick()
        rule.onNodeWithText("Newton").performClick()
        waitFor("3 iterations · last change", substring = true)
        rule.onNodeWithText("Secant").performClick()
        waitFor("6 iterations · last change", substring = true)
        rule.onNodeWithText("Bisection").performClick()
        rule.onNodeWithText("ln x + x − 2").performClick()
        waitFor("17 iterations · last change", substring = true)
    }

    @Test
    fun scriptB_interpolationExample() {
        home()
        rule.onNodeWithText("Interpolate").performClick()
        rule.onNodeWithText("sin x · 4 points").performClick()
        rule.waitUntilAtLeastOneExists(hasContentDescription("0.976006"), 10_000)
        waitFor("|error| 2.15e-2")
    }

    @Test
    fun scriptD_historyCardsSwipeDeleteUndo() {
        home()
        rule.onNodeWithContentDescription("History").performClick()
        waitFor("Runs are saved when you open the graph or tap Save. Swipe a card left to delete.")
        rule.onAllNodesWithText("Sample").onFirst().assertExists()
        rule.onAllNodesWithText("Table").onFirst().performClick()
        waitFor("Iter".uppercase())
        rule.onAllNodesWithText("f(x) = x^3 - x - 2").onFirst().performTouchInput { swipeLeft() }
        waitFor("Run deleted")
        rule.onNodeWithText("Undo").performClick()
        waitFor("f(x) = x^3 - x - 2")
    }
}
