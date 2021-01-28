package io.muun.apollo.presentation

import io.muun.apollo.utils.RandomUser
import org.junit.Test

open class SecurityCenterTests : BaseInstrumentationTest() {

    @Test
    fun test_01_a_user_can_set_up_email_and_password() {
        val user = RandomUser()

        autoFlows.createRecoverableUser(user.pin, user.email, user.password)
        autoFlows.logOut()
    }

    @Test
    fun test_02_back_navigation_works_for_email_and_password_setup() {
        val user = RandomUser()

        autoFlows.signUp(user.pin)

        homeScreen.goToSecurityCenter()

        // 0 step

        securityCenterScreen.goToEmailAndPassword()

        toolbar.pressBack()

        // 1st step (1 of 4)

        securityCenterScreen.goToEmailAndPassword()
        emailPasswordScreen.startSetup()

        toolbar.pressBack()
        toolbar.pressBack()

        // 2nd step (2 of 4)

        securityCenterScreen.goToEmailAndPassword()
        emailPasswordScreen.startSetup()
        emailPasswordScreen.enterEmailAndConfirm(user.email)

        emailPasswordScreen.checkEmailVerificationScreenDisplayed(user.email)

        // This should be quick to avoid auto verify email
        toolbar.pressBack()
        toolbar.pressBack()
        toolbar.pressBack()

        // 3rd step (3 of 4)

        securityCenterScreen.goToEmailAndPassword()
        emailPasswordScreen.startSetup()
        emailPasswordScreen.enterEmailAndConfirm(user.email)

        emailPasswordScreen.awaitEmailVerification(user.email, false)

        emailPasswordScreen.tryClose()
        emailPasswordScreen.abortFlow()

        // 4th step (4 of 4)

        securityCenterScreen.goToEmailAndPassword()
        emailPasswordScreen.startSetup()
        emailPasswordScreen.enterEmailAndConfirm(user.email)

        emailPasswordScreen.awaitEmailVerification(user.email, false)

        emailPasswordScreen.enterPasswordsAndConfirm(user.password)

        emailPasswordScreen.tryClose()
        emailPasswordScreen.abortFlow()

        // That's it, that was the last step and we can abort the flow from there too. We're good.
    }

    @Test
    fun test_03_a_user_can_sign_in_with_recovery_code() {
        val user = RandomUser()

        autoFlows.createRecoverableUser(user.pin, user.email, user.password) // Needed to set up RC

        val recoveryCodeParts = autoFlows.setUpRecoveryCode()

        autoFlows.logOut()

        autoFlows.signIn(user.email, pin = user.pin, recoveryCodeParts = recoveryCodeParts)
    }

    @Test
    fun test_04_back_navigation_works_for_recovery_code_setup_flow() {
        val user = RandomUser()

        autoFlows.createRecoverableUser(user.pin, user.email, user.password) // Needed to set up RC

        homeScreen.goToSecurityCenter()

        // 0 step
        securityCenterScreen.goToRecoveryCode()

        toolbar.pressBack()

        // 1st step (1 of 3)
        securityCenterScreen.goToRecoveryCode()
        recoveryCodeScreen.begin()

        recoveryCodeScreen.tryClose()
        recoveryCodeScreen.abortFlow()

        // 2nd step (2 of 3)
        securityCenterScreen.goToRecoveryCode()
        recoveryCodeScreen.begin()

        recoveryCodeScreen.goNext()

        toolbar.pressBack()

        recoveryCodeScreen.abortFlow()

        // 3rd step (3 of 3)
        securityCenterScreen.goToRecoveryCode()
        recoveryCodeScreen.begin()

        val recoveryCode = recoveryCodeScreen.readRecoveryCode()
        recoveryCodeScreen.goNext()

        recoveryCodeScreen.enterRecoveryCode(recoveryCode)

        recoveryCodeScreen.confirmRecoveryCode()

        recoveryCodeScreen.tryClose()
        recoveryCodeScreen.abortFlow()

        // That's it, that was the last step and we can abort the flow from there too. We're good.
    }

    @Test
    fun test_05_a_user_can_have_full_security_setup() {
        val user = RandomUser()

        autoFlows.createRecoverableUser(user.pin, user.email, user.password) // Needed to set up RC

        val recoveryCodeParts = autoFlows.setUpRecoveryCode()

        autoFlows.exportEmergencyKit {
            sniffActivationCode()!!
        }

        autoFlows.logOut()

        autoFlows.signIn(user.email, pin = user.pin, password = user.password)

        autoFlows.logOut()

        autoFlows.signIn(user.email, pin = user.pin, recoveryCodeParts = recoveryCodeParts)
    }

    @Test
    fun test_06_a_user_can_have_full_security_setup_skipping_email_first() {
        val user = RandomUser()

        autoFlows.signUp(user.pin)

        autoFlows.skipEmailAndPasswordSetup()

        val recoveryCodeParts = autoFlows.setUpRecoveryCode()

        autoFlows.exportEmergencyKit {
            sniffActivationCode()!!
        }

        autoFlows.logOut()

        autoFlows.rcSignIn(recoveryCode = recoveryCodeParts, pin = user.pin)

        autoFlows.setUpEmailAndPassword(user.email, user.password)

        autoFlows.logOut()

        autoFlows.rcSignIn(recoveryCode = recoveryCodeParts, email = user.email, pin = user.pin)

        autoFlows.logOut()

        autoFlows.signIn(user.email, pin = user.pin, password = user.password)

        autoFlows.logOut()

        autoFlows.signIn(user.email, pin = user.pin, recoveryCodeParts = recoveryCodeParts)
    }
}