package staff.android.delivery

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeliveryJourneyTest {
    @Test fun rejectedAdditionStaysFourAcrossScreens() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            context.packageManager.getLaunchIntentForPackage(context.packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        try {
            onView(withText("Accepted quantity: 3")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText("Add 1 Tea")).perform(scrollTo(), click())
            onView(withText("Added. Cart has 4.")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText("Add 1 Tea")).perform(scrollTo(), click())
            onView(withText("Maximum quantity reached.")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText("Accepted quantity: 4")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText("Lab controls")).perform(scrollTo(), click())
            onView(withText("Revision: 1")).perform(scrollTo())
            onView(withText("Revision: 1")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withContentDescription("Open Cart")).perform(scrollTo(), click())
            onView(withText("A little everyday ritual. In your cart."))
                .perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText("Add 1 Tea")).check(doesNotExist())
            onView(withText("Accepted quantity: 4")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withContentDescription("Open Saved")).perform(scrollTo(), click())
            onView(withText("Maximum quantity reached.")).perform(scrollTo()).check(matches(isDisplayed()))
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
