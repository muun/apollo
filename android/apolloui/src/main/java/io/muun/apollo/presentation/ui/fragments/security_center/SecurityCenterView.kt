package io.muun.apollo.presentation.ui.fragments.security_center

import io.muun.apollo.domain.analytics.AnalyticsEvent.SECURITY_CENTER_ORIGIN
import io.muun.apollo.domain.model.SecurityCenter
import io.muun.apollo.presentation.ui.base.BaseView


interface SecurityCenterView: BaseView {

    enum class TaskStatus {
        BLOCKED,
        PENDING,
        DONE
    }

    fun setTaskStatus(
        emailStatus: TaskStatus,
        recoveryCodeStatus: TaskStatus,
        exportKeysStatus: TaskStatus,
        securityCenter: SecurityCenter
    )

    /**
     * Return how the user got to this visit to Security Center.
     */
    fun getOrigin(): SECURITY_CENTER_ORIGIN
}