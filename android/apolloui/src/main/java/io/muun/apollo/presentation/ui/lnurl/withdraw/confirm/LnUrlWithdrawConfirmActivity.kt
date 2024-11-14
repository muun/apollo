package io.muun.apollo.presentation.ui.lnurl.withdraw.confirm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.ActivityLnurlWithdrawConfirmBinding
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity
import io.muun.apollo.presentation.ui.view.MuunHeader

class LnUrlWithdrawConfirmActivity : SingleFragmentActivity<LnUrlWithdrawConfirmPresenter>() {

    companion object {
        const val ARG_LNURL = "lnurl"

        fun getStartActivityIntent(context: Context, lnurl: String) =
            Intent(context, LnUrlWithdrawConfirmActivity::class.java)
                .putExtra(ARG_LNURL, lnurl)

        fun getLnUrl(arguments: Bundle): String =
            arguments.getString(ARG_LNURL)!!
    }

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.activity_lnurl_withdraw_confirm

    override fun bindingInflater(): (LayoutInflater) -> ViewBinding {
        return ActivityLnurlWithdrawConfirmBinding::inflate
    }

    private val binding: ActivityLnurlWithdrawConfirmBinding
        get() = getBinding() as ActivityLnurlWithdrawConfirmBinding

    override fun getHeader(): MuunHeader =
        binding.lnurlWithdrawConfirmHeader

    private fun getBackButton() =
        binding.lnurlWithdrawConfirmBackAction

    private fun getConfirmButton() =
        binding.lnurlWithdrawConfirmAction


    override fun initializeUi() {
        super.initializeUi()

        header.attachToActivity(this)
        header.showTitle(R.string.showqr_title)
        header.setNavigation(MuunHeader.Navigation.BACK)
        header.setElevated(true)

        getConfirmButton().setOnClickListener {
            onContinueClick()
        }

        getBackButton().setOnClickListener {
            onBackClick()
        }

    }

    private fun onBackClick() {
        finishActivity()
    }

    private fun onContinueClick() {
        presenter.continueWithLnUrlFlow()
    }
}
