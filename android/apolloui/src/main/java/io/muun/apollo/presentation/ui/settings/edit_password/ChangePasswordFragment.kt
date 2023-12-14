package io.muun.apollo.presentation.ui.settings.edit_password

import android.view.View
import android.widget.CheckBox
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.base.BaseActivity
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunTextInput

class ChangePasswordFragment : SingleFragment<ChangePasswordPresenter>(), ChangePasswordView {

    @BindView(R.id.change_password)
    lateinit var password: MuunTextInput

    @BindView(R.id.change_password_condition)
    lateinit var condition: CheckBox

    @BindView(R.id.change_password_continue)
    lateinit var continueButton: MuunButton

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.change_password_fragment
    }

    override fun initializeUi(view: View) {
        password.setPasswordRevealEnabled(true)
        continueButton.isEnabled = false

        condition.setOnCheckedChangeListener { _, _ -> onConditionCheckedChanged() }
        continueButton.setOnClickListener { onContinueButtonClick() }
    }

    override fun onBackPressed(): Boolean {
        val muunDialog = MuunDialog.Builder()
            .title(R.string.change_password_abort_dialog_title)
            .message(R.string.change_password_abort_dialog_message)
            .positiveButton(R.string.change_password_abort_dialog_yes) { abort() }
            .negativeButton(R.string.change_password_abort_dialog_no, null)
            .build()

        parentActivity.showDialog(muunDialog)
        return true
    }

    private fun abort() {
        safeGetParentActivity().ifPresent(BaseActivity<*>::finish)
    }

    override fun setLoading(loading: Boolean) {
        continueButton.setLoading(loading)
        condition.isEnabled = !loading
    }

    override fun setPasswordError(error: UserFacingError?) {
        password.clearError()
        if (error != null) {
            password.setError(error)
            password.requestFocusInput()
        }
    }

    override fun onResume() {
        super.onResume()
        password.requestFocusInput()
    }

    override fun blockScreenshots(): Boolean {
        return true
    }

    private fun onConditionCheckedChanged() {
        continueButton.isEnabled = condition.isChecked
    }

    private fun onContinueButtonClick() {
        presenter.submitPassword(password.text.toString())
    }
}