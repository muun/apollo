package io.muun.apollo.presentation

import io.muun.apollo.utils.RandomUser
import io.muun.apollo.utils.screens.ReceiveScreen
import io.muun.common.model.ReceiveFormatPreference
import org.junit.Test

open class SettingsTests : BaseInstrumentationTest() {

    @Test
    fun test_01_a_user_can_change_password_with_old_password() {
        val user = RandomUser()
        val newPassword = "the new password"

        autoFlows.createRecoverableUser(user.pin, user.email, user.password)

        autoFlows.changePassword(user, newPassword)

        user.password = newPassword

        autoFlows.logOut()

        autoFlows.signIn(user.email, user.password, pin = user.pin)
    }

    @Test
    fun test_02_a_user_can_change_password_with_recovery_code() {
        val user = RandomUser()
        val newPassword = "the new password"

        autoFlows.createRecoverableUser(user.pin, user.email, user.password) // Needed to set up RC

        user.recoveryCode = autoFlows.setUpRecoveryCode()

        autoFlows.changePassword(user, newPassword)

        user.password = newPassword

        autoFlows.logOut()

        autoFlows.signIn(user.email, user.password, pin = user.pin)
    }

    @Test
    fun test_03_a_user_can_change_receive_preference_and_receive_funds() {
        val user = RandomUser()

        autoFlows.signUp(user.pin)

        // Check Default receive preference is bitcoin (no actual receive, other tests exercise that)
        autoFlows.checkReceivePreferenceIs(ReceiveFormatPreference.ONCHAIN)

        // Change to Lightning + receive
        autoFlows.turnOnReceiveLightningByDefault()
        autoFlows.checkReceivePreferenceIs(ReceiveFormatPreference.LIGHTNING)

        autoFlows.receiveMoneyFromLNWithAmountLessInvoice(100_000)

        // Change to Unified QR + receive
        autoFlows.turnOnUnifiedQr()
        autoFlows.checkReceivePreferenceIs(ReceiveFormatPreference.UNIFIED)

        autoFlows.receiveMoneyFromUnifiedQrViaLightning(
            100_000,
            ReceiveScreen.UnifiedQrDraft.OffChain(100_000)
        )

        // Test Unified QR changes in config

        homeScreen.goToReceive()
        receiveScreen.checkUnifiedQrConfig()

        // Test receive unified QR with amountless invoice

        autoFlows.receiveMoneyFromUnifiedQrViaLightning(
            200_000,
            ReceiveScreen.UnifiedQrDraft.OffChain()
        )
    }

}
