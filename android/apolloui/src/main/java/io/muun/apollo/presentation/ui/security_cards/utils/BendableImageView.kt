package io.muun.apollo.presentation.ui.security_cards.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.sqrt

/**
 * ImageView that bends away from the finger like a flexible card: pressing tilts it towards the
 * touch point, dragging tracks the finger, and releasing springs it back flat.
 *
 * The effect is an illusion built from three standard pieces:
 * - **3D rotation**: every View can rotate around its horizontal axis ([View.setRotationX]) and
 *   vertical axis ([View.setRotationY]). Tilting a few degrees towards wherever the finger is
 *   makes the flat image read as a bending physical card.
 * - **Perspective**: how dramatic a 3D rotation looks depends on how close the virtual camera
 *   sits ([View.setCameraDistance]): the closer the camera, the more the receding edge shrinks.
 * - **Springs**: instead of fixed-duration animations, [SpringAnimation] moves each property with
 *   simulated physics, so the card lands with a small, natural wobble and can be retargeted
 *   mid-flight without a visual jump.
 *
 * The parameters port the security cards prototype's BendableImage spec.
 */
class BendableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    companion object {
        // Touch position is normalized to [0, 1], so (position - 0.5) * 20 tilts up to 10 degrees
        // towards the touched edge, and not at all when pressing the center.
        private const val MAX_TILT_DEGREES = 20f

        // While pressed the card also squashes slightly and lifts 2.1dp (the prototype's
        // bendIntensity 0.7 * 0.2, mapped through a 15dp range), selling the "pressed" feel.
        private const val PRESSED_SCALE = 0.99f
        private const val PRESSED_LIFT_DP = 2.1f

        // The prototype uses an origami spring (tension 140, friction 7), which react native
        // converts internally to the physical params a SpringForce takes:
        // stiffness = (tension - 30) * 3.62 + 194, damping = (friction - 8) * 3 + 25 = 22,
        // and ratio = damping / (2 * sqrt(stiffness)). A ratio below 1 is underdamped: the card
        // overshoots a little and wobbles into place instead of easing in flatly.
        private const val SPRING_STIFFNESS = 592f
        private const val SPRING_DAMPING_RATIO = 0.45f

        // The prototype renders with css perspective(1000px). Android's camera projection is
        // ~sqrt(5) * density stronger than css at the same nominal distance (react native
        // normalizes by that same factor in BaseViewManager), and the value is also expressed in
        // physical pixels, hence the extra density factor applied in init.
        private const val PERSPECTIVE_DISTANCE = 1000f
        private val CAMERA_DISTANCE_NORMALIZATION = sqrt(5f)
    }

    /** Snapshot of every animated property; the card is always moving towards some pose. */
    private data class Pose(
        val rotationX: Float,
        val rotationY: Float,
        val scale: Float,
        val translationY: Float,
    )

    private val restPose = Pose(rotationX = 0f, rotationY = 0f, scale = 1f, translationY = 0f)

    // One reusable spring per animated property, created on first use
    private val springs = mutableMapOf<FloatPropertyCompat<View>, SpringAnimation>()

    init {
        val density = resources.displayMetrics.density
        cameraDistance = PERSPECTIVE_DISTANCE * CAMERA_DISTANCE_NORMALIZATION * density * density
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // The scrollable ancestor must not steal the drag while the card is bending
                parent?.requestDisallowInterceptTouchEvent(true)
                springToPose(poseFor(event.x, event.y))
                return true
            }

            // Once pressed, the card tracks the finger with no animation: any lag here would
            // read as the card slipping under the finger
            MotionEvent.ACTION_MOVE -> {
                snapToPose(poseFor(event.x, event.y))
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                springToPose(restPose)
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    /**
     * Compute the pressed pose for a touch point, in this view's coordinates. The signs make the
     * touched edge recede: pressing the top tips the top away, pressing the right tips the right
     * away, and so on.
     */
    private fun poseFor(touchX: Float, touchY: Float): Pose {
        if (width <= 0 || height <= 0) {
            return restPose
        }

        // Clamped so a drag that leaves the view keeps the tilt pinned at the nearest edge
        val normalizedX = (touchX / width).coerceIn(0f, 1f)
        val normalizedY = (touchY / height).coerceIn(0f, 1f)

        return Pose(
            rotationX = -(normalizedY - 0.5f) * MAX_TILT_DEGREES,
            rotationY = (normalizedX - 0.5f) * MAX_TILT_DEGREES,
            scale = PRESSED_SCALE,
            translationY = -PRESSED_LIFT_DP * resources.displayMetrics.density,
        )
    }

    private fun springToPose(pose: Pose) {
        pose.forEachProperty(::springTo)
    }

    private fun snapToPose(pose: Pose) {
        pose.forEachProperty(::snapTo)
    }

    private fun Pose.forEachProperty(apply: (FloatPropertyCompat<View>, Float) -> Unit) {
        apply(DynamicAnimation.ROTATION_X, rotationX)
        apply(DynamicAnimation.ROTATION_Y, rotationY)
        apply(DynamicAnimation.SCALE_X, scale)
        apply(DynamicAnimation.SCALE_Y, scale)
        apply(DynamicAnimation.TRANSLATION_Y, translationY)
    }

    private fun springTo(property: FloatPropertyCompat<View>, value: Float) {
        // animateToFinalPosition retargets a running spring keeping its current velocity, so
        // rapid press/release sequences blend instead of restarting from zero
        springs.getOrPut(property) {
            SpringAnimation(this, property).apply {
                spring = SpringForce().apply {
                    stiffness = SPRING_STIFFNESS
                    dampingRatio = SPRING_DAMPING_RATIO
                }
            }
        }.animateToFinalPosition(value)
    }

    private fun snapTo(property: FloatPropertyCompat<View>, value: Float) {
        // A still-running spring would keep overwriting the property, so it's cancelled first
        springs[property]?.cancel()
        property.setValue(this, value)
    }
}
