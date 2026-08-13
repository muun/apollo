package io.muun.apollo.presentation.ui.security_cards_shipping_address

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.google.android.material.transition.MaterialContainerTransform

/**
 * Orchestrator that runs one animation at a time. Starting a new animation
 * automatically cancels the previous one.
 */
class HeroAnimation {
    private var currentRoot: AnimationNode? = null

    val isPlaying: Boolean get() = currentRoot?.isAnimationPlaying == true

    fun run(
        sceneRoot: ViewGroup,
        block: HeroAnimationScope.() -> AnimationNode,
    ): AnimationHandle {
        currentRoot?.cancel()
        val scope = HeroAnimationScope(sceneRoot)
        val root = scope.block()
        currentRoot = root
        root.play()
        return root
    }
}

/**
 * DSL scope for building animation trees. Provides factory methods for leaf
 * nodes ([morph], [fade]) and composite nodes ([inSequence], [inParallel]).
 * The block passed to [HeroAnimation.run] returns the root [AnimationNode].
 */
class HeroAnimationScope internal constructor(internal val sceneRoot: ViewGroup) {

    companion object {

        fun noop(): AnimationHandle = NoOpAnimationNode()
    }
    internal val nodes = mutableListOf<AnimationNode>()

    fun morph(
        from: View,
        to: View,
        duration: Long,
    ): AnimationNode = MorphAnimationNode(sceneRoot, from, to, duration).also { nodes.add(it) }

    fun fade(
        target: View,
        to: Float,
        duration: Long,
    ): AnimationNode = FadeAnimationNode(target, to, duration).also { nodes.add(it) }

    fun inSequence(block: HeroAnimationScope.() -> Unit): AnimationNode {
        val inner = HeroAnimationScope(sceneRoot)
        inner.block()
        return SequentialAnimationNode(inner.nodes.toList()).also { nodes.add(it) }
    }

    fun inParallel(block: HeroAnimationScope.() -> Unit): AnimationNode {
        val inner = HeroAnimationScope(sceneRoot)
        inner.block()
        return ParallelAnimationNode(inner.nodes.toList()).also { nodes.add(it) }
    }
}

/**
 * A node in the animation tree. Can be a leaf ([MorphAnimationNode], [FadeAnimationNode]) or a
 * composite ([ParallelAnimationNode], [SequentialAnimationNode]). Every node follows the same
 * lifecycle: onStart → doPlay → onEnd. Implements [AnimationHandle] so the
 * root node returned by [HeroAnimation.run] doubles as the public handle.
 */
abstract class AnimationNode : AnimationHandle {
    private var onStartAction: (() -> Unit)? = null
    private var onEndAction: (() -> Unit)? = null
    private var playing = false
    private var cancelled = false

    internal fun play() {
        if (cancelled) return

        doPlay(
            doOnAnimationStart = {
                playing = true
                onStartAction?.invoke()
            },
            doOnAnimationEnd = {
                playing = false
                onEndAction?.invoke()
            },
        )
    }

    internal open fun cancel() {
        if (cancelled) return

        cancelled = true
        onStartAction = null
        onEndAction = null
    }

    protected abstract fun doPlay(
        doOnAnimationStart: () -> Unit,
        doOnAnimationEnd: () -> Unit,
    )

    // region AnimationHandle
    override val isAnimationPlaying: Boolean get() = playing

    override fun onStartAnimation(action: () -> Unit): AnimationHandle {
        val prev = onStartAction
        onStartAction = { prev?.invoke(); action() }
        return this
    }

    override fun onEndAnimation(action: () -> Unit): AnimationHandle {
        val prev = onEndAction
        onEndAction = { prev?.invoke(); action() }
        return this
    }
    // endregion
}

/** Leaf node that performs a [MaterialContainerTransform] between two views. */
private class MorphAnimationNode(
    private val sceneRoot: ViewGroup,
    private val from: View,
    private val to: View,
    private val duration: Long,
) : AnimationNode() {

    override fun doPlay(doOnAnimationStart: () -> Unit, doOnAnimationEnd: () -> Unit) {
        doOnAnimationStart()
        val transform = MaterialContainerTransform(
            sceneRoot.context, true,
        ).apply {
            startView = from
            endView = to
            addTarget(to)
            this.duration = this@MorphAnimationNode.duration
            scrimColor = Color.TRANSPARENT
        }
        transform.addListener(object : Transition.TransitionListener {
            override fun onTransitionEnd(t: Transition) = doOnAnimationEnd()
            override fun onTransitionCancel(t: Transition) = doOnAnimationEnd()
            override fun onTransitionStart(t: Transition) {}
            override fun onTransitionPause(t: Transition) {}
            override fun onTransitionResume(t: Transition) {}
        })
        TransitionManager.beginDelayedTransition(sceneRoot, transform)
        to.visibility = View.VISIBLE
        from.visibility = View.INVISIBLE
    }
}

/** Leaf node that animates a view's alpha via [ViewPropertyAnimator]. */
private class FadeAnimationNode(
    private val target: View,
    private val to: Float,
    private val duration: Long,
) : AnimationNode() {

    override fun doPlay(doOnAnimationStart: () -> Unit, doOnAnimationEnd: () -> Unit) {
        doOnAnimationStart()
        target.animate()
            .alpha(to)
            .setDuration(duration)
            .withEndAction { doOnAnimationEnd() }
            .start()
    }
}

/** Composite node that plays all children concurrently, completing when the last child ends. */
private class ParallelAnimationNode(
    private val children: List<AnimationNode>,
) : AnimationNode() {

    override fun doPlay(
        doOnAnimationStart: () -> Unit,
        doOnAnimationEnd: () -> Unit,
    ) {
        doOnAnimationStart()
        if (children.isEmpty()) {
            doOnAnimationEnd()
            return
        }

        var remaining = children.size
        children.forEach { child ->
            child.onEndAnimation {
                if (--remaining == 0) {
                    doOnAnimationEnd()
                }
            }
            child.play()
        }
    }

    override fun cancel() {
        children.forEach { it.cancel() }
        super.cancel()
    }
}

/** Composite node that plays children one after another in order. */
private class SequentialAnimationNode(
    private val children: List<AnimationNode>,
) : AnimationNode() {

    override fun doPlay(
        doOnAnimationStart: () -> Unit,
        doOnAnimationEnd: () -> Unit,
    ) {
        doOnAnimationStart()
        if (children.isEmpty()) {
            doOnAnimationEnd()
            return
        }

        playSequentially(doOnAnimationEnd)
    }

    private fun playSequentially(
        doOnAnimationEnd: () -> Unit,
        childIndex: Int = 0,
    ) {
        if (childIndex == children.size) {
            doOnAnimationEnd()
            return
        }

        val child = children[childIndex]
        child.onEndAnimation { playSequentially(doOnAnimationEnd, childIndex = childIndex + 1) }
        child.play()
    }

    override fun cancel() {
        children.forEach { it.cancel() }
        super.cancel()
    }
}

/**
 * No-op node that completes instantly.
 */
private class NoOpAnimationNode : AnimationNode() {

    override fun doPlay(doOnAnimationStart: () -> Unit, doOnAnimationEnd: () -> Unit) {
        doOnAnimationStart()
        doOnAnimationEnd()
    }

    override fun onStartAnimation(action: () -> Unit): AnimationHandle {
        action()
        return this
    }

    override fun onEndAnimation(action: () -> Unit): AnimationHandle {
        action()
        return this
    }
}

/**
 * Public handle returned by [HeroAnimation.run]. Exposes animation lifecycle
 * callbacks without leaking internal node structure.
 */
interface AnimationHandle {

    val isAnimationPlaying: Boolean

    fun onStartAnimation(action: () -> Unit): AnimationHandle

    fun onEndAnimation(action: () -> Unit): AnimationHandle
}
