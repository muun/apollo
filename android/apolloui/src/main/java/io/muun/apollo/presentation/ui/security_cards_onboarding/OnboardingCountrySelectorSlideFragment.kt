package io.muun.apollo.presentation.ui.security_cards_onboarding

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.muun.apollo.databinding.FragmentOnboardingCountrySelectorSlideBinding
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.CountryInfo
import io.muun.apollo.presentation.ui.security_cards_onboarding.models.OnboardingSlide
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OnboardingCountrySelectorSlideFragment : Fragment() {

    interface Listener {
        fun onCountryPickerClick()
    }

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_DESCRIPTION = "arg_description"

        fun newInstance(slide: OnboardingSlide.CountrySelector) =
            OnboardingCountrySelectorSlideFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TITLE, slide.titleRes)
                    putInt(ARG_DESCRIPTION, slide.descriptionRes)
                }
            }
    }

    private var _binding: FragmentOnboardingCountrySelectorSlideBinding? = null
    private val binding get() = _binding!!

    private val countrySelectionViewModel: CountrySelectionSharedViewModel by activityViewModels()

    private var listener: Listener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentOnboardingCountrySelectorSlideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textViewTitle.setText(requireArguments().getInt(ARG_TITLE))
        binding.textViewDescription.setText(requireArguments().getInt(ARG_DESCRIPTION))
        binding.viewTextInputLayoutCountryOverlay.setOnClickListener {
            listener?.onCountryPickerClick()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                countrySelectionViewModel.selectedCountryInfo.collectLatest(::handleSelectedCountry)
            }
        }
    }

    private fun handleSelectedCountry(countryInfo: CountryInfo?) {
        binding.textInputEditTextCountry.setText(countryInfo?.let { "${countryInfo.flagEmoji} ${countryInfo.name}" })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        listener = null
    }
}
