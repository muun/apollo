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

class Picker @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0):
    MuunView(c, a, s) {

    interface OnOptionChosenListener {
        fun onOptionChosen(optionId: Int)
    }

    @BindView(R.id.picker_title)
    lateinit var titleView: TextView

    @BindView(R.id.picker_radio_group)
    lateinit var radioGroup: RadioGroup

    override fun getLayoutResource() =
        R.layout.view_picker

    fun setTitle(@StringRes resId: Int) {
        titleView.setText(resId)
        titleView.visibility = VISIBLE
    }

    fun addOption(optionId: Int, label: CharSequence, checked: Boolean) {
        val radioButton = RadioButton(context, null, R.attr.radioButtonStyle)
        radioButton.layoutParams = LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        )

        radioButton.id = optionId
        radioButton.text = label

        if (checked) {
            radioButton.isChecked = true
            radioButton.setTextAppearanceCompat(R.style.MuunRadioButtonTextAppearanceSelected)
        }

        radioGroup.addView(radioButton)
    }

    fun setOnOptionChosenListener(listener: OnOptionChosenListener) {
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            listener.onOptionChosen(checkedId)
        }
    }
}
