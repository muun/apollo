package io.muun.apollo.presentation.ui.high_fees

import android.content.Context
import android.content.Intent
import android.graphics.Color
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.base.BaseActivity
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.view.MuunHeader

class HighFeesExplanationActivity : BaseActivity<BasePresenter<BaseView>>(), BaseView {

    companion object {
        fun getStartActivityIntent(context: Context) =
            Intent(context, HighFeesExplanationActivity::class.java)
    }

    @BindView(R.id.muun_header)
    lateinit var header: MuunHeader

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.activity_high_fees
    }

    override fun initializeUi() {
        super.initializeUi()
        header.attachToActivity(this)
        header.setBackgroundColor(Color.TRANSPARENT)
        header.hideTitle()
        header.setNavigation(MuunHeader.Navigation.BACK)
    }
}