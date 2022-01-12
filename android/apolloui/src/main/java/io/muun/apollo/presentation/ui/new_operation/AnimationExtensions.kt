package io.muun.apollo.presentation.ui.new_operation

import android.animation.LayoutTransition
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import io.muun.apollo.presentation.ui.utils.UiUtils

private const val ANIMATION_DURATION_MS = 300L

fun View.doAnimateTransition(animation: Animation) {
    this.animation = animation
    UiUtils.fadeIn(this)
}

fun revealFrom(deltaX: Float, deltaY: Float): Animation {
    val translateLabel = TranslateAnimation(deltaX, 0F, deltaY, 0F)
    translateLabel.duration = ANIMATION_DURATION_MS
    return translateLabel
}

/**
 * Disable appearing or disappearing transitions for a ViewGroup.
 * We only want to animate the effect that some appearing or disappearing view has in other views,
 * not the changes in the changed view itself.
 */
fun ViewGroup.disableTransitions() {
    val transition = LayoutTransition()
    transition.setDuration(ANIMATION_DURATION_MS)
    transition.disableTransitionType(LayoutTransition.APPEARING)
    transition.disableTransitionType(LayoutTransition.DISAPPEARING)
    this.layoutTransition = transition
}