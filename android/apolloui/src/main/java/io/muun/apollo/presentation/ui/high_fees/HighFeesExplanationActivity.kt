package io.muun.apollo.presentation.ui.high_fees

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.ActivityHighFeesBinding
import io.muun.apollo.presentation.ui.base.BaseActivity
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.view.MuunHeader

class HighFeesExplanationActivity : BaseActivity<BasePresenter<BaseView>>(), BaseView {

    companion object {
        fun getStartActivityIntent(context: Context) =
            Intent(context, HighFeesExplanationActivity::class.java)
    }

    private val binding: ActivityHighFeesBinding
        get() = getBinding() as ActivityHighFeesBinding

    private val header: MuunHeader
        get() = binding.muunHeader

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.activity_high_fees
    }

    override fun bindingInflater(): (LayoutInflater) -> ViewBinding {
        return ActivityHighFeesBinding::inflate
    }

    override fun initializeUi() {
        super.initializeUi()
        header.attachToActivity(this)
        header.setBackgroundColor(Color.TRANSPARENT)
        header.hideTitle()
        header.setNavigation(MuunHeader.Navigation.BACK)
    }
}