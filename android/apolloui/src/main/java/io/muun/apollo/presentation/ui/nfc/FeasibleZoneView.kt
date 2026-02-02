package io.muun.apollo.presentation.ui.nfc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import io.muun.apollo.domain.model.FeasibleZone
import io.muun.apollo.presentation.ui.utils.calculatePixelsPerMm

// TODO: rough debug view, iterations will occur in this view.
class FeasibleZoneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var boundaryPoints: List<List<Int>>? = null

    private val pixelsPerMm: Float = calculatePixelsPerMm(resources)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(70, 100, 180)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb((0.25f * 255).toInt(), 160, 180, 230)
        style = Paint.Style.FILL
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(70, 100, 180)
        style = Paint.Style.FILL
    }

    fun setData(response: FeasibleZone) {
        boundaryPoints = response.boundary
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val points = boundaryPoints

        if (points == null || points.isEmpty()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val minX = points.minOf { it[0] }
        val maxX = points.maxOf { it[0] }
        val minY = points.minOf { it[1] }
        val maxY = points.maxOf { it[1] }

        val dataWidth = (maxX - minX).toFloat()
        val dataHeight = (maxY - minY).toFloat()

        val viewWidth = (dataWidth * pixelsPerMm).toInt()
        val viewHeight = (dataHeight * pixelsPerMm).toInt()

        setMeasuredDimension(viewWidth, viewHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val points = boundaryPoints
        if (points == null || points.isEmpty()) {
            return
        }

        val scale = pixelsPerMm
        val statusBarHeight = getStatusBarHeight()
        // TODO: remove magic numbers when corrections are added to the measurements
        val xEdge = -3
        val yEdge = -3

        val offsetX = scale * xEdge // scale * (- edges)
        val offsetY = (scale * yEdge) - statusBarHeight // (scale * (- edges)) - statusBarHeight

        fun transformX(x: Int): Float = offsetX + (x * scale)
        fun transformY(y: Int): Float = offsetY + (y * scale)

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = transformX(point[0])
            val y = transformY(point[1])

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, paint)

        points.forEach { point ->
            val x = transformX(point[0])
            val y = transformY(point[1])
            canvas.drawCircle(x, y, 3f, pointPaint)
        }
    }

    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }
}