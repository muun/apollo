package io.muun.apollo.presentation.ui.show_qr

import android.content.Context
import android.util.AttributeSet
import android.view.View
import io.muun.apollo.R
import io.muun.apollo.databinding.ViewNotificationsPrimingBinding
import io.muun.apollo.presentation.ui.view.BindingInflater
import io.muun.apollo.presentation.ui.view.MuunView

class NotificationsPrimingView : MuunView<ViewNotificationsPrimingBinding> {

    private val title get() = binding.primingNotificationsTitle
    private val description get() = binding.primingNotificationsDesc
    private val enableButton get() = binding.primingNotificationsEnable
    private val skipButton get() = binding.primingNotificationsSkip

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    override fun bindingInflater(): BindingInflater<ViewNotificationsPrimingBinding> {
        return ViewNotificationsPrimingBinding::inflate
    }

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