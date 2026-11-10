package io.muun.apollo.presentation

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.muun.apollo.data.di.DataComponent
import io.muun.apollo.data.net.NetworkStatusProviderImpl
import io.muun.apollo.domain.libwallet.errors.BackgroundActionsRunInterruptedError
import io.muun.apollo.domain.libwallet.errors.BackgroundActionsRunInterruptedError.Reason
import io.muun.apollo.domain.model.background_actions.ExecutionContext
import io.muun.apollo.osmodules.background_actions.BACKGROUND_ACTIONS_WORK_NAME
import io.muun.apollo.osmodules.background_actions.BackgroundActionsScheduler
import io.muun.apollo.osmodules.background_actions.RUN_NOW_TAG
import io.muun.apollo.presentation.app.ApolloApplication
import io.muun.apollo.utils.SystemCommand
import io.muun.common.model.SessionStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Test
import org.threeten.bp.Duration
import java.util.UUID

private const val ONE_HOUR_MILLIS = 3_600_000L
private const val NINETY_MINUTES_MILLIS = 5_400_000L
private const val SETTLE_TIMEOUT_MILLIS = 75_000L
// Covers the network callback, the hop to the background executor and the WorkManager write.
private const val RUN_REQUEST_MILLIS = 500L

open class BackgroundActionsTest : BaseInstrumentationTest() {

    private var networkDisabled = false

    @After
    fun restoreNetwork() {
        if (networkDisabled) {
            enableNetwork()
        }
    }

    @Test
    fun test_01_run_background_actions_on_foreground_wake() {
        logInInBackground()
        val pending = schedulePendingWake()

        moveAppToForeground()

        assertForegroundRunReplaced(pending)
    }

    @Test
    fun test_02_entering_the_foreground_offline_runs_background_actions_once_online() {
        logInInBackground()
        val pending = schedulePendingWake()
        disableNetwork()

        moveAppToForeground()

        // Nothing runs offline, so there is no signal to wait for.
        Thread.sleep(RUN_REQUEST_MILLIS)
        assertPendingWakeKept(pending)

        enableNetwork()

        assertForegroundRunReplaced(pending)
    }

    @Test
    fun test_03_a_background_wake_runs_background_actions() {
        logIn()
        moveAppToBackground()

        scheduler().scheduleWake(0)
        val wake = requests().single { !it.state.isFinished }.id

        waitUntil("The background wake did not run") {
            workInfo(wake)?.state?.isFinished == true
        }
        assertThat(workInfo(wake)?.state).isEqualTo(WorkInfo.State.SUCCEEDED)
        assertThat(armedWake()).isNotEqualTo(wake)
    }

    @Test
    fun test_04_reset_data_cancels_the_pending_wake() {
        logIn()
        val pending = armedWake()

        // Logout cancels all work before ResetData, which would hide what ResetData does.
        dataComponent().libwalletClient().resetData()

        assertThat(workInfo(pending)?.state).isEqualTo(WorkInfo.State.CANCELLED)
    }

    @Test
    fun test_05_leaving_the_foreground_before_getting_online_drops_the_run() {
        logInInBackground()
        val pending = schedulePendingWake()
        disableNetwork()

        moveAppToForeground()
        moveAppToBackground()

        enableNetwork()

        // A run still waiting would be requested as soon as the network validates.
        Thread.sleep(RUN_REQUEST_MILLIS)
        assertPendingWakeKept(pending)
    }

    @Test
    fun test_06_logging_in_after_logging_out_runs_background_actions() {
        logIn()
        logOut()
        val pending = schedulePendingWake()

        logIn()

        assertForegroundRunReplaced(pending)
    }

    @Test
    fun test_07_a_cancelled_signal_interrupts_the_run_as_cancelled() {
        val cancellation = CancellationSignal()
        cancellation.cancel()

        assertThatThrownBy {
            dataComponent().libwalletClient()
                .runDueBackgroundActions(
                    ExecutionContext.FOREGROUND,
                    Duration.ofMinutes(1),
                    cancellation
                )
        }.isInstanceOfSatisfying(BackgroundActionsRunInterruptedError::class.java) {
            assertThat(it.reason).isEqualTo(Reason.CANCELLED)
        }
    }

    @Test
    fun test_08_a_zero_timeout_interrupts_the_run_as_timeout() {
        assertThatThrownBy {
            dataComponent().libwalletClient()
                .runDueBackgroundActions(
                    ExecutionContext.FOREGROUND,
                    Duration.ZERO,
                    CancellationSignal()
                )
        }.isInstanceOfSatisfying(BackgroundActionsRunInterruptedError::class.java) {
            assertThat(it.reason).isEqualTo(Reason.TIMEOUT)
        }
    }

    /**
     * Store a logged in session, and wait for the run that logging in with the app in the
     * foreground requests. The session is all a run looks at, so there is no signup.
     */
    private fun logIn() {
        val earlierRequests = requests().map { it.id }

        storeLoggedInSession()

        waitUntil("The run after logging in did not finish") {
            requests().any { it.id !in earlierRequests && it.state.isFinished }
        }
    }

    /**
     * Store a logged in session with the app in the background, so entering the foreground is
     * the session's first run.
     */
    private fun logInInBackground() {
        moveAppToBackground()
        storeLoggedInSession()
    }

    private fun storeLoggedInSession() {
        dataComponent().authRepository().storeSessionStatus(SessionStatus.LOGGED_IN)
        dataComponent().userRepository().storeInitialSyncCompleted()
        assertThat(dataComponent().userActions().isLoggedIn).isTrue
    }

    private fun logOut() {
        dataComponent().logoutActions().uncheckedDestroyWalletForUiTests()
    }

    private fun schedulePendingWake(): UUID {
        // A run requested before the test started would replace the wake on its own.
        waitUntilNoPendingRequests()

        scheduler().scheduleWake(ONE_HOUR_MILLIS)
        return requests().single { it.state == WorkInfo.State.ENQUEUED }.id
    }

    /**
     * Wait for the background wake the last run asked for. Libwallet's static functions make
     * every run with a wallet ask for one, at least 90 minutes out.
     */
    private fun armedWake(): UUID {
        waitUntil("The run did not ask for a background wake") {
            val request = requests().singleOrNull { !it.state.isFinished }
            request?.state == WorkInfo.State.ENQUEUED && RUN_NOW_TAG !in request.tags
        }
        val wake = requests().single { !it.state.isFinished }
        assertThat(wake.initialDelayMillis).isGreaterThanOrEqualTo(NINETY_MINUTES_MILLIS)
        return wake.id
    }

    private fun assertPendingWakeKept(pending: UUID) {
        val request = requests().single { !it.state.isFinished }
        assertThat(request.id).isEqualTo(pending)
        assertThat(request.state).isEqualTo(WorkInfo.State.ENQUEUED)
    }

    /**
     * Wait for the foreground run that replaced the pending wake to finish, and check it
     * succeeded.
     */
    private fun assertForegroundRunReplaced(pending: UUID) {
        waitUntil("The foreground run did not finish") {
            requests().any { RUN_NOW_TAG in it.tags && it.state.isFinished }
        }
        val foreground = requests().single { RUN_NOW_TAG in it.tags }
        assertThat(foreground.state).isEqualTo(WorkInfo.State.SUCCEEDED)
        assertThat(workInfo(pending)).isNull()
        armedWake()
    }

    private fun moveAppToBackground() {
        device.pressHome()
        waitUntil("The app did not move to the background") {
            !processState().isAtLeast(Lifecycle.State.STARTED)
        }
    }

    private fun moveAppToForeground() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        context.startActivity(requireNotNull(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitUntil("The app did not move to the foreground") {
            processState().isAtLeast(Lifecycle.State.STARTED)
        }
    }

    private fun processState(): Lifecycle.State {
        return ProcessLifecycleOwner.get().lifecycle.currentState
    }

    private fun waitUntilNoPendingRequests() {
        waitUntil("Background actions requests did not settle") {
            requests().all { it.state.isFinished }
        }
    }

    private fun disableNetwork() {
        networkDisabled = true
        SystemCommand.disableNetwork()
        // The default network is briefly null while it moves from wifi to mobile data, so wait
        // until no network has internet. allNetworks is deprecated, but nothing else lists them
        // synchronously.
        waitUntil("Network did not turn off") {
            @Suppress("DEPRECATION")
            val networks = connectivityManager().allNetworks
            networks.none {
                connectivityManager().getNetworkCapabilities(it)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            }
        }
    }

    private fun enableNetwork() {
        val networkStatusProvider = NetworkStatusProviderImpl(context)

        // Validated, as a run waiting for the network sees it.
        SystemCommand.enableNetwork()
        waitUntil("Network did not turn on") {
            networkStatusProvider.isConnected()
        }
        networkDisabled = false
    }

    private fun waitUntil(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + SETTLE_TIMEOUT_MILLIS
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { message }
            Thread.sleep(100)
        }
    }

    private fun connectivityManager(): ConnectivityManager {
        return context.getSystemService(ConnectivityManager::class.java)
    }

    private fun requests(): List<WorkInfo> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(BACKGROUND_ACTIONS_WORK_NAME)
            .get()
    }

    private fun workInfo(id: UUID): WorkInfo? {
        return WorkManager.getInstance(context)
            .getWorkInfoById(id)
            .get()
    }

    private fun scheduler(): BackgroundActionsScheduler {
        return dataComponent().backgroundActionsScheduler()
    }

    private fun dataComponent(): DataComponent {
        return (context.applicationContext as ApolloApplication).dataComponent
    }
}
