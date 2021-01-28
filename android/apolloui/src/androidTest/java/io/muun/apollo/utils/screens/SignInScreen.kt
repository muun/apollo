package io.muun.apollo.utils.screens

import android.content.Context
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R
import io.muun.apollo.data.external.Gen
import io.muun.apollo.utils.WithMuunInstrumentationHelpers
import org.assertj.core.api.Assertions.assertThat

class SignInScreen(
    override val device: UiDevice,
    override val context: Context
): WithMuunInstrumentationHelpers {

    fun waitForLanding(): Boolean {
        return normalizedLabel(R.string.signup_start).waitForExists(2500)
    }

    fun fillSignInForm(email: String = Gen.email(),
                       password: String?,
                       recoveryCodeParts: List<String>?) {

        startLogin()

        enterEmail(email)
        confirmEmail()

        awaitEmailVerification(email)

        // TODO find way to bypass email auth for remote envs like stg

        if (password != null) {
            enterPassword(password)
            confirmPassword()

        } else {
            checkNotNull(recoveryCodeParts)

            useRecoveryCode()
            enterRecoveryCode(recoveryCodeParts)
            confirmRecoveryCode()
        }
    }

    fun rcSignIn(recoveryCodeParts: List<String> , email: String? = null) {

        startLogin()

        pressMuunButton(R.id.enter_email_use_rc_only)

        enterRecoveryCode(recoveryCodeParts)
        pressMuunButton(R.id.rc_only_login_continue)

        if (email != null) {
            checkRcLoginEmailAuthScreenDisplayed(email)
        }
    }

    fun startSignup() {
        normalizedLabel(R.string.signup_start).click()
    }

    fun startLogin() {
        normalizedLabel(R.string.login_start).click()
    }

    fun back() {
        id(R.id.signup_header)  // Check toolbar is showed, but...
        device.pressBack()      // Still use device back btn, targeting toolbar back btn is sketchy
    }

    fun enterEmail(email: String) {
        input(R.id.enter_email_input).text = email
    }

    fun confirmEmail() {
        pressMuunButton(R.id.enter_email_action)
    }

    fun checkEmailVerificationScreenDisplayed(email: String) {
        assertThat(id(R.id.signup_waiting_for_email_verification_title).await(20000)).isTrue()
        checkScreenShows(email)
    }

    private fun checkRcLoginEmailAuthScreenDisplayed(email: String) {
        assertThat(id(R.id.rc_login_email_auth_title).await(20000)).isTrue()
        checkScreenShows(email)

        waitUntilGone(R.id.rc_login_email_auth_title)
    }

    /**
     * Wait for auto verify email mechanism to kick in and arrive next screen.
     * We have email auto-verification on local and regtest envs ;).
     */
    fun awaitEmailVerification(email: String) {
        checkEmailVerificationScreenDisplayed(email)
        input(R.id.signup_unlock_edit_password).await(10000)
    }

    fun enterPassword(password: String) {
        input(R.id.signup_unlock_edit_password).text = password
    }

    fun confirmPassword() {
        pressMuunButton(R.id.signup_continue)
    }

    fun useRecoveryCode() {
        pressMuunButtonAndWaitForNewWindow(R.id.signup_forgot_password)
    }

    fun checkEmailConfirmEnabled(enabled: Boolean) {
        assertThat(button(R.id.enter_email_action).isEnabled).isEqualTo(enabled)
    }

    fun checkEmailError() {
        checkInputError(R.id.enter_email_input, R.string.error_email_not_registered)
    }

    fun checkPasswordConfirmEnabled(enabled: Boolean) {
        assertThat(button(R.id.signup_continue).isEnabled).isEqualTo(enabled)
    }

    fun checkPasswordError() {
       checkInputError(R.id.signup_unlock_edit_password, R.string.error_incorrect_password)
    }

    fun abortDialogCancel() {
        normalizedLabel(R.string.cancel).click()
    }

    fun abortDialogAbort() {
        normalizedLabel(R.string.abort).click()
    }

    private fun enterRecoveryCode(recoveryCodeParts: List<String>) {
        recoveryCodeScreen.enterRecoveryCode(recoveryCodeParts)
    }

    private fun confirmRecoveryCode() {
        pressMuunButton(R.id.signup_forgot_password_continue)
    }
}