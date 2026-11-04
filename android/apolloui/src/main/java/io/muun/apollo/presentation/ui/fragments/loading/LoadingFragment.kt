package io.muun.apollo.presentation.ui.fragments.loading

import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.viewbinding.ViewBinding
import io.muun.apollo.databinding.LoadingViewBinding
import io.muun.apollo.domain.utils.applyArgs
import io.muun.apollo.presentation.ui.base.SingleFragment

class LoadingFragment : SingleFragment<LoadingFragmentPresenter>() {

    companion object {
        const val HUMAN_INSTANT_IN_MILLISECONDS = 200L
        const val MESSAGE_ARG = "message"

        @JvmStatic
        fun create(@StringRes messageRes: Int) =
            LoadingFragment().applyArgs {
                putInt(MESSAGE_ARG, messageRes)
            }
    }

    private val binding: LoadingViewBinding
        get() = getBinding() as LoadingViewBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return LoadingViewBinding::inflate
    }

    override fun inject() =
        component.inject(this)

    override fun initializeUi(view: View) {
        binding.loadingViewTitle.text = getString(argumentsBundle.getInt(MESSAGE_ARG))

        // Whenever this fragment is used, we want to avoid spinner flickers when the loading is
        // completed instantly:
        view.visibility = View.GONE
        Handler().postDelayed({ view.visibility = View.VISIBLE }, HUMAN_INSTANT_IN_MILLISECONDS)
    }
}