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

    @BindView(R.id.taproot_success_illustration)
    lateinit var imageView: ImageView

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
        super.initializeUi(view)
        parentActivity.header.visibility = View.GONE

        confirmButton.setOnClickListener {
            finishActivity()
        }
    }

    override fun getLayoutResource() =
        R.layout.taproot_success_fragment

    override fun setState(state: TaprootSuccessView.State) {
        blockClock.value = state.blocksToTaproot

        when (state.taprootStatus) {
            UserActivatedFeatureStatus.ACTIVE -> {
                descriptionView.setText(R.string.taproot_success_active_desc)
                blockClock.visibility = View.GONE

                imageView.setImageResource(R.drawable.taproot_rocket3)
            }
            else -> {
                descriptionView.setStyledText(
                    R.string.taproot_success_preactivated_desc,
                    state.blocksToTaproot,
                    state.hoursToTaproot
                )

                blockClock.visibility = View.VISIBLE

                imageView.setImageResource(R.drawable.taproot_rocket)
            }
        }
    }

    override fun onBackPressed(): Boolean {
        finishActivity()
        return true
    }
}