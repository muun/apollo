package io.muun.apollo.presentation.ui.security_cards.utils

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.animation.LinearInterpolator
import androidx.core.animation.doOnEnd
import com.google.android.material.textview.MaterialTextView
import kotlin.math.cos
import kotlin.math.sin

/**
 * TextView that can sweep a shine across its text: once when armed with auto-play (shortly after
 * the view is first drawn, so the screen-open transition doesn't consume it), and on tap (with
 * haptic feedback).
 *
 * The technique is borrowed from facebook's shimmer-android library
 * (https://github.com/facebookarchive/shimmer-android): a linear gradient shader translated
 * across the text by animating its local matrix each frame. The gradient's edge colors match the
 * current text color and the shader only exists while the sweep runs, so the view renders as a
 * plain TextView before and after.
 *
 * The effect spec comes from the security cards prototype (ShimmerText/MetalGradientText):
 * a white highlight at 46-54% of a gradient 3x the text width, swept left to right at a
 * 10 degree tilt, played once in 1.4s with linear timing.
 */
class ShimmerTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MaterialTextView(context, attrs, defStyleAttr) {

    companion object {
        private const val SHIMMER_DURATION_MS = 1400L
        private const val GRADIENT_WIDTH_FACTOR = 3f
        private const val GRADIENT_TILT_DEGREES = 10.0

        // The prototype's 46-54% band: a white core fading linearly into the base color.
        private val GRADIENT_STOPS = floatArrayOf(0.46f, 0.5f, 0.54f)

        // Auto-play waits out the screen-open transition, or the sweep is consumed while the
        // window is still animating in and barely seen.
        private const val AUTO_PLAY_DELAY_MS = 500L
    }

    private var animator: ValueAnimator? = null
    private var pendingAutoPlay = false

    // Kept as a field so it can be unscheduled when the view goes away before the delay elapses
    private val delayedAutoPlay = Runnable { playShimmer() }

    /**
     * Arms the shine: a tap sweeps it across the text once. With [autoPlay], it also plays
     * shortly after the view is first drawn. Sets the view's click listener; callers can reset
     * it at any time, losing tap-to-replay.
     */
    fun armShimmer(autoPlay: Boolean) {
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            playShimmer()
        }

        pendingAutoPlay = autoPlay
        if (autoPlay) {
            invalidate()
        }
    }

    /**
     * Stops any running sweep and disarms tap-to-replay, restoring plain text rendering. For
     * views that get recycled into non-shimmering content.
     */
    fun clearShimmer() {
        pendingAutoPlay = false
        removeCallbacks(delayedAutoPlay)
        cancelSweep()
        setOnClickListener(null)
        isClickable = false
    }

    /** Sweeps the shine across the text once, starting now. */
    fun playShimmer() {
        cancelSweep()

        val textLayout = layout ?: return
        if (textLayout.lineCount == 0) return
        val textWidth = textLayout.getLineWidth(0)
        if (textWidth <= 0f) return

        // Text-relative geometry: the text can be end-aligned inside a stretched view, so the
        // view's own width says nothing about where the glyphs are.
        val textLeft = paddingLeft + textLayout.getLineLeft(0)

        val gradientWidth = textWidth * GRADIENT_WIDTH_FACTOR
        val tiltRadians = Math.toRadians(GRADIENT_TILT_DEGREES)
        val textColor = currentTextColor
        val shader = LinearGradient(
            textLeft,
            0f,
            textLeft + (gradientWidth * cos(tiltRadians)).toFloat(),
            (gradientWidth * sin(tiltRadians)).toFloat(),
            intArrayOf(textColor, Color.WHITE, textColor),
            GRADIENT_STOPS,
            Shader.TileMode.CLAMP,
        )
        val gradientMatrix = Matrix()

        // The highlight enters a quarter of the text width before the text and leaves a quarter
        // past its right edge: the prototype translates its 3x-wide gradient from -25% to 25%.
        animator = ValueAnimator.ofFloat(-1.75f * textWidth, -0.25f * textWidth).apply {
            duration = SHIMMER_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                gradientMatrix.setTranslate(animation.animatedValue as Float, 0f)
                shader.setLocalMatrix(gradientMatrix)
                invalidate()
            }
            doOnEnd {
                paint.shader = null
                invalidate()
            }
            start()
        }
        paint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (pendingAutoPlay) {
            pendingAutoPlay = false
            postDelayed(delayedAutoPlay, AUTO_PLAY_DELAY_MS)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(delayedAutoPlay)
        cancelSweep()
    }

    private fun cancelSweep() {
        // Cancelling fires doOnEnd, which already restores the plain text rendering.
        animator?.cancel()
        animator = null
    }
}
