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

abstract class Picker<T> @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0):
    MuunView(c, a, s) {

    interface OnOptionPickListener {
        fun onOptionPick(optionId: Int)
    }

    open class Option(
        val id: Int
    )

    abstract fun addOption(option: T)

    abstract fun setOnOptionPickListener(listener: OnOptionPickListener)
}
