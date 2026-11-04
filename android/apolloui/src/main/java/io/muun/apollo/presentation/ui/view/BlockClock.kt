package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import io.muun.apollo.databinding.ViewBlockClockBinding

class BlockClock @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0):
    MuunView<ViewBlockClockBinding>(c, a, s) {

    private val numberViews: List<TextView>
        get() = listOf(
            binding.blockClockNumber1,
            binding.blockClockNumber2,
            binding.blockClockNumber3,
            binding.blockClockNumber4,
            binding.blockClockNumber5,
            binding.blockClockNumber6,
        )

    override fun bindingInflater(): BindingInflater<ViewBlockClockBinding> {
        return ViewBlockClockBinding::inflate
    }

    var value: Int = 0
        set(newValue) {
            check(newValue in 0..999_999)
            val newValueString = newValue.toString().padStart(6, '0')

            newValueString.forEachIndexed {
                i, char -> numberViews[i].text = char.toString()
            }

            field = newValue
        }
}