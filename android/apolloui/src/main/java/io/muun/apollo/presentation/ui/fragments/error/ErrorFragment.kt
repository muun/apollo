package io.muun.apollo.presentation.ui.fragments.error

import android.view.View
import android.widget.TextView
import butterknife.BindView
import butterknife.OnClick
import io.muun.apollo.R
import io.muun.apollo.domain.utils.applyArgs
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.fragments.error.ErrorViewModel.*
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.common.utils.Preconditions

class ErrorFragment: SingleFragment<ErrorFragmentPresenter>(), ErrorView {

    companion object {
        const val VIEW_MODEL_ARG = "view_model"

        @JvmStatic
        fun create(errorViewModel: ErrorViewModel) =
            ErrorFragment().applyArgs {
                putString(VIEW_MODEL_ARG, errorViewModel.serialize())
            }
    }

    @BindView(R.id.title)
    lateinit var titleView: TextView

    @BindView(R.id.description)
    lateinit var descriptionView: TextView

    @BindView(R.id.primary_button)
    lateinit var primaryButton: MuunButton

    @BindView(R.id.secondary_button)
    lateinit var secondaryButton: MuunButton

    private lateinit var viewModel: ErrorViewModel
    private lateinit var delegate: ErrorFragmentDelegate

    override fun getLayoutResource() =
        R.layout.error_fragment

    override fun inject() =
        component.inject(this)

    override fun setViewModel(viewModel: ErrorViewModel) {
        this.viewModel = viewModel

        titleView.text = viewModel.title()
        descriptionView.text = viewModel.description(requireContext())
        descriptionView.setOnClickListener {
            handleDescriptionClicked()
        }

        when (viewModel.kind()) {

            ErrorViewKind.RETRYABLE -> {
                primaryButton.setText(R.string.retry)
                primaryButton.setOnClickListener { handleRetry() }
                setUpSecondaryButton()
            }

            ErrorViewKind.REPORTABLE -> {
                primaryButton.setText(R.string.send_report)
                primaryButton.setOnClickListener { handleSendReport() }
                setUpSecondaryButton()
            }

            ErrorViewKind.FINAL -> {
                // This is the default view state
                primaryButton.setOnClickListener { presenter.goHomeInDefeat() }
            }
        }
    }

    fun setDelegate(delegate: ErrorFragmentDelegate) {
        this.delegate = delegate
    }

    private fun setUpSecondaryButton() {
        secondaryButton.visibility = View.VISIBLE
        secondaryButton.setText(R.string.error_op_action)
        secondaryButton.setOnClickListener {
            presenter.goHomeInDefeat()
        }
    }

    private fun handleDescriptionClicked() {
        Preconditions.checkArgument(::delegate.isInitialized, "ErrorFragmentDelegate not set")
        delegate.handleErrorDescriptionClicked()
    }

    private fun handleRetry() {
        Preconditions.checkArgument(::delegate.isInitialized, "ErrorFragmentDelegate not set")
        delegate.handleRetry()
    }

    private fun handleSendReport() {
        Preconditions.checkArgument(::delegate.isInitialized, "ErrorFragmentDelegate not set")
        delegate.handleSendReport()
    }
}