package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.view.postDelayed
import butterknife.BindViews
import io.muun.apollo.R


class BlockClock @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0):
    MuunView(c, a, s) {

    override val layoutResource: Int
        get() = R.layout.view_block_clock

    @BindViews(
        R.id.block_clock_number_1,
        R.id.block_clock_number_2,
        R.id.block_clock_number_3,
        R.id.block_clock_number_4,
        R.id.block_clock_number_5,
        R.id.block_clock_number_6
    )
    lateinit var numberViews: java.util.List<TextView> // ButterKnife :D

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