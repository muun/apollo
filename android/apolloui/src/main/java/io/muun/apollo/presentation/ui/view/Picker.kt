package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet

abstract class Picker<T> @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0):
    MuunView(c, a, s) {

    interface OnOptionPickListener {
        fun onOptionPick(optionId: Int)
    }

    open class Option(
        val id: Int
    )

    abstract fun setTitle(title: CharSequence)

    abstract fun addOption(option: T)

    abstract fun setOnOptionPickListener(listener: OnOptionPickListener)
}
