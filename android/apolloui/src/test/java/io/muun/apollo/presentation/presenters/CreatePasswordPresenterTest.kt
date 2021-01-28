package io.muun.apollo.presentation.presenters

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.muun.apollo.data.external.Gen
import io.muun.apollo.domain.errors.EmptyFieldError
import io.muun.apollo.domain.errors.PasswordTooShortError
import io.muun.apollo.domain.errors.PasswordsDontMatchError
import io.muun.apollo.presentation.BasePresentationTest
import io.muun.apollo.presentation.ui.fragments.create_password.CreatePasswordParentPresenter
import io.muun.apollo.presentation.ui.fragments.create_password.CreatePasswordPresenter
import io.muun.common.Rules
import org.junit.Test

class CreatePasswordPresenterTest: BasePresentationTest() {

    @Test
    fun `should show error if password is empty`() {
        val (presenter, parent) = sharedInit()

        presenter.submitPassword("", "")
        verify(presenter.view).setPasswordError(null)
        verify(presenter.view).setPasswordError(isA<EmptyFieldError>())

        verify(parent, never()).submitPassword(any())
    }

    @Test
    fun `should show error if password is too short`() {
        val (presenter, parent) = sharedInit()

        val password = Gen.alpha(Rules.PASSWORD_MIN_LENGTH - 1)
        presenter.submitPassword(password, password)
        verify(presenter.view).setPasswordError(null)
        verify(presenter.view).setPasswordError(isA<PasswordTooShortError>())

        verify(parent, never()).submitPassword(any())
    }

    @Test
    fun `should show error if passwords dont match`() {
        val (presenter, parent) = sharedInit()

        val password = Gen.alpha(Rules.PASSWORD_MIN_LENGTH  + 1)
        presenter.submitPassword(password, "somePassword")
        verify(presenter.view).setPasswordError(null)
        verify(presenter.view).setConfirmPasswordError(isA<PasswordsDontMatchError>())

        verify(parent, never()).submitPassword(any())
    }

    @Test
    fun `should submit passwords of sufficient length`() {
        val (presenter, parent) = sharedInit()
        val password = Gen.alpha(Rules.PASSWORD_MIN_LENGTH)

        presenter.submitPassword(password, password)

        verify(parent, times(1)).submitPassword(password)
        verifyNoMoreInteractions(parent)
    }

    private data class SharedInit(
            val presenter: CreatePasswordPresenter,
            val parent: CreatePasswordParentPresenter
    )

    /**
     * Create basic objects shared by all test cases.
     */
    private fun sharedInit(): SharedInit {

        val parent = mock<CreatePasswordParentPresenter> {
        }

        val presenter = object: CreatePasswordPresenter() {
            override fun setUpDeprecatedClientVersionCheck() {}
            override fun setUpSessionExpiredCheck() {}
            override fun setUpNetworkInfo() {}
            override fun handleError(error: Throwable) {}
            override fun reportPasswordDidNotMatch() {}
        }

        presenter.setParentPresenter(parent)
        presenter.setView(mock())

        return SharedInit(presenter, parent)
    }
}