package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout.LayoutParams.MATCH_PARENT
import androidx.annotation.ColorRes
import io.muun.apollo.R
import io.muun.apollo.databinding.MuunProgressBarBinding
import io.muun.apollo.presentation.ui.utils.UiUtils
import io.muun.apollo.presentation.ui.utils.addOnNextLayoutListener

class MuunProgressBar @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0) :
    MuunView<MuunProgressBarBinding>(c, a, s) {

    private val progressView: View get() = binding.progress

    override fun bindingInflater(): BindingInflater<MuunProgressBarBinding> {
        return MuunProgressBarBinding::inflate
    }

    var progress: Double = 0.0
        set(progress) {
            check(progress in 0.0..1.0)
            field = progress

            // Deferring until next layout pass, to be sure views are already measured
            // (and we can make use of our/our parent's width)
            addOnNextLayoutListener {
                adjustBarDimensions()
            }
        }

    var colorRes: Int = 0
        set(@ColorRes color) {
            field = color
            progressView.background = getBackgroundDrawable(colorRes)
        }

    private fun adjustBarDimensions() {
        progressView.layoutParams = LayoutParams((this.width * progress).toInt(), MATCH_PARENT)
    }

    private fun getBackgroundDrawable(@ColorRes colorRes: Int) =
        UiUtils.getTintedDrawable(context, R.drawable.muun_progress_bar_bg, colorRes)
}
