package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import io.muun.apollo.R
import kotlin.math.roundToInt

/**
 * A measurement line: a thin line with an arrowhead at each end, used to annotate dimensions
 * (e.g. the width/height of a security card image).
 *
 * Orientation is set with the standard android:orientation attribute. The drawing replicates
 * the old ic_arrowhead_up/left vectors (8x5dp stroked chevrons): at wrap_content the view is
 * 8dp thick across the line's axis, and all geometry (arrowheads, stroke) scales proportionally
 * with the view's thickness.
 */
class DimensionLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    companion object {
        // At wrap_content the view is 8dp thick, matching the original arrowhead vectors
        private const val DEFAULT_THICKNESS_DP = 8f

        // Geometry of the original arrowhead vectors, in artwork units (1 unit = 1dp at the
        // default thickness): an 8x5 chevron with a 1-unit stroke
        private const val THICKNESS_UNITS = 8f
        private const val ARROWHEAD_LENGTH_UNITS = 5f
        private const val STROKE_WIDTH_UNITS = 1f
    }

    var orientation: Int = LinearLayout.VERTICAL
        set(value) {
            field = value
            // requestLayout because wrap_content measurement is orientation-dependent, plus
            // invalidate for the fixed-size case, where bounds (and thus layout) don't change
            requestLayout()
            invalidate()
        }

    @get:ColorInt
    var strokeColor: Int
        get() = paint.color
        set(@ColorInt value) {
            paint.color = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.text_secondary_color)
    }

    private val path = Path()

    private val density: Float
        get() = resources.displayMetrics.density

    init {
        context.obtainStyledAttributes(attrs, R.styleable.DimensionLineView).apply {
            orientation = getInt(
                R.styleable.DimensionLineView_android_orientation,
                LinearLayout.VERTICAL,
            )
            paint.color = getColor(R.styleable.DimensionLineView_strokeColor, paint.color)
            recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val defaultThickness = (DEFAULT_THICKNESS_DP * density).roundToInt()

        if (orientation == LinearLayout.VERTICAL) {
            val width = resolveSize(defaultThickness + paddingLeft + paddingRight, widthMeasureSpec)
            val minLength = minMainAxisLength(width - paddingLeft - paddingRight)
            val height = resolveSize(minLength + paddingTop + paddingBottom, heightMeasureSpec)
            setMeasuredDimension(width, height)
        } else {
            val height =
                resolveSize(defaultThickness + paddingTop + paddingBottom, heightMeasureSpec)
            val minLength = minMainAxisLength(height - paddingTop - paddingBottom)
            val width = resolveSize(minLength + paddingLeft + paddingRight, widthMeasureSpec)
            setMeasuredDimension(width, height)
        }
    }

    // Room for the two arrowheads, at the scale given by the view's thickness
    private fun minMainAxisLength(thickness: Int): Int {
        return (2 * ARROWHEAD_LENGTH_UNITS * scaleUnit(thickness)).roundToInt()
    }

    // The size in px of one artwork unit, at the current thickness
    private fun scaleUnit(thickness: Int): Float {
        return thickness / THICKNESS_UNITS
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        val right = (width - paddingRight).toFloat()
        val bottom = (height - paddingBottom).toFloat()

        path.rewind()

        if (orientation == LinearLayout.VERTICAL) {
            val u = scaleUnit((right - left).roundToInt())
            val centerX = (left + right) / 2f

            // Line, top arrowhead, bottom arrowhead (see ic_arrowhead_up: M0.5,4.5 L4,0.5 L7.5,4.5)
            path.moveTo(centerX, top)
            path.lineTo(centerX, bottom)
            path.moveTo(left + 0.5f * u, top + 4.5f * u)
            path.lineTo(left + 4f * u, top + 0.5f * u)
            path.lineTo(left + 7.5f * u, top + 4.5f * u)
            path.moveTo(left + 0.5f * u, bottom - 4.5f * u)
            path.lineTo(left + 4f * u, bottom - 0.5f * u)
            path.lineTo(left + 7.5f * u, bottom - 4.5f * u)

            paint.strokeWidth = STROKE_WIDTH_UNITS * u
        } else {
            val u = scaleUnit((bottom - top).roundToInt())
            val centerY = (top + bottom) / 2f

            // Line, start arrowhead, end arrowhead
            // (see ic_arrowhead_left: M4.5,0.5 L0.5,4 L4.5,7.5)
            path.moveTo(left, centerY)
            path.lineTo(right, centerY)
            path.moveTo(left + 4.5f * u, top + 0.5f * u)
            path.lineTo(left + 0.5f * u, top + 4f * u)
            path.lineTo(left + 4.5f * u, top + 7.5f * u)
            path.moveTo(right - 4.5f * u, top + 0.5f * u)
            path.lineTo(right - 0.5f * u, top + 4f * u)
            path.lineTo(right - 4.5f * u, top + 7.5f * u)

            paint.strokeWidth = STROKE_WIDTH_UNITS * u
        }

        canvas.drawPath(path, paint)
    }
}
