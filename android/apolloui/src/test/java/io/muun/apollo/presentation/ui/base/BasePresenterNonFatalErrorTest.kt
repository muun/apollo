package io.muun.apollo.presentation.ui.base

import android.util.Log
import io.mockk.mockk
import io.mockk.verify
import io.muun.apollo.BaseTest
import io.muun.apollo.R
import io.muun.apollo.data.net.base.NetworkException
import io.muun.apollo.data.net.base.TlsVerificationError
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import javax.net.ssl.SSLPeerUnverifiedException

class BasePresenterNonFatalErrorTest : BaseTest() {

    // TlsVerificationError is a NetworkException: it keeps the generic network message, but
    // unlike other network errors it has to reach Crashlytics.

    private val view: BaseView = mockk(relaxed = true)
    private val errorLogs = ErrorLogsTree()

    @Before
    fun plantTree() {
        Timber.plant(errorLogs)
    }

    @After
    fun uprootTree() {
        Timber.uproot(errorLogs)
    }

    @Test
    fun `should report a TlsVerificationError and show the network message`() {
        // GIVEN a rejected server certificate
        val error = TlsVerificationError(SSLPeerUnverifiedException("pin"))

        // WHEN the presenter handles it
        val actual = presenter().runNonFatalErrorHandling(error)

        // THEN it's handled with the network message and reported
        assertThat(actual).isTrue()
        verify { view.showErrorDialog(R.string.network_error_message) }
        assertThat(errorLogs.errors).containsExactly(error)
    }

    @Test
    fun `should report a wrapped TlsVerificationError`() {
        // GIVEN a rejected server certificate wrapped in another error
        val error = RuntimeException(TlsVerificationError(SSLPeerUnverifiedException("pin")))

        // WHEN the presenter handles it
        val actual = presenter().runNonFatalErrorHandling(error)

        // THEN it's handled with the network message and reported
        assertThat(actual).isTrue()
        verify { view.showErrorDialog(R.string.network_error_message) }
        assertThat(errorLogs.errors).containsExactly(error)
    }

    @Test
    fun `should not report other network errors`() {
        // GIVEN a network error that isn't a TLS trust failure
        val error = NetworkException(RuntimeException("reset"))

        // WHEN the presenter handles it
        val actual = presenter().runNonFatalErrorHandling(error)

        // THEN it's handled with the network message but not reported
        assertThat(actual).isTrue()
        verify { view.showErrorDialog(R.string.network_error_message) }
        assertThat(errorLogs.errors).isEmpty()
    }

    private fun presenter(): TestPresenter {
        return TestPresenter().also {
            it.setView(view)
        }
    }

    /**
     * Timber.e is how errors reach Crashlytics (see MuunTree), so recording them is enough.
     */
    private class ErrorLogsTree : Timber.Tree() {

        val errors = mutableListOf<Throwable>()

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == Log.ERROR && t != null) {
                errors.add(t)
            }
        }
    }

    /**
     * handleNonFatalError is protected; a subclass reaches it without standing up the whole
     * dependency graph.
     */
    private class TestPresenter : BasePresenter<BaseView>() {

        fun runNonFatalErrorHandling(error: Throwable): Boolean {
            return handleNonFatalError(error)
        }
    }
}
