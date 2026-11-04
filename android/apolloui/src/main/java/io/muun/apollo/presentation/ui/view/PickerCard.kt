package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import io.muun.apollo.R
import io.muun.apollo.databinding.MuunPickerCardBinding

class PickerCard @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0) :
    MuunView<MuunPickerCardBinding>(c, a, s) {

    enum class Status {
        NORMAL,
        SELECTED,
        DISABLED
    }

    private val titleView get() = binding.title
    private val descriptionView get() = binding.description

    private val textPrimaryColor: Int
        get() = ContextCompat.getColor(context, R.color.text_primary_color)

    private val disabledTintColor: Int
        get() = ContextCompat.getColor(context, R.color.picker_disabled_color)

    override fun bindingInflater(): BindingInflater<MuunPickerCardBinding> {
        return MuunPickerCardBinding::inflate
    }

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