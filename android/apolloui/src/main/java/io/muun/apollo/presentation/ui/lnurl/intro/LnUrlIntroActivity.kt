package io.muun.apollo.presentation.ui.lnurl.intro

import android.content.Context
import android.content.Intent
import butterknife.BindView
import butterknife.OnClick
import io.muun.apollo.R
import io.muun.apollo.domain.model.OperationUri
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity
import io.muun.apollo.presentation.ui.view.MuunHeader

class LnUrlIntroActivity: SingleFragmentActivity<LnUrlIntroPresenter>() {

    companion object {
        fun getStartActivityIntent(context: Context) =
            Intent(context, LnUrlIntroActivity::class.java)
    }

    @BindView(R.id.lnurl_intro_header)
    lateinit var muunHeader: MuunHeader

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.activity_lnurl_intro

    override fun getHeader(): MuunHeader =
        muunHeader

    override fun initializeUi() {
        super.initializeUi()

        header.attachToActivity(this)
        header.showTitle(R.string.home_footer_action_receive)
        header.setNavigation(MuunHeader.Navigation.BACK)
        header.setElevated(true)
    }

    @OnClick(R.id.lnurl_intro_action)
    fun onContinueClick() {
        presenter.continueWithLnUrlFlow()
    }
}
