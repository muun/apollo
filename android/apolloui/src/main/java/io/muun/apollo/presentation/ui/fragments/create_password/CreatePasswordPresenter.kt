package io.muun.apollo.presentation.ui.fragments.create_password

import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import io.muun.apollo.domain.errors.EmptyFieldError
import io.muun.apollo.domain.errors.PasswordTooShortError
import io.muun.apollo.domain.errors.PasswordsDontMatchError
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.analytics.AnalyticsEvent.PASSWORD_ERROR
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.common.Rules
import javax.inject.Inject


@PerFragment
open class CreatePasswordPresenter @Inject constructor():
    SingleFragmentPresenter<CreatePasswordView, CreatePasswordParentPresenter>() {

    fun submitPassword(password: String, confirmPassword: String) {
        view.setPasswordError(null)

        when {

            password == "" -> {
                view.setPasswordError(EmptyFieldError(EmptyFieldError.Field.PASSWORD))

            }

            password.length < Rules.PASSWORD_MIN_LENGTH -> {
                view.setPasswordError(PasswordTooShortError())
            }

            password != confirmPassword -> {
                reportPasswordDidNotMatch()
                view.setConfirmPasswordError(PasswordsDontMatchError())
            }

            else -> {
                parentPresenter.submitPassword(password)
            }
        }
    }

    fun isValidPassword(password: String) =
        !TextUtils.isEmpty(password) && password.length >= Rules.PASSWORD_MIN_LENGTH

    @VisibleForTesting
    open fun reportPasswordDidNotMatch() {
        analytics.report(AnalyticsEvent.E_PASSWORD(PASSWORD_ERROR.DID_NOT_MATCH))
    }

    fun goBack() {
        parentPresenter.cancelCreatePassword()
    }

    override fun getEntryEvent() =
        AnalyticsEvent.S_SIGN_UP_PASSWORD()
}