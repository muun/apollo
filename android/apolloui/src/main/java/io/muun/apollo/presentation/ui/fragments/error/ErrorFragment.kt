package io.muun.apollo.presentation.ui.fragments.error

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.ErrorFragmentBinding
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.utils.applyArgs
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.fragments.error.ErrorViewModel.ErrorViewKind
import io.muun.common.utils.Preconditions

class ErrorFragment : SingleFragment<ErrorFragmentPresenter>(), ErrorView {

    companion object {
        const val VIEW_MODEL_ARG = "view_model"
        private const val DESCRIPTION_CLICKABLE = "description_clickable"

        @JvmStatic
        fun create(errorViewModel: ErrorViewModel, descriptionClickable: Boolean = false) =
            ErrorFragment().applyArgs {
                putString(VIEW_MODEL_ARG, errorViewModel.serialize())
                putBoolean(DESCRIPTION_CLICKABLE, descriptionClickable)
            }
    }

    private val binding: ErrorFragmentBinding
        get() = getBinding() as ErrorFragmentBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return ErrorFragmentBinding::inflate
    }

    private lateinit var viewModel: ErrorViewModel
    private lateinit var delegate: ErrorFragmentDelegate

    override fun inject() =
        component.inject(this)

    override fun setViewModel(viewModel: ErrorViewModel) {
        this.viewModel = viewModel

        binding.title.text = viewModel.title()
        binding.description.text = viewModel.description(requireContext())
        if (argumentsBundle.getBoolean(DESCRIPTION_CLICKABLE)) {
            binding.description.setOnClickListener {
                handleDescriptionClicked()
            }
        }

        when (viewModel.kind()) {

            ErrorViewKind.RETRYABLE -> {
                binding.primaryButton.setText(R.string.retry)
                binding.primaryButton.setOnClickListener { handleRetry(viewModel.loggingName()) }
                setUpSecondaryButton()
            }

            ErrorViewKind.REPORTABLE -> {
                binding.primaryButton.setText(R.string.send_report)
                binding.primaryButton.setOnClickListener { handleSendReport() }
                setUpSecondaryButton()
            }

            ErrorViewKind.FINAL -> {
                // This is the default view state
                binding.primaryButton.setOnClickListener { presenter.goHomeInDefeat() }
            }
        }
    }

    fun setDelegate(delegate: ErrorFragmentDelegate) {
        this.delegate = delegate
    }

    private fun setUpSecondaryButton() {
        binding.secondaryButton.visibility = View.VISIBLE

        if (viewModel.canGoBack()) {
            binding.secondaryButton.setText(R.string.error_op_action_back)
            binding.secondaryButton.setOnClickListener {
                delegate.handleBack(viewModel.loggingName())
            }
        } else {
            binding.secondaryButton.setText(R.string.error_op_action)
            binding.secondaryButton.setOnClickListener {
                presenter.goHomeInDefeat()
            }
        }
    }

    private fun handleDescriptionClicked() {
        Preconditions.checkArgument(::delegate.isInitialized, "ErrorFragmentDelegate not set")
        delegate.handleErrorDescriptionClicked()
    }

    private fun handleRetry(errorType: AnalyticsEvent.ERROR_TYPE) {
        Preconditions.checkArgument(::delegate.isInitialized, "ErrorFragmentDelegate not set")
        delegate.handleRetry(errorType)
    }

    private fun handleSendReport() {
        Preconditions.checkArgument(::delegate.isInitialized, "ErrorFragmentDelegate not set")
        delegate.handleSendReport()
    }
}