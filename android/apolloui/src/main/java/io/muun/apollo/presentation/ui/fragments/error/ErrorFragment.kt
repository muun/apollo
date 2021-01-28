package io.muun.apollo.presentation.ui.fragments.error

import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import butterknife.BindView
import butterknife.OnClick
import io.muun.apollo.R
import io.muun.apollo.domain.utils.applyArgs
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.getStyledString

class ErrorFragment: SingleFragment<ErrorFragmentPresenter>() {

    companion object {
        const val TITLE_ARG = "title"
        const val DESCRIPTION_ARG = "description"
        const val DESCRIPTION_ARGS_ARG = "description_args"

        @JvmStatic
        fun create(@StringRes titleRes: Int, @StringRes descriptionRes: Int) =
            ErrorFragment().applyArgs {
                putInt(TITLE_ARG, titleRes)
                putInt(DESCRIPTION_ARG, descriptionRes)
            }

        @JvmStatic
        fun create(@StringRes titleRes: Int, @StringRes descriptionRes: Int, vararg args: String) =
            create(titleRes, descriptionRes).applyArgs {
                putStringArray(DESCRIPTION_ARGS_ARG, args)
            }
    }

    @BindView(R.id.title)
    lateinit var titleView: TextView

    @BindView(R.id.description)
    lateinit var descriptionView: TextView

    override fun getLayoutResource() =
        R.layout.error_fragment

    override fun inject() =
        component.inject(this)

    override fun initializeUi(view: View?) {
        super.initializeUi(view)

        titleView.text = getString(argumentsBundle.getInt(TITLE_ARG))

        val descriptionRes = argumentsBundle.getInt(DESCRIPTION_ARG)
        val dynamicArguments = argumentsBundle.getStringArray(DESCRIPTION_ARGS_ARG) ?: arrayOf()

        descriptionView.text = getStyledString(
            descriptionRes,
            *dynamicArguments
        )
    }

    @OnClick(R.id.exit)
    fun onExitClick() {
        presenter.goHomeInDefeat()
    }
}