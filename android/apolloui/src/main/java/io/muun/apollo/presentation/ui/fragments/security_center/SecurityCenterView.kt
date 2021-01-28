package io.muun.apollo.presentation.ui.fragments.security_center

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
}