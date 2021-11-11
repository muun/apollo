package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.StringRes
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.utils.setTextAppearanceCompat

class RadioPicker @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0) :
    Picker<RadioPicker.Option>(c, a, s) {

    class Option(id: Int, val label: CharSequence, val isChecked: Boolean):
        Picker.Option(id)

    @BindView(R.id.picker_title)
    lateinit var titleView: TextView

    @BindView(R.id.picker_radio_group)
    lateinit var radioGroup: RadioGroup

    override val layoutResource: Int
        get() = R.layout.view_radio_picker

    fun setTitle(@StringRes resId: Int) {
        titleView.setText(resId)
        titleView.visibility = VISIBLE
    }

    override fun addOption(option: Option) {
        val radioButton = RadioButton(context, null, R.attr.radioButtonStyle)
        radioButton.layoutParams = LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        )

        radioButton.id = option.id
        radioButton.text = option.label

        if (option.isChecked) {
            radioButton.isChecked = true
            radioButton.setTextAppearanceCompat(R.style.MuunRadioButtonTextAppearanceSelected)
        }

        radioGroup.addView(radioButton)
    }

    override fun setOnOptionPickListener(listener: Picker.OnOptionPickListener) {
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            listener.onOptionPick(checkedId)
        }
    }
}
