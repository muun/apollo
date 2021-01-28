package io.muun.apollo.presentation.presenters

import android.os.Bundle
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.muun.apollo.data.external.Gen
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.base.ActionState
import io.muun.apollo.domain.action.session.UseMuunLinkAction
import io.muun.apollo.domain.action.user.EmailLinkAction
import io.muun.apollo.presentation.BasePresentationTest
import io.muun.apollo.presentation.ui.fragments.verify_email.VerifyEmailParentPresenter
import io.muun.apollo.presentation.ui.fragments.verify_email.VerifyEmailPresenter
import io.muun.apollo.presentation.ui.utils.UiNotificationPoller
import org.junit.Test
import rx.subjects.BehaviorSubject

class VerifyEmailPresenterTest: BasePresentationTest() {

    @Test
    fun shouldSetEmailInView() {
        val user = Gen.user(email = "user1@muun.com")

        val parent = mock<VerifyEmailParentPresenter> {
            on { getEmail() } doReturn(user.email.get())
        }

        val notificationPoller = mock<UiNotificationPoller>()

        val useMuunLinkResult = BehaviorSubject.create<ActionState<Void>>()

        val useMuunLinkAction = mock<UseMuunLinkAction> {
            on { state } doReturn(useMuunLinkResult)
        }

        val userRepository = mock<UserRepository>()

        val emailLinkAction = object: EmailLinkAction(userRepository) {}

        val presenter = object: VerifyEmailPresenter(
            notificationPoller,
            useMuunLinkAction,
            emailLinkAction) {

            override fun setUpDeprecatedClientVersionCheck() {}
            override fun setUpSessionExpiredCheck() {}
            override fun setUpNetworkInfo() {}
        }

        presenter.setParentPresenter(parent)
        presenter.setView(mock())
        presenter.setUp(Bundle())

        verify(presenter.view, times(1)).setEmail(user.email.get())
        verifyNoMoreInteractions(presenter.view)
    }
}