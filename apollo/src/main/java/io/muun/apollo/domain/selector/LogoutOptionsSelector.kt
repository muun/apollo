package io.muun.apollo.domain.selector

import rx.Observable
import javax.inject.Inject

class LogoutOptionsSelector @Inject constructor(
    val userSel: UserSelector,
    val paymentContextSel: PaymentContextSelector,
    val operationSel: OperationSelector
) {

    class LogoutOptions(
        val isRecoverable: Boolean,
        val hasBalance: Boolean,
        val hasUnsetteledOps: Boolean
    ) {

        fun canDestroyWallet() =
            isRecoverable || (!hasBalance && !hasUnsetteledOps)
    }

    fun watch() =
        Observable
            .combineLatest(
                userSel.watch(),
                paymentContextSel.watch().map { it.userBalance > 0 },
                operationSel.watchUnsettled().map { it.isNotEmpty() },
                { user, balance, hasUnsettled ->
                    LogoutOptions(user.isRecoverable, balance, hasUnsettled)
                }
            )

    fun get() =
        watch().toBlocking().first()

}