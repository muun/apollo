package io.muun.apollo.presentation.ui.lnurl.withdraw.confirm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import butterknife.BindView
import butterknife.OnClick
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity
import io.muun.apollo.presentation.ui.view.MuunHeader

class LnUrlWithdrawConfirmActivity: SingleFragmentActivity<LnUrlWithdrawConfirmPresenter>() {

    companion object {
        const val ARG_LNURL = "lnurl"

        fun getStartActivityIntent(context: Context, lnurl: String) =
            Intent(context, LnUrlWithdrawConfirmActivity::class.java)
                .putExtra(ARG_LNURL, lnurl)

        fun getLnUrl(arguments: Bundle): String =
            arguments.getString(ARG_LNURL)!!
    }

    @BindView(R.id.lnurl_withdraw_confirm_header)
    lateinit var muunHeader: MuunHeader

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.activity_lnurl_withdraw_confirm

    override fun getHeader(): MuunHeader =
        muunHeader

    override fun initializeUi() {
        super.initializeUi()

        header.attachToActivity(this)
        header.showTitle(R.string.showqr_title)
        header.setNavigation(MuunHeader.Navigation.BACK)
        header.setElevated(true)
    }

    @OnClick(R.id.lnurl_withdraw_confirm_back_action)
    fun onBackClick() {
        finishActivity()
    }

    @OnClick(R.id.lnurl_withdraw_confirm_action)
    fun onContinueClick() {
        presenter.continueWithLnUrlFlow()
    }
}
