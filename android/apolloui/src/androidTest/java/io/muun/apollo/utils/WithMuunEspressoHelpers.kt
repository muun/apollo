package io.muun.apollo.utils

import android.app.Activity
import android.content.Context
import android.text.SpannableString
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.muun.apollo.presentation.ui.utils.OS
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.hamcrest.Matchers.not


interface WithMuunEspressoHelpers {

    val context: Context    // "Real" app context

    /**
     * Helper method to click a ClickableSpan matching EXACTLY the target text.
     */
    fun clickClickableSpan(@StringRes targetStringResId: Int): ViewAction {
        return clickClickableSpan(context.getString(targetStringResId))
    }

    /**
     * Helper method to click a ClickableSpan matching EXACTLY the target text.
     */
    private fun clickClickableSpan(targetTextToClick: CharSequence): ViewAction {
        return internalClickClickableSpan { _, spanText ->
            spanText == targetTextToClick
        }
    }

    /**
     * Helper method to click a ClickableSpan based on index (first, second, third clickableSpan)
     * inside the TextView.
     */
    fun clickClickableSpanByOrder(target: Int): ViewAction {  // Of the peaky blinders!!!
        return internalClickClickableSpan { index, _ ->
            index == target
        }
    }

    private fun internalClickClickableSpan(predicate: (Int, String) -> Boolean): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return Matchers.instanceOf(TextView::class.java)
            }

            override fun getDescription(): String {
                return "clicking on a ClickableSpan"
            }

            override fun perform(uiController: UiController, view: View) {
                val textView = view as TextView
                val spannableString = textView.text as SpannableString

                if (spannableString.isEmpty()) {
                    // TextView is empty, nothing to do
                    throw NoMatchingViewException.Builder()
                        .includeViewHierarchy(true)
                        .withRootView(textView)
                        .build()
                }

                // Get the links inside the TextView and check if we find textToClick
                val spans =
                    spannableString.getSpans(0, spannableString.length, ClickableSpan::class.java)
                if (spans.isNotEmpty()) {
                    for ((index, span) in spans.withIndex()) {
                        val start = spannableString.getSpanStart(span)
                        val end = spannableString.getSpanEnd(span)
                        val spanText = spannableString.subSequence(start, end).toString()
                        if (predicate(index, spanText)) {
                            span.onClick(textView)
                            return
                        }
                    }
                }

                // textToClick not found in TextView
                throw NoMatchingViewException.Builder()
                    .includeViewHierarchy(true)
                    .withRootView(textView)
                    .build()

            }
        }
    }

    fun getCurrentActivity(): Activity? {
        var currentActivity: Activity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            run {
                currentActivity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).elementAtOrNull(0)
            }
        }
        return currentActivity
    }

    fun checkToastDisplayed(resId: Int) {
        if (OS.supportsEspressoToastDetection()) {
            checkToastDisplayed(getCurrentActivity(), resId)
        }
        // Else do nothing :'( (We can't reliably detect toasts)
    }

    // Note: Doesn't work in starting Android 12 :shrug:
    private fun checkToastDisplayed(activity: Activity?, @StringRes resId: Int) {
        onView(withText(resId)).inRoot(withDecorView(not(activity!!.window.decorView)))
            .check(matches(isDisplayed()))
    }
}