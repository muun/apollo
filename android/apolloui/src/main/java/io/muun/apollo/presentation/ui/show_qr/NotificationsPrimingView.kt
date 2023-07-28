package io.muun.apollo.presentation.ui.show_qr

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunView

class NotificationsPrimingView : MuunView {

    @BindView(R.id.priming_notifications_title)
    lateinit var title: TextView

    @BindView(R.id.priming_notifications_desc)
    lateinit var description: TextView

    @BindView(R.id.priming_notifications_enable)
    lateinit var enableButton: MuunButton

    @BindView(R.id.priming_notifications_skip)
    lateinit var skipButton: MuunButton

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    override val layoutResource: Int
        get() = R.layout.view_notifications_priming

    fun setUpForFirstTime() {
        title.setText(R.string.priming_notifications_title)
        description.setText(R.string.priming_notifications_desc)
    }

    fun setUpForLightning() {
        skipButton.visibility = View.GONE
        title.setText(R.string.priming_notifications_skipped_title)
        description.setText(R.string.priming_notifications_skipped_desc)
    }

    fun setEnableClickListener(listener: OnClickListener) {
        enableButton.setOnClickListener(listener)
    }

    fun setSkipClickListener(listener: OnClickListener) {
        skipButton.setOnClickListener(listener)
    }
}