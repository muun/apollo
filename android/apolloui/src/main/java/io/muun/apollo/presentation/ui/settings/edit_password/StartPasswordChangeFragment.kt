package io.muun.apollo.presentation.ui.settings.edit_password

import android.view.View
import android.widget.TextView
import butterknife.BindView
import butterknife.OnClick
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.utils.getStyledString
import io.muun.apollo.presentation.ui.view.MuunButton

class StartPasswordChangeFragment : BaseEditPasswordFragment<StartPasswordChangePresenter>() {

    @BindView(R.id.change_password_start_explanation)
    lateinit var explanation: TextView

    @BindView(R.id.change_password_start)
    lateinit var startButton: MuunButton

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.start_password_change_fragment
    }

    override fun initializeUi(view: View) {
        super.initializeUi(view)

        explanation.text = getStyledString(R.string.edit_password_explanation)
    }

    override fun onResume() {
        super.onResume()
        startButton.isEnabled = true
    }

    @OnClick(R.id.change_password_start)
    fun onStartButtonClick() {
        startButton.isEnabled = false // avoid double tap while preparing next Fragment
        presenter.start()
    }
}
