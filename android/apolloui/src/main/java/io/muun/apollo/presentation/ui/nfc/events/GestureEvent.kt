package io.muun.apollo.presentation.ui.nfc.events

import android.view.MotionEvent
import io.muun.apollo.domain.model.SensorEvent
import org.threeten.bp.ZonedDateTime

/**
 * Represents a gesture event captured from a [MotionEvent], including gesture type and position data.
 *
 * Implements [ISensorEvent] to provide structured data for gesture interactions.
 *
 * @property id The unique identifier of the gesture event.
 * @property eventType The type of the event, defaulted to "gesture".
 * @property action The gesture action type from [MotionEvent].
 * @property x The x-coordinate of the gesture event.
 * @property y The y-coordinate of the gesture event.
 * @property pointerCount The number of pointers (fingers) involved in the gesture.
 * @property timestamp The ISO 8601 formatted timestamp when the event was created.
 */
internal data class GestureEvent(
    override val id: Long,
    override val eventType: String = "gesture",
    val action: Int,
    val x: Float,
    val y: Float,
    val pointerCount: Int,
) : ISensorEvent {

    override val timestamp: ZonedDateTime = ZonedDateTime.now()

    override fun handle(): SensorEvent {
        val actionName = when (action) {
            MotionEvent.ACTION_DOWN -> "gesture_down"
            MotionEvent.ACTION_UP -> "gesture_up"
            MotionEvent.ACTION_MOVE -> "gesture_move"
            MotionEvent.ACTION_CANCEL -> "gesture_cancel"
            MotionEvent.ACTION_OUTSIDE -> "gesture_outside"
            MotionEvent.ACTION_POINTER_DOWN -> "gesture_pointer_down"
            MotionEvent.ACTION_POINTER_UP -> "gesture_pointer_up"
            MotionEvent.ACTION_HOVER_MOVE -> "gesture_hover_move"
            MotionEvent.ACTION_SCROLL -> "gesture_scroll"
            MotionEvent.ACTION_HOVER_ENTER -> "gesture_hover_enter"
            MotionEvent.ACTION_HOVER_EXIT -> "gesture_hover_exit"
            MotionEvent.ACTION_BUTTON_PRESS -> "gesture_button_press"
            MotionEvent.ACTION_BUTTON_RELEASE -> "gesture_button_release"
            else -> "gesture_unknown_$action"
        }

        return SensorEvent(
            eventId = id,
            eventType = eventType,
            eventTimestamp = timestamp,
            eventData = mapOf(
                "gesture_action" to actionName,
                "gesture_x" to x.toString(),
                "gesture_y" to y.toString(),
                "gesture_pointers" to pointerCount.toString(),
            )
        )
    }
}
