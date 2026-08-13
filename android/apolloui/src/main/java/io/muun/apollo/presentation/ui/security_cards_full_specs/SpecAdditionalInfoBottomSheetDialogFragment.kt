package io.muun.apollo.presentation.ui.security_cards_full_specs

import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import androidx.core.text.HtmlCompat
import io.muun.apollo.databinding.BottomSheetSpecAdditionalInfoBinding
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.AdditionalInfo
import io.muun.apollo.presentation.ui.view.MuunBottomSheetDialogFragment

class SpecAdditionalInfoBottomSheetDialogFragment : MuunBottomSheetDialogFragment() {

    companion object {
        private const val ARG_INFO = "info"

        fun newInstance(info: AdditionalInfo) =
            SpecAdditionalInfoBottomSheetDialogFragment().apply {
                arguments = android.os.Bundle().apply {
                    putParcelable(ARG_INFO, info)
                }
            }
    }

    override fun createContentView(): View {
        val binding = BottomSheetSpecAdditionalInfoBinding.inflate(LayoutInflater.from(requireContext()))
        val info = requireNotNull(requireArguments().getParcelable<AdditionalInfo>(ARG_INFO))

        binding.imageViewIcon.setImageResource(info.iconRes)
        binding.textViewTitle.text = info.title
        binding.textViewBody.text =
            HtmlCompat.fromHtml(info.bodyHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
        binding.textViewBody.movementMethod = LinkMovementMethod.getInstance()

        return binding.root
    }
}
