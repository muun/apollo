package io.muun.apollo.presentation.ui.lnurl.withdraw

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.ActivityLnurlWithdrawBinding
import io.muun.apollo.databinding.FragmentRecoveryToolBinding
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.model.LnUrlWithdraw
import io.muun.apollo.domain.model.lnurl.LnUrlError
import io.muun.apollo.domain.model.lnurl.LnUrlState
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity
import io.muun.apollo.presentation.ui.fragments.error.ErrorFragmentDelegate
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.postDelayed
import io.muun.apollo.presentation.ui.view.LoadingView
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.common.utils.Preconditions

class LnUrlWithdrawActivity: SingleFragmentActivity<LnUrlWithdrawPresenter>(), LnUrlWithdrawView,
    ErrorFragmentDelegate {

    companion object {
        /**
         * For normal activity start.
         */
        fun getStartActivityIntent(context: Context, lnurl: String) =
            Intent(context, LnUrlWithdrawActivity::class.java)
                .putExtra(LnUrlWithdrawView.ARG_LNURL, lnurl)

        /**
         * For activity start from a notification.
         */
        fun getStartActivityIntent(ctx: Context, withdraw: LnUrlWithdraw, fail: Boolean = false) =
            Intent(ctx, LnUrlWithdrawActivity::class.java)
                .putExtra(LnUrlWithdrawView.ARG_LNURL, withdraw.lnUrl)
                .putExtra(LnUrlWithdrawView.ARG_LNURL_WITHDRAW, withdraw.serialize())
                .putExtra(LnUrlWithdrawView.ARG_LN_PAYMENT_FAILED, fail)

        val zebedeeCountryLink = "https://zebedee.io/countries/"
    }

    private val binding: ActivityLnurlWithdrawBinding
        get() = getBinding() as ActivityLnurlWithdrawBinding

    private val muunHeader: MuunHeader
        get() = binding.lnurlWithdrawHeader

    private val loadingView: LoadingView
        get() = binding.lnurlWithdrawLoading

    private val actionButton: MuunButton
        get() = binding.lnurlWithdrawAction

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.activity_lnurl_withdraw

    override fun bindingInflater(): (LayoutInflater) -> ViewBinding {
        return ActivityLnurlWithdrawBinding::inflate
    }

    override fun getHeader(): MuunHeader =
        muunHeader

    override fun initializeUi() {
        super.initializeUi()

        header.attachToActivity(this)
        header.showTitle(R.string.showqr_title)
        header.setNavigation(MuunHeader.Navigation.NONE)
        header.setElevated(true)
    }

    override fun handleWithdrawState(state: LnUrlState) {
        hideError()
        loadingView.resetViewState()

        when (state) {
            is LnUrlState.Contacting -> showContacting(state.domain)
            is LnUrlState.Receiving -> showReceiving(state.domain)
            is LnUrlState.TakingTooLong -> showIsTakingTooLong(state.domain)
            is LnUrlState.Failed -> showFailed(state.error)
            is LnUrlState.Success -> presenter.handleSuccess()

            else -> {
                // Ignore
            }
        }
    }

    override fun onBackPressed() {
        presenter.handleBack()
    }

    override fun handleErrorDescriptionClicked() {
        presenter.handleErrorDescriptionClicked()
    }

    override fun handleRetry(errorType: AnalyticsEvent.ERROR_TYPE) {
        // There's only one retriable error here
        Preconditions.checkArgument(errorType == AnalyticsEvent.ERROR_TYPE.LNURL_UNRESPONSIVE)
        hideError()
        presenter.handleRetry()
    }

    override fun handleSendReport() {
        presenter.handleSendReport()
    }

    private fun showContacting(domain: String) {
        loadingView.visibility = View.VISIBLE
        loadingView.setTitle(
            StyledStringRes(this, R.string.lnurl_withdraw_contacting).toCharSequence(domain)
        )
    }

    private fun showReceiving(domain: String) {
        loadingView.visibility = View.VISIBLE
        loadingView.setTitle(
            StyledStringRes(this, R.string.lnurl_withdraw_receiving).toCharSequence(domain)
        )
    }

    private fun showIsTakingTooLong(domain: String) {
        loadingView.visibility = View.VISIBLE
        loadingView.tintProgressBar(ContextCompat.getColor(this, R.color.red))
        loadingView.setTitle(
            StyledStringRes(this, R.string.lnurl_withdraw_taking_long).toCharSequence(domain)
        )
        loadingView.setDescription(
            StyledStringRes(this, R.string.lnurl_withdraw_taking_long_desc).toCharSequence(domain)
        )

        actionButton.visibility = View.VISIBLE
        actionButton.setOnClickListener {
            presenter.goBackHome()
        }
    }

    private fun showFailed(error: LnUrlError) {
        if (error is LnUrlError.Unresponsive) {
            // Sometimes this error is triggered super quickly on retries, making the UI kinda
            // glitchy, jumping from one state to another. So, we add a small delay to avoid
            // glitches on retries
            postDelayed(1000) {
                showError(error.asViewModel(this))
            }
            return
        }

        showError(error.asViewModel(this))
    }
}