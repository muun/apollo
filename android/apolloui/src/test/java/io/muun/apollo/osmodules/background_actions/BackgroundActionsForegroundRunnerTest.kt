package io.muun.apollo.osmodules.background_actions

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.muun.apollo.data.net.NetworkStatusProvider
import io.muun.apollo.data.os.AppState
import io.muun.apollo.data.os.AppStateProvider
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.data.preferences.UserRepository
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.threeten.bp.Duration
import rx.Observable
import rx.Subscription
import rx.schedulers.TestScheduler
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import java.util.concurrent.TimeUnit

class BackgroundActionsForegroundRunnerTest {

    private val testScheduler = TestScheduler()

    private val initialSyncCompleted = BehaviorSubject.create(false)

    // Logout clears the preference without emitting, so it can disagree with the subject.
    private var synced = false

    private val appStates = BehaviorSubject.create<AppState>()

    // Read on subscription and on every change, like the real provider, so it can change before
    // a change arrives.
    private var connectedNow = false

    private val networkChanges = PublishSubject.create<Unit>()

    private val scheduler = mockk<BackgroundActionsScheduler> {
        every { runNow() } just Runs
    }

    private val userRepository = mockk<UserRepository> {
        every { watchInitialSyncCompleted() } returns initialSyncCompleted
        every { isInitialSyncCompleted } answers { synced }
    }

    private val appStateProvider = mockk<AppStateProvider> {
        every { watch() } returns appStates
    }

    private val networkStatusProvider = mockk<NetworkStatusProvider> {
        every { watchConnected() } answers { watchNetwork() }
    }

    private val transformerFactory = mockk<ExecutionTransformerFactory> {
        every { backgroundScheduler } returns testScheduler
    }

    private lateinit var subscription: Subscription

    @Before
    fun setUp() {
        val runner = BackgroundActionsForegroundRunner(
            scheduler,
            userRepository,
            appStateProvider,
            networkStatusProvider,
            transformerFactory
        )

        subscription = runner.start()
        testScheduler.triggerActions()
    }

    @After
    fun tearDown() {
        subscription.unsubscribe()
    }

    @Test
    fun runsWhenSyncedForegroundAndOnline() {
        logIn()
        enterForeground()
        goOnline()

        assertRuns(1)
    }

    @Test
    fun waitsUntilOnline() {
        logIn()
        enterForeground()
        assertRuns(0)

        goOnline()

        assertRuns(1)
    }

    @Test
    fun waitsUntilSynced() {
        enterForeground()
        goOnline()
        assertRuns(0)

        logIn()

        assertRuns(1)
    }

    @Test
    fun doesNothingInTheBackground() {
        logIn()
        goOnline()
        enterBackground()
        goOffline()
        goOnline()

        assertRuns(0)
    }

    @Test
    fun leavingTheForegroundBeforeGettingOnlineDropsTheRun() {
        logIn()
        enterForeground()
        enterBackground()

        goOnline()

        assertRuns(0)
    }

    @Test
    fun networkFlapWithinTheCooldownRunsOnce() {
        logIn()
        enterForeground()
        goOnline()
        assertRuns(1)

        goOffline()
        goOnline()

        assertRuns(1)
    }

    @Test
    fun returningToTheForegroundWithinTheCooldownRunsOnce() {
        logIn()
        enterForeground()
        goOnline()
        assertRuns(1)

        enterBackground()
        enterForeground()

        assertRuns(1)
    }

    @Test
    fun anEdgeAfterTheCooldownRunsAgain() {
        logIn()
        enterForeground()
        goOnline()
        assertRuns(1)

        enterBackground()
        advance(FOREGROUND_RUN_COOLDOWN)
        enterForeground()

        assertRuns(2)
    }

    @Test
    fun anEdgeAfterASilentLogoutDoesNotRun() {
        logIn()
        enterForeground()
        goOnline()
        assertRuns(1)

        logOut()
        enterBackground()
        advance(FOREGROUND_RUN_COOLDOWN)
        enterForeground()

        assertRuns(1)
    }

    @Test
    fun loggingInAgainWithoutLeavingTheAppRunsAgain() {
        logIn()
        enterForeground()
        goOnline()
        assertRuns(1)

        logOut()
        advance(FOREGROUND_RUN_COOLDOWN)
        logIn()

        assertRuns(2)
    }

    @Test
    fun loggingInAgainWithinTheCooldownRunsAgain() {
        logIn()
        enterForeground()
        goOnline()
        assertRuns(1)

        logOut()
        logIn()

        assertRuns(2)
    }

    @Test
    fun aFailedRunDoesNotStopTheNextOne() {
        every { scheduler.runNow() } throws IllegalStateException() andThen Unit
        logIn()
        enterForeground()
        goOnline()
        assertRuns(1)

        enterBackground()
        advance(FOREGROUND_RUN_COOLDOWN)
        enterForeground()

        assertRuns(2)
    }

    @Test
    fun aNetworkLostWhileInTheBackgroundIsReadOnReturning() {
        logIn()
        enterForeground()
        goOnline()
        assertRuns(1)

        enterBackground()
        advance(FOREGROUND_RUN_COOLDOWN)
        // Its callback has not arrived yet.
        connectedNow = false
        enterForeground()
        assertRuns(1)

        goOnline()

        assertRuns(2)
    }

    @Test
    fun aFailedNetworkWatchOnlyDropsThatVisit() {
        every { networkStatusProvider.watchConnected() } returns
            Observable.error(IllegalStateException()) andThenAnswer { watchNetwork() }
        logIn()
        goOnline()
        enterForeground()
        assertRuns(0)

        enterBackground()
        enterForeground()

        assertRuns(1)
    }

    private fun watchNetwork(): Observable<Boolean> {
        return networkChanges.startWith(Unit).map { connectedNow }.distinctUntilChanged()
    }

    private fun logIn() {
        synced = true
        initialSyncCompleted.onNext(true)
        testScheduler.triggerActions()
    }

    private fun logOut() {
        synced = false
    }

    private fun enterForeground() {
        appStates.onNext(AppState.FOREGROUND)
        testScheduler.triggerActions()
    }

    private fun enterBackground() {
        appStates.onNext(AppState.BACKGROUND)
        testScheduler.triggerActions()
    }

    private fun goOnline() {
        connectedNow = true
        networkChanges.onNext(Unit)
        testScheduler.triggerActions()
    }

    private fun goOffline() {
        connectedNow = false
        networkChanges.onNext(Unit)
        testScheduler.triggerActions()
    }

    private fun advance(duration: Duration) {
        testScheduler.advanceTimeBy(duration.toMillis(), TimeUnit.MILLISECONDS)
    }

    private fun assertRuns(times: Int) {
        testScheduler.triggerActions()
        verify(exactly = times) { scheduler.runNow() }
    }
}
