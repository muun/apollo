package io.muun.apollo.presentation.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DimenRes
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.utils.UiUtils
import io.muun.apollo.presentation.ui.utils.addOnNextLayoutListener
import io.muun.apollo.presentation.ui.utils.children

class MuunButtonLayout @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0):
    MuunView(c, a, s) {

    @BindView(R.id.muun_button_layout_content_box)
    lateinit var contentBox: ViewGroup

    @BindView(R.id.muun_button_layout_button_box)
    lateinit var buttonBox: ViewGroup

    private val gradientHeight = getDimen(R.dimen.muun_button_layout_gradient_height)
    private val buttonSpacing = getDimen(R.dimen.muun_button_layout_button_spacing)
    private var isButtonBoxVisible = true

    override fun getLayoutResource() =
        R.layout.muun_button_layout

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
        if (child.id == R.id.muun_button_layout_root) {
            return super.addView(child, index, params) // attach our own frame without intervention
        }

        if (child is MuunButton || child is MuunButtonLayoutAnchor) {
            buttonBox.children.forEach { UiUtils.setMarginBottom(it, buttonSpacing) }
            buttonBox.addView(child, params)

        } else {
            contentBox.addView(child, params)
        }

        addOnNextLayoutListener {
            adjustBoxPositions(if (isButtonBoxVisible) 0 else buttonBox.height)
        }
    }

    fun setButtonsVisible(visible: Boolean) {
        setButtonsVisible(visible, 300)
    }

    private fun setButtonsVisible(visible: Boolean, animationDuration: Long) {
        // Do nothing if already in the desired state:
        if (isButtonBoxVisible == visible) return
        isButtonBoxVisible = visible

        // Postpone (and later "animate" in 0ms) if layout is not ready yet:
        if (buttonBox.height == 0) {
            Handler().post { setButtonsVisible(visible, 0) }
            return
        }

        // Go!
        ValueAnimator.ofInt(0, buttonBox.height).apply {
            duration = animationDuration

            addUpdateListener {
                adjustBoxPositions(it.animatedValue as Int)
            }

            if (visible) reverse() else start()
        }
    }

    private fun adjustBoxPositions(newButtonBoxOffset: Int) {
        // Move the buttonBox down as indicated:
        UiUtils.setTranslationY(buttonBox, newButtonBoxOffset)

        // Set the contentBox margin to match, overlapping by `gradientHeight` for fading effect:
        val ovelapOffset = if (isButtonBoxVisible) gradientHeight else 0
        UiUtils.setMarginBottom(contentBox, buttonBox.height - newButtonBoxOffset - ovelapOffset)
    }

    private fun getDimen(@DimenRes id: Int) =
        context.resources.getDimension(id).toInt()
}