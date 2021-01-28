package io.muun.apollo.presentation.presenters

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.muun.apollo.domain.action.fcm.ForceFetchFcmAction
import io.muun.apollo.domain.model.SignupDraft
import io.muun.apollo.domain.model.SignupStep
import io.muun.apollo.presentation.BasePresentationTest
import io.muun.apollo.presentation.ui.fragments.landing.LandingPresenter
import io.muun.apollo.presentation.ui.signup.SignupPresenter
import org.junit.Test

class LandingPresenterTest: BasePresentationTest() {

    @Test
    fun `should call on parent to start signup`() {
        val (presenter, parent, draft) = sharedInit()

        presenter.startSignup()

        verify(parent, times(1)).startSignup()
        verifyNoMoreInteractions(parent)
    }

    @Test
    fun `should call on parent to start login`() {
        val (presenter, parent, draft) = sharedInit()

        presenter.startLogin()

        verify(parent, times(1)).startLogin()
        verifyNoMoreInteractions(parent)
    }

    private data class SharedInit(
            val presenter: LandingPresenter,
            val parent: SignupPresenter,
            val draft: SignupDraft
    )

    private fun sharedInit(): SharedInit {
        val draft = SignupDraft()

        draft.step = SignupStep.START

        val parent = mock<SignupPresenter>()

        val forceFetchFcmAction = mock<ForceFetchFcmAction>()

        val presenter = object: LandingPresenter(forceFetchFcmAction) {
            override fun setUpDeprecatedClientVersionCheck() {}
            override fun setUpSessionExpiredCheck() {}
            override fun setUpNetworkInfo() {}
        }

        presenter.setParentPresenter(parent)
        presenter.setView(mock())

        return SharedInit(presenter, parent, draft)
    }
}