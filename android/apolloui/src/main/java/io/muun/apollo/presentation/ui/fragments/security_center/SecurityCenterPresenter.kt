package io.muun.apollo.presentation.ui.fragments.security_center

import android.os.Bundle
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_SECURITY_CENTER
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_SECURITY_CENTER_EMAIL_STATUS
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_SECURITY_CENTER_NEXT_STEP
import io.muun.apollo.domain.model.SecurityCenter
import io.muun.apollo.domain.model.SecurityLevel
import io.muun.apollo.domain.selector.UserPreferencesSelector
import io.muun.apollo.presentation.ui.base.ParentPresenter
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.apollo.presentation.ui.fragments.security_center.SecurityCenterView.TaskStatus
import javax.inject.Inject

@PerFragment
class SecurityCenterPresenter @Inject constructor(
    private val userPreferencesSel: UserPreferencesSelector,
) : SingleFragmentPresenter<SecurityCenterView, ParentPresenter>() {

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        setUpBoxes()
    }

    private fun setUpBoxes() {
        // NOTE: this is not reactive. Fortunately, nothing can change unless we leave this screen.
        userSel.watch()
            .first()
            .doOnNext { user ->

                val emailSetupSkipped = userPreferencesSel.emailSetupSkipped()

                val emailStatus = when {
                    user.hasPassword -> TaskStatus.DONE
                    else -> TaskStatus.PENDING
                }

                val recoveryCodeStatus = when {
                    user.hasRecoveryCode -> TaskStatus.DONE
                    user.hasPassword || emailSetupSkipped -> TaskStatus.PENDING
                    else -> TaskStatus.BLOCKED
                }

                val exportKeysStatus = when {
                    user.hasExportedEmergencyKit() || user.hasExportedKeys -> TaskStatus.DONE
                    user.hasRecoveryCode -> TaskStatus.PENDING
                    else -> TaskStatus.BLOCKED
                }

                val securityCenter = SecurityCenter(user, emailSetupSkipped)

                view.setTaskStatus(
                    emailStatus,
                    recoveryCodeStatus,
                    exportKeysStatus,
                    securityCenter
                )

                reportNextStep(securityCenter.getLevel())
            }
            .let(this::subscribeTo)
    }

    private fun reportNextStep(securityLevel: SecurityLevel) {

        val nextStep = when (securityLevel) {
            SecurityLevel.ANON -> S_SECURITY_CENTER_NEXT_STEP.EMAIL
            SecurityLevel.EMAIL -> S_SECURITY_CENTER_NEXT_STEP.RECOVERY_CODE
            SecurityLevel.SKIPPED_EMAIL_ANON -> S_SECURITY_CENTER_NEXT_STEP.RECOVERY_CODE
            SecurityLevel.EMAIL_AND_RC -> S_SECURITY_CENTER_NEXT_STEP.EMERGENCY_KIT
            SecurityLevel.SKIPPED_EMAIL_RC -> S_SECURITY_CENTER_NEXT_STEP.EMERGENCY_KIT
            SecurityLevel.DONE -> S_SECURITY_CENTER_NEXT_STEP.FULLY_SET
            SecurityLevel.SKIPPED_EMAIL_DONE -> S_SECURITY_CENTER_NEXT_STEP.FULLY_SET
        }

        val emailStatus = when (securityLevel) {
            SecurityLevel.ANON -> S_SECURITY_CENTER_EMAIL_STATUS.NOT_SET
            SecurityLevel.EMAIL -> S_SECURITY_CENTER_EMAIL_STATUS.COMPLETED
            SecurityLevel.EMAIL_AND_RC -> S_SECURITY_CENTER_EMAIL_STATUS.COMPLETED
            SecurityLevel.DONE -> S_SECURITY_CENTER_EMAIL_STATUS.COMPLETED
            SecurityLevel.SKIPPED_EMAIL_ANON -> S_SECURITY_CENTER_EMAIL_STATUS.SKIPPED
            SecurityLevel.SKIPPED_EMAIL_RC -> S_SECURITY_CENTER_EMAIL_STATUS.SKIPPED
            SecurityLevel.SKIPPED_EMAIL_DONE -> S_SECURITY_CENTER_EMAIL_STATUS.SKIPPED
        }

        val origin = SecurityCenterFragmentArgs.fromBundle(view.argumentsBundle).origin

        val event = S_SECURITY_CENTER(nextStep, origin, emailStatus)

        analytics.report(event)
    }

    fun goToEmailSetup() {
        navigator.navigateToSetupPassword(context)
    }

    fun goToRecoveryCodeSetup() {
        navigator.navigateToRecoveryCode(context)
    }

    fun goToExportKeys() {
        navigator.navigateToExportKeysIntro(context)
    }

    fun goToRecoveryTool() {
        navigator.navigateToRecoveryTool(context)
    }
}