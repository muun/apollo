package io.muun.apollo.presentation.ui.fragments.explanation_block

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.FragmentExplanationPageBinding
import io.muun.apollo.domain.utils.applyArgs
import io.muun.apollo.presentation.ui.base.BaseFragment
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.utils.StyledStringRes

/**
 * A Fragment with explanatory information, containing an image, a title and a body.
 */
open class ExplanationPageFragment : BaseFragment<ExplanationPagePresenter>() {

    companion object {
        const val IMAGE_ARG = "message"
        const val TITLE_ARG = "title"
        const val BODY_ARG = "body"

        @JvmStatic
        fun create(@DrawableRes imageRes: Int, @StringRes titleRes: Int, @StringRes bodyRes: Int) =
            ExplanationPageFragment().applyArgs {
                putInt(IMAGE_ARG, imageRes)
                putInt(TITLE_ARG, titleRes)
                putInt(BODY_ARG, bodyRes)
            }
    }

    private val binding: FragmentExplanationPageBinding
        get() = getBinding() as FragmentExplanationPageBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return FragmentExplanationPageBinding::inflate
    }

    override fun inject() =
        component.inject(this)

    override fun initializeUi(view: View) {
        binding.image.setImageResource(argumentsBundle.getInt(IMAGE_ARG))
        binding.title.setText(argumentsBundle.getInt(TITLE_ARG))
        binding.body.text = getStyledBody()
    }

    private fun onLinkClick(id: String) {
        showWhyEncryptedDrawer()
    }

    private fun showWhyEncryptedDrawer() {
        val dialog = TitleAndDescriptionDrawer()
        dialog.setTitle(R.string.export_keys_intro_why_encrypted_title)
        dialog.setDescription(getString(R.string.export_keys_intro_why_encrypted_body))
        showDrawerDialog(dialog)
    }

    private fun getStyledBody() =
        StyledStringRes(requireContext(), argumentsBundle.getInt(BODY_ARG), this::onLinkClick)
            .toCharSequence()
}