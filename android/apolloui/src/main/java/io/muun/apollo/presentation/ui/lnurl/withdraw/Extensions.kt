package io.muun.apollo.presentation.ui.lnurl.withdraw

import android.content.Context
import io.muun.apollo.R
import io.muun.apollo.domain.analytics.AnalyticsEvent.ERROR_TYPE
import io.muun.apollo.domain.model.lnurl.LnUrlError
import io.muun.apollo.presentation.ui.fragments.error.ErrorViewModel
import io.muun.apollo.presentation.ui.fragments.error.ErrorViewModel.ErrorViewKind
import io.muun.apollo.presentation.ui.utils.StyledStringRes.StringResWithArgs

fun LnUrlError.asViewModel(ctx: Context) = object : ErrorViewModel {

    override fun kind(): ErrorViewKind = with(this@asViewModel) {
        when (this) {
            is LnUrlError.Unresponsive -> ErrorViewKind.RETRYABLE
            is LnUrlError.Unknown -> ErrorViewKind.REPORTABLE
            is LnUrlError.InvalidCode -> ErrorViewKind.FINAL
            is LnUrlError.InvalidLnUrlTag -> ErrorViewKind.FINAL
            is LnUrlError.ExpiredInvoice -> ErrorViewKind.FINAL
            is LnUrlError.ExpiredLnUrl -> ErrorViewKind.FINAL
            is LnUrlError.NoWithdrawBalance -> ErrorViewKind.FINAL
            is LnUrlError.NoRoute -> ErrorViewKind.FINAL
            is LnUrlError.CountryNotSupported -> ErrorViewKind.FINAL
            is LnUrlError.AlreadyUsed -> ErrorViewKind.FINAL
            // Made it an explicit and comprehensive list so we get a compiler hint when new enum
            // values are added (e.g instead of just using else)
        }
    }

    override fun canGoBack(): Boolean =
        false

    override fun loggingName(): ERROR_TYPE = with(this@asViewModel) {
        when (this) {
            is LnUrlError.InvalidCode -> ERROR_TYPE.LNURL_INVALID_CODE
            is LnUrlError.InvalidLnUrlTag -> ERROR_TYPE.LNURL_INVALID_TAG
            is LnUrlError.Unresponsive -> ERROR_TYPE.LNURL_UNRESPONSIVE
            is LnUrlError.Unknown -> ERROR_TYPE.LNURL_UNKNOWN_ERROR
            is LnUrlError.ExpiredInvoice -> ERROR_TYPE.LNURL_EXPIRED_INVOICE
            is LnUrlError.ExpiredLnUrl -> ERROR_TYPE.LNURL_REQUEST_EXPIRED
            is LnUrlError.NoWithdrawBalance -> ERROR_TYPE.LNURL_NO_BALANCE
            is LnUrlError.NoRoute -> ERROR_TYPE.LNURL_NO_ROUTE
            is LnUrlError.CountryNotSupported -> ERROR_TYPE.LNURL_COUNTRY_NOT_SUPPORTED
            is LnUrlError.AlreadyUsed -> ERROR_TYPE.LNURL_ALREADY_USED
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
                ctx.getString(R.string.error_lnurl_invoice_expired_title)

            is LnUrlError.ExpiredLnUrl ->
                ctx.getString(R.string.error_lnurl_expired_title)

            is LnUrlError.NoWithdrawBalance ->
                ctx.getString(R.string.error_lnurl_no_balance_title)

            is LnUrlError.NoRoute ->
                ctx.getString(R.string.error_lnurl_no_route_title)

            is LnUrlError.CountryNotSupported ->
                ctx.getString(R.string.error_lnurl_country_not_supported_title)

            is LnUrlError.AlreadyUsed ->
                ctx.getString(R.string.error_lnurl_already_used_title)
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
                    StringResWithArgs(
                        R.string.error_lnurl_unknown_desc,
                        arrayOf(this.event.truncatedMessage)
                    )

                is LnUrlError.ExpiredInvoice ->
                    StringResWithArgs(
                        R.string.error_lnurl_invoice_expired_desc,
                        arrayOf(this.domain, this.invoice)
                    )

                is LnUrlError.ExpiredLnUrl ->
                    StringResWithArgs(R.string.error_lnurl_expired_desc)

                is LnUrlError.NoWithdrawBalance ->
                    StringResWithArgs(R.string.error_lnurl_no_balance_desc)

                is LnUrlError.NoRoute ->
                    StringResWithArgs(
                        R.string.error_lnurl_no_route_desc,
                        arrayOf(this.domain, this.domain)
                    )

                is LnUrlError.CountryNotSupported ->
                    StringResWithArgs(R.string.error_lnurl_country_not_supported_desc)

                is LnUrlError.AlreadyUsed ->
                    StringResWithArgs(R.string.error_lnurl_already_used_desc)
            }
        }
}