package io.muun.apollo.presentation.ui.fragments.loading

import android.os.Handler
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import butterknife.BindView
import io.muun.apollo.R
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

    @BindView(R.id.loading_view_title)
    lateinit var titleView: TextView

    override fun getLayoutResource() =
        R.layout.loading_view

    override fun inject() =
        component.inject(this)

    override fun initializeUi(view: View) {
        titleView.text = getString(argumentsBundle.getInt(MESSAGE_ARG))

        // Whenever this fragment is used, we want to avoid spinner flickers when the loading is
        // completed instantly:
        view.visibility = View.GONE
        Handler().postDelayed({ view.visibility = View.VISIBLE }, HUMAN_INSTANT_IN_MILLISECONDS)
    }
}