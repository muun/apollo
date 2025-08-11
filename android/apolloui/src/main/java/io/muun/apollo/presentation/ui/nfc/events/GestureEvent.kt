package io.muun.apollo.presentation.ui.nfc.events

import android.view.MotionEvent

internal data class GestureEvent(
    val action: Int,
    val x: Float,
    val y: Float,
    val pointerCount: Int,
) : ISensorEvent {
    override fun handle(): List<Pair<String, String>> {
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

        return listOf(
            "gesture_action" to actionName,
            "gesture_x" to x.toString(),
            "gesture_y" to y.toString(),
            "gesture_pointers" to pointerCount.toString()
        )
    }
}
