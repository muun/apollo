package io.muun.apollo.presentation.ui.security_logout

import android.os.Bundle
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import io.muun.apollo.domain.action.session.LogoutAction
import io.muun.apollo.presentation.BasePresentationTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityLogoutPresenterTest : BasePresentationTest() {

    @Test
    fun `should log out once when set up on every resume`() {
        // GIVEN
        val logout = mock<LogoutAction>()
        val presenter = buildPresenter(logout)

        // WHEN
        presenter.setUp(Bundle())
        presenter.setUp(Bundle())

        // THEN
        verify(logout, times(1)).run()
        assertTrue(presenter.loggedOut)
    }

    @Test
    fun `should not log out when already logged out`() {
        // GIVEN
        val logout = mock<LogoutAction>()
        val presenter = buildPresenter(logout)
        presenter.loggedOut = true

        // WHEN
        presenter.setUp(Bundle())

        // THEN
        verify(logout, never()).run()
    }

    private fun buildPresenter(logout: LogoutAction): SecurityLogoutPresenter {
        return object : SecurityLogoutPresenter(logout) {
            override fun setUpNetworkInfo() {}
            override fun setUpDeprecatedClientVersionCheck() {}
            override fun setUpSessionExpiredCheck() {}
            override fun setUpBuildErrorEmailReportAction() {}
        }
    }
}
