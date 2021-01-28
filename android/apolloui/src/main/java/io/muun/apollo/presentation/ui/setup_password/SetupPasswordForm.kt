package io.muun.apollo.presentation.ui.setup_password

data class SetupPasswordForm(
    val step: SetupPasswordStep,
    val email: String?,
    val password: String?
)