package io.muun.apollo.presentation.ui.lnurl.withdraw

import io.muun.apollo.domain.model.lnurl.LnUrlState
import io.muun.apollo.presentation.ui.base.SingleFragmentView

interface LnUrlWithdrawView: SingleFragmentView {

    companion object {
        const val ARG_LNURL = "lnurl"
        const val ARG_LNURL_WITHDRAW = "lnurl_withdraw"
        const val ARG_LN_PAYMENT_FAILED = "ln_payment_failed"
    }

    fun handleWithdrawState(state: LnUrlState)

}
