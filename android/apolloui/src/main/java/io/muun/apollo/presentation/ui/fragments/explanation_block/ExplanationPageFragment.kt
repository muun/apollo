package io.muun.apollo.presentation.ui.fragments.explanation_block

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import butterknife.BindView
import io.muun.apollo.R
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

    @BindView(R.id.image)
    lateinit var imageView: ImageView

    @BindView(R.id.title)
    lateinit var titleView: TextView

    @BindView(R.id.body)
    lateinit var bodyView: TextView

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.fragment_explanation_page

    override fun initializeUi(view: View) {
        imageView.setImageResource(argumentsBundle.getInt(IMAGE_ARG))
        titleView.setText(argumentsBundle.getInt(TITLE_ARG))
        bodyView.text = getStyledBody()
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