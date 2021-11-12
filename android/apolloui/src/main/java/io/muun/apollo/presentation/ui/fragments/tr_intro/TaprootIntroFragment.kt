package io.muun.apollo.presentation.ui.fragments.tr_intro

import io.muun.apollo.R
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.fragments.flow_intro.FlowIntroFragment
import io.muun.apollo.presentation.ui.fragments.flow_intro.FlowIntroView
import rx.functions.Action0

class TaprootIntroFragment: FlowIntroFragment<
        FlowIntroView,
        TaprootIntroPresenter,
        TaprootIntroParentPresenter>() {

    override fun inject() =
        component.inject(this)

    override fun getPager() =
        TaprootIntroPager(childFragmentManager)

    override fun getConfirmLabel() =
        R.string.tr_setup_intro_action

    override fun onBackPressed(): Boolean =
    // Unlike the basic introduction back handling, which goes back to previous slides, here
        // we display an abort dialog in every step except the first.
        if (currentPosition > 0) {
            showAbortDialog()
            true

        } else {
            false
        }

    private fun showAbortDialog() {
        val dialog = MuunDialog.Builder()
            .title(R.string.tr_setup_intro_abort_title)
            .message(R.string.tr_setup_intro_abort_desc)
            .positiveButton(R.string.leave, Action0 { presenter.abortIntroduction() })
            .negativeButton(R.string.stay, null)
            .build()

        showDialog(dialog)
    }

}