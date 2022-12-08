package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import butterknife.BindColor
import butterknife.BindView
import io.muun.apollo.R

class PickerCard @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0) :
    MuunView(c, a, s) {

    enum class Status {
        NORMAL,
        SELECTED,
        DISABLED
    }

    @BindView(R.id.title)
    internal lateinit var titleView: TextView

    @BindView(R.id.description)
    internal lateinit var descriptionView: TextView

    @BindColor(R.color.text_primary_color)
    @JvmField
    internal var textPrimaryColor = 0

    @BindColor(R.color.picker_disabled_color)
    @JvmField
    internal var disabledTintColor = 0

    override val layoutResource: Int
        get() = R.layout.muun_picker_card

    var title: CharSequence
        get() = titleView.text
        set(text) {
            titleView.text = text
        }

    var description: CharSequence
        get() = descriptionView.text
        set(text) {
            descriptionView.text = text
        }

    var status: Status = Status.NORMAL
        set(newStatus) {
            isEnabled = (newStatus != Status.DISABLED)
            isSelected = (newStatus == Status.SELECTED)
            isClickable = isEnabled

            titleView.setTextColor(if (isEnabled) textPrimaryColor else disabledTintColor)

            field = newStatus
        }
}