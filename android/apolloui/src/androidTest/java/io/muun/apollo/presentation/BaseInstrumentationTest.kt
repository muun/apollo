package io.muun.apollo.presentation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bumptech.glide.Glide
import io.muun.apollo.data.preferences.AuthRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.SignupDraftManager
import io.muun.apollo.domain.action.LogoutActions
import io.muun.apollo.domain.action.session.SyncApplicationDataAction
import io.muun.apollo.domain.selector.UserSelector
import io.muun.apollo.presentation.app.ApolloApplication
import io.muun.apollo.presentation.app.Navigator
import io.muun.apollo.presentation.ui.launcher.LauncherActivity
import io.muun.apollo.utils.AutoFlows
import io.muun.apollo.utils.SystemCommand
import io.muun.apollo.utils.WithMuunInstrumentationHelpers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestName

open class BaseInstrumentationTest : WithMuunInstrumentationHelpers {

    @JvmField
    @Rule
    var name: TestName = TestName()

    @JvmField
    @Rule
    var activityRule = ActivityTestRule(LauncherActivity::class.java)

    // Instrumentation, test app (io.muun.apollo.debug.test), executes test logic and tests "real"
    // app. Use it to load a resource of the test app (e.g. a test input for a test)
    lateinit var testContext: Context

    // "Real" app (io.muun.apollo.debug), the one being tested and that users normally see. Use it
    // to load a resource of your the real app (e.g a string resource)
    override lateinit var context: Context

    override lateinit var device: UiDevice

    lateinit var autoFlows: AutoFlows

    private lateinit var authRepository: AuthRepository
    private lateinit var userRepository: UserRepository
    private lateinit var logoutActions: LogoutActions
    private lateinit var signupDraftManager: SignupDraftManager

    private lateinit var navigator: Navigator

    private lateinit var syncApplicationDataAction: SyncApplicationDataAction

    @Before
    fun setUp() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)

        Log.i("test", "=========================================================")
        Log.i("test", name.methodName + "#setUp")

        testContext = InstrumentationRegistry.getInstrumentation().context
        context = InstrumentationRegistry.getInstrumentation().targetContext

        val dataComponent = (context.applicationContext as ApolloApplication).dataComponent

        // TODO should we find a way to make dagger inject these? Is it even possible?
        logoutActions = dataComponent.logoutActions()
        userRepository = dataComponent.userRepository()
        authRepository = dataComponent.authRepository()
        signupDraftManager = dataComponent.signupDraftManager()
        navigator = Navigator(UserSelector(userRepository))

        syncApplicationDataAction = dataComponent.syncApplicationDataAction()

        val pm = context.packageManager
        val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)

        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wait(Until.hasObject(By.pkg(resolveInfo!!.activityInfo.packageName).depth(0)), 1000)

        autoFlows = AutoFlows(device, context)

        clearData()

        SystemCommand.disableSoftKeyboard()
    }

    @After
    fun cleanUp() {
        SystemCommand.enableSoftKeyboard()
    }

    protected fun sniffActivationCode(): String? {
        return userRepository.fetchOne().emergencyKitVerificationCodes.getNewest()
    }

    private fun clearData() {
        // If we don't have nothing to reset (aka this test is running with a clean slate) then
        // let's avoid doing anything. Is cheaper, faster AND, most importantly avoids problems
        // with test framework.
        if (shouldClearData()) {

            Log.i("test", this.javaClass.simpleName + "#clearData")

            logoutActions.uncheckedDestroyWalletForUiTests()

            val mainHandler = Handler(context.mainLooper)
            mainHandler.post {
                // Avoid Glide Bitmaps leaking memory. This may produce a tiny performance hit due
                // to having to reload them for every time but is negligible.
                Glide.get(context).clearMemory()
            }

            // This is necessary if a previous test hung or errored on signup/sign-in initial sync
            // TODO: we may need to do this for other async actions if we want to avoid problems
            // with async actions not running again when supposed to. Maybe a way of resetting EVERY
            // async action? Dagger component can provide them...
            syncApplicationDataAction.reset()

            // Now that we have cleared all local data. We're probably not in the initial screen
            // (landing screen). So let's navigate to it.

            // Only navigate to launcher when NOT ALREADY IN IT. Otherwise, there're problems
            // with the Android ui test framework when navigating out of the initial activity which
            // lead to unexpected behaviour and flakiness. To clarify, problems arise when initial
            // activity started by the test is LauncherActivity and we also 'navigateToLauncher'.
            // Haven't looked too much into it but I think is related to activity flags and test
            // framework playing hard ball.
            val readyToStartTest = signInScreen.waitForLanding()
            if (!readyToStartTest) {
                navigator.navigateToLauncher(context.applicationContext)
            }
        }
    }

    /**
     * This is a heuristic of when do we need to clear local storage. As check for EVERY piece of
     * data stored locally is bit cumbersome (and overkill since its usually deleted altogether),
     * we ask:
     * - if user is stored -> means the previous user was fully loggged in
     * - if signupDraft is stored -> means the previous user left while in signup/in process
     * - if jwt or sessionStatus is stored -> to account for possible weird scenarios where this
     * data is still around when the previous one isn't.
     */
    private fun shouldClearData(): Boolean {
        val optionalUser = userRepository.fetchOneOptional()
        val optionalSignupDraft = signupDraftManager.fetchSignupDraft()
        val hasJwt = authRepository.serverJwt.isPresent
        val hasSessionStatus = authRepository.sessionStatus.isPresent

        return optionalUser.isPresent || optionalSignupDraft != null || hasJwt || hasSessionStatus
    }
}