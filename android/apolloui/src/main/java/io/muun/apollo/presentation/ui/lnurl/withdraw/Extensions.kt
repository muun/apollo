package io.muun.apollo.presentation.ui.lnurl.withdraw

import android.content.Context
import io.muun.apollo.R
import io.muun.apollo.domain.model.lnurl.LnUrlError
import io.muun.apollo.presentation.analytics.AnalyticsEvent.ERROR_TYPE
import io.muun.apollo.presentation.ui.fragments.error.ErrorViewModel
import io.muun.apollo.presentation.ui.fragments.error.ErrorViewModel.ErrorViewKind
import io.muun.apollo.presentation.ui.utils.StyledStringRes.StringResWithArgs

fun LnUrlError.asViewModel(ctx: Context) = object: ErrorViewModel {

    override fun kind(): ErrorViewKind = with(this@asViewModel) {
        when (this) {
            is LnUrlError.Unresponsive -> ErrorViewKind.RETRYABLE
            is LnUrlError.Unknown -> ErrorViewKind.REPORTABLE
            is LnUrlError.InvalidCode -> ErrorViewKind.FINAL
            is LnUrlError.InvalidLnUrlTag -> ErrorViewKind.FINAL
            is LnUrlError.ExpiredInvoice -> ErrorViewKind.FINAL
            // Made it an explicit and comprehensive list so we get a compiler hint when new enum
            // values are added (e.g instead of just using else)
        }
    }

    override fun loggingName(): ERROR_TYPE = with(this@asViewModel) {
        when (this) {
            is LnUrlError.InvalidCode -> ERROR_TYPE.LNURL_INVALID_CODE
            is LnUrlError.InvalidLnUrlTag -> ERROR_TYPE.LNURL_INVALID_TAG
            is LnUrlError.Unresponsive -> ERROR_TYPE.LNURL_UNRESPONSIVE
            is LnUrlError.Unknown -> ERROR_TYPE.LNURL_UNKNOWN_ERROR
            is LnUrlError.ExpiredInvoice -> ERROR_TYPE.LNURL_EXPIRED_INVOICE
        }
    }

    override fun title(): String = with(this@asViewModel) {
        when (this) {

            is LnUrlError.InvalidCode ->
                ctx.getString(R.string.error_invalid_lnurl_title)

            is LnUrlError.InvalidLnUrlTag ->
                ctx.getString(R.string.error_invalid_lnurl_tag_title)

            is LnUrlError.Unresponsive ->
                ctx.getString(R.string.error_lnurl_service_unresponsive_title, this.domain)

            is LnUrlError.Unknown ->
                ctx.getString(R.string.error_lnurl_unknown_title)

            is LnUrlError.ExpiredInvoice ->
                ctx.getString(R.string.error_lnurl_expired_title)
        }
    }

    override val description: StringResWithArgs
        get() = with(this@asViewModel) {
            when (this) {
                is LnUrlError.InvalidCode ->
                    StringResWithArgs(R.string.error_invalid_lnurl_desc, arrayOf(this.lnUrl))

                is LnUrlError.InvalidLnUrlTag ->
                    StringResWithArgs(R.string.error_invalid_lnurl_tag_desc, arrayOf(this.lnUrl))

                is LnUrlError.Unresponsive ->
                    StringResWithArgs(R.string.error_lnurl_service_unresponsive_desc)

                is LnUrlError.Unknown ->
                    StringResWithArgs(R.string.error_lnurl_unknown_desc)

                is LnUrlError.ExpiredInvoice ->
                    StringResWithArgs(
                        R.string.error_lnurl_expired_desc,
                        arrayOf(this.domain, this.invoice)
                    )
            }
        }
}