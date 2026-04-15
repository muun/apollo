package io.muun.apollo.presentation.ui.security_cards_onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.muun.apollo.databinding.FragmentOnboardingExplanatorySlideBinding
import io.muun.apollo.presentation.ui.security_cards_onboarding.models.OnboardingSlide

class OnboardingExplanatorySlideFragment : Fragment() {

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_DESCRIPTION = "arg_description"

        fun newInstance(slide: OnboardingSlide.Explanatory) =
            OnboardingExplanatorySlideFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TITLE, slide.titleRes)
                    putInt(ARG_DESCRIPTION, slide.descriptionRes)
                }
            }
    }

    private var _binding: FragmentOnboardingExplanatorySlideBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentOnboardingExplanatorySlideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textViewTitle.setText(requireArguments().getInt(ARG_TITLE))
        binding.textViewDescription.setText(requireArguments().getInt(ARG_DESCRIPTION))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
