package io.muun.apollo.presentation.ui.fragments.landing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.LandingActivityBinding
import io.muun.apollo.presentation.ui.base.SingleFragment

class LandingFragment : SingleFragment<LandingPresenter>() {

    companion object {
        private const val ARG_SHOW_ANIMATION = "show_animation"

        @JvmStatic
        fun newInstance(): LandingFragment {
            return LandingFragment()
        }

        @JvmStatic
        fun newInstanceWithAnimation(): LandingFragment {
            val fragment = LandingFragment()
            fragment.arguments = Bundle().apply {
                putBoolean(ARG_SHOW_ANIMATION, true)
            }
            return fragment
        }
    }

    private val binding: LandingActivityBinding
        get() = getBinding() as LandingActivityBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return LandingActivityBinding::inflate
    }

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.landing_activity

    override fun initializeUi(view: View) {
        with(binding) {
            if (argumentsBundle.getBoolean(ARG_SHOW_ANIMATION)) {
                lottieView.setAnimation(R.raw.logo_animation)
                lottieView.setMinFrame(40) // Small arbitrary correction to json exported animation
            } else {
                lottieView.setImageResource(R.drawable.wordmark_blue_beta_big_top)
            }

            createWalletButton.setOnClickListener { onCreateWalletButtonClick() }
            recoverWalletButton.setOnClickListener { onRecoverWalletButtonClick() }
        }
    }

    private fun onCreateWalletButtonClick() {
        binding.createWalletButton.isEnabled = false // avoid double tap while preparing next Fragment
        presenter.startCreateWallet()
    }

    private fun onRecoverWalletButtonClick() {
        binding.recoverWalletButton.isEnabled = false // avoid double tap while preparing next Fragment
        presenter.startRecoverWallet()
    }

    override fun onBackPressed(): Boolean {
        finishActivity()
        return true
    }

    override fun setUpHeader() {
        parentActivity.header.clear()
    }
}