package io.muun.apollo.presentation.ui.fragments.tr_success

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.TaprootSuccessFragmentBinding
import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.setStyledText

class TaprootSuccessFragment : SingleFragment<TaprootSuccessPresenter>(), TaprootSuccessView {

    private val binding: TaprootSuccessFragmentBinding
        get() = getBinding() as TaprootSuccessFragmentBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding =
        TaprootSuccessFragmentBinding::inflate

    override fun inject() {
        component.inject(this)
    }

    override fun initializeUi(view: View) {
        binding.confirm.setOnClickListener {
            finishActivity()
        }
    }

    override fun setUpHeader() {
        parentActivity.header.visibility = View.GONE
    }

    override fun setState(state: TaprootSuccessView.State) {
        binding.blockClock.value = state.blocksToTaproot

        when (state.taprootStatus) {
            UserActivatedFeatureStatus.ACTIVE -> {
                binding.description.setText(R.string.taproot_success_active_desc)
                binding.blockClock.visibility = View.GONE
                binding.taprootSuccessIllustrationDefault.visibility = View.GONE
                binding.taprootSuccessIllustrationActive.visibility = View.VISIBLE
            }
            else -> {
                binding.description.setStyledText(
                    R.string.taproot_success_preactivated_desc,
                    state.blocksToTaproot,
                    state.hoursToTaproot
                )

                binding.blockClock.visibility = View.VISIBLE
                binding.taprootSuccessIllustrationDefault.visibility = View.VISIBLE
                binding.taprootSuccessIllustrationActive.visibility = View.GONE
            }
        }
    }

    override fun onBackPressed(): Boolean {
        finishActivity()
        return true
    }
}