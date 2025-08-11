package io.muun.apollo.domain

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import io.muun.apollo.data.preferences.BackgroundTimesRepository
import io.muun.apollo.data.preferences.RepositoryRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
@RunWith(AndroidJUnit4::class)
class BackgroundTimesProcessorTest {

    private lateinit var context: Context
    private lateinit var backgroundTimesProcessor: BackgroundTimesProcessor
    private lateinit var backgroundTimesRepository: BackgroundTimesRepository

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        backgroundTimesRepository = BackgroundTimesRepository(context, RepositoryRegistry())
        backgroundTimesProcessor = BackgroundTimesProcessor(
            backgroundTimesRepository
        )

        backgroundTimesRepository.clear()
    }

    @After
    fun cleanUp() {
        backgroundTimesRepository.clear()
    }

    @Test
    fun saveSingleBackgroundEventWorks() {
        backgroundTimesProcessor.enterBackground()
        backgroundTimesProcessor.enterForeground()

        val bkgTimes = backgroundTimesRepository.getBackgroundTimes()
        assertThat(bkgTimes).isNotEmpty
        assertThat(bkgTimes.size).isEqualTo(1)
    }

    @Test
    fun recordForegroundBeforeGoingToBackgroundDoesntStoreAnything() {
        // E.g on first open we record enterForeground but since we didn't record enterBackground
        // we can't calculation bkgEvent duration so we ignore it.

        backgroundTimesProcessor.enterForeground()

        val bkgTimes = backgroundTimesRepository.getBackgroundTimes()
        assertThat(bkgTimes).isEmpty()
        assertThat(bkgTimes.size).isEqualTo(0)

        saveSingleBackgroundEventWorks()
    }

    @Test
    fun pruneEventListUponReachingCapLimit() {

        val capLimit = BackgroundTimesProcessor.MAX_BKG_TIMES_ARRAY_SIZE

        for (i in 1..capLimit + 20) {
            backgroundTimesProcessor.enterBackground()
            backgroundTimesProcessor.enterForeground()
        }

        val bkgTimes = backgroundTimesRepository.getBackgroundTimes()
        assertThat(bkgTimes).isNotEmpty
        // Due to an impl detail we're actually always keeping capLimit + 1 items (hehe)
        assertThat(bkgTimes.size).isEqualTo(capLimit + 1)
    }

}