package io.muun.apollo.utils.screens

import android.content.Context
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R
import io.muun.apollo.data.external.Gen
import io.muun.apollo.utils.WithMuunInstrumentationHelpers

class SignInScreen(
    override val device: UiDevice,
    override val context: Context,
) : WithMuunInstrumentationHelpers {

    fun waitForLanding(): Boolean {
        return normalizedLabel(R.string.signup_start).waitForExists(2500)
    }

    fun fillSignInForm(
        email: String = Gen.email(),
        password: String?,
        recoveryCodeParts: List<String>?,
    ) {

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

    fun rcSignIn(recoveryCodeParts: List<String>, email: String? = null) {

        startLogin()

        recoverWithRecoveryCode()

        enterRecoveryCode(recoveryCodeParts)
        confirmRecoveryCodeOnlyLogin()

        if (email != null) {
            checkRcLoginEmailAuthScreenDisplayed(email)
        }
    }

    fun recoverWithRecoveryCode() {
        pressMuunButton(R.id.enter_email_use_rc_only)
    }

    fun confirmRecoveryCodeOnlyLogin() {
        pressMuunButton(R.id.rc_only_login_continue)
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
        id(R.id.signup_waiting_for_email_verification_title).await(20000)
        checkScreenShows(email)
    }

    private fun checkRcLoginEmailAuthScreenDisplayed(email: String) {
        id(R.id.rc_login_email_auth_title).await(20000)

        val firstTwoLetters = email.substring(0, 2)
        val tld = email.substring(email.indexOf('.') + 1)
        val obfuscatedEmailRegex = "$firstTwoLetters.*@.*\\.$tld"
        checkScreenFor(".*$obfuscatedEmailRegex.*") // regex must perfectly match whole string

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
        if (enabled) {
            button(R.id.enter_email_action).assertEnabled()

        } else {
            button(R.id.enter_email_action).assertDisabled()
        }
    }

    fun checkEmailError() {
        checkInputError(R.id.enter_email_input, R.string.error_email_not_registered)
    }

    fun checkPasswordConfirmEnabled(enabled: Boolean) {
        if (enabled) {
            button(R.id.signup_continue).assertEnabled()

        } else {
            button(R.id.signup_continue).assertDisabled()
        }
    }

    fun checkPasswordError() {
        checkInputError(R.id.signup_unlock_edit_password, R.string.error_incorrect_password)
    }

    fun abortDialogCancelWithSafeguard() {
        try {
            signInScreen.abortDialogCancel()
        } catch (_: Exception) {
            // I'm DONE with this annoying issue regarding Google Password Manager.
            // Not gonna keep suffering flakiness from it. ENOUGH!
            // TODO: find reliable way to disable Google Password Manager
            signInScreen.back() // Add extra back to dismiss Google Password Manager popup
            signInScreen.abortDialogCancel()
        }
    }


    fun abortDialogCancel() {
        normalizedLabel(R.string.cancel).click()
    }

    fun abortDialogAbort() {
        normalizedLabel(R.string.abort).click()
    }

    fun enterRecoveryCode(recoveryCodeParts: List<String>) {
        recoveryCodeScreen.enterRecoveryCode(recoveryCodeParts)
    }

    fun confirmRecoveryCode() {
        pressMuunButton(R.id.signup_forgot_password_continue)
    }
}