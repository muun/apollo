package io.muun.apollo.presentation.ui.fragments.tr_success

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.setStyledText
import io.muun.apollo.presentation.ui.view.BlockClock
import io.muun.apollo.presentation.ui.view.MuunButton

class TaprootSuccessFragment : SingleFragment<TaprootSuccessPresenter>(), TaprootSuccessView {

    @BindView(R.id.taproot_success_illustration_default)
    lateinit var defaultSuccessIllustration: ImageView

    @BindView(R.id.taproot_success_illustration_active)
    lateinit var taprootActiveSuccessIllustration: ImageView

    @BindView(R.id.block_clock)
    lateinit var blockClock: BlockClock

    @BindView(R.id.description)
    lateinit var descriptionView: TextView

    @BindView(R.id.confirm)
    lateinit var confirmButton: MuunButton

    override fun inject() {
        component.inject(this)
    }

    override fun initializeUi(view: View) {
        confirmButton.setOnClickListener {
            finishActivity()
        }
    }

    override fun setUpHeader() {
        parentActivity.header.visibility = View.GONE
    }

    override fun getLayoutResource() =
        R.layout.taproot_success_fragment

    override fun setState(state: TaprootSuccessView.State) {
        blockClock.value = state.blocksToTaproot

        when (state.taprootStatus) {
            UserActivatedFeatureStatus.ACTIVE -> {
                descriptionView.setText(R.string.taproot_success_active_desc)
                blockClock.visibility = View.GONE
                defaultSuccessIllustration.visibility = View.GONE
                taprootActiveSuccessIllustration.visibility = View.VISIBLE

            }
            else -> {
                descriptionView.setStyledText(
                    R.string.taproot_success_preactivated_desc,
                    state.blocksToTaproot,
                    state.hoursToTaproot
                )

                blockClock.visibility = View.VISIBLE
                defaultSuccessIllustration.visibility = View.VISIBLE
                taprootActiveSuccessIllustration.visibility = View.GONE


            }
        }
    }

    override fun onBackPressed(): Boolean {
        finishActivity()
        return true
    }
}