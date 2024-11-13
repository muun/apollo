package io.muun.apollo.presentation.ui.lnurl.intro

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.ActivityLnurlIntroBinding
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity
import io.muun.apollo.presentation.ui.view.MuunHeader

class LnUrlIntroActivity : SingleFragmentActivity<LnUrlIntroPresenter>() {

    companion object {
        fun getStartActivityIntent(context: Context) =
            Intent(context, LnUrlIntroActivity::class.java)
    }

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.activity_lnurl_intro

    override fun bindingInflater(): (LayoutInflater) -> ViewBinding {
        return ActivityLnurlIntroBinding::inflate
    }

    private val binding: ActivityLnurlIntroBinding
        get() = getBinding() as ActivityLnurlIntroBinding

    override fun getHeader(): MuunHeader = binding.lnurlIntroHeader

    override fun initializeUi() {
        super.initializeUi()

        header.attachToActivity(this)
        header.showTitle(R.string.home_footer_action_receive)
        header.setNavigation(MuunHeader.Navigation.BACK)
        header.setElevated(true)

        binding.lnurlIntroAction.setOnClickListener {
            onContinueClick()
        }
    }


    private fun onContinueClick() {
        presenter.continueWithLnUrlFlow()
    }
}
