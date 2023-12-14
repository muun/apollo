package io.muun.apollo.presentation.ui.fragments.verify_email

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.app.Email
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.view.HtmlTextView
import io.muun.apollo.presentation.ui.view.LoadingView
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunHeader

class VerifyEmailFragment : SingleFragment<VerifyEmailPresenter>(), VerifyEmailView {

    @BindView(R.id.open_email_client)
    lateinit var openEmailClientButton: MuunButton

    @BindView(R.id.verify_email_title)
    lateinit var titleView: TextView

    @BindView(R.id.verify_email_description)
    lateinit var descriptionView: HtmlTextView

    @BindView(R.id.verify_email_icon)
    lateinit var emailIcon: ImageView

    @BindView(R.id.verify_email_loading)
    lateinit var loadingView: LoadingView

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource() =
        R.layout.fragment_verify_email

    override fun initializeUi(view: View) {
        openEmailClientButton.isEnabled = Email.hasEmailAppInstalled(requireContext())
        openEmailClientButton.setOnClickListener { presenter.openEmailClient() }

    }

    override fun setEmail(email: String) {
        descriptionView.text = StyledStringRes(requireContext(), R.string.verify_email_subtitle)
            .toCharSequence(email)
    }

    override fun onBackPressed(): Boolean {
        presenter!!.goBack()
        return true
    }

    override fun setUpHeader() {
        parentActivity.header.setNavigation(MuunHeader.Navigation.BACK)
    }

    override fun setLoading(loading: Boolean) {
        loadingView.visibility = if (loading) View.VISIBLE else View.GONE
        titleView.visibility = if (!loading) View.VISIBLE else View.GONE
        descriptionView.visibility = if (!loading) View.VISIBLE else View.GONE
        emailIcon.visibility = if (!loading) View.VISIBLE else View.GONE
    }

    override fun handleInvalidLinkError() {
        setLoading(false)
        emailIcon.setImageResource(R.drawable.ic_envelope_error)
        titleView.setText(R.string.email_link_error_title)
        descriptionView.setText(R.string.verify_email_link_invalid)
    }

    override fun handleExpiredLinkError() {
        setLoading(false)
        emailIcon.setImageResource(R.drawable.ic_envelope_error)
        titleView.setText(R.string.email_link_error_title)
        descriptionView.setText(R.string.verify_email_link_expired)
    }
}