package io.muun.apollo.presentation.ui.security_cards_shipping_address

import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class AnimationNodeTest {

    private fun scope() = HeroAnimationScope(mockk(relaxed = true))

    private fun buildSequential(vararg nodes: AnimationNode): AnimationNode {
        val scope = scope()
        scope.nodes.addAll(nodes.toList())
        return scope.inSequence { nodes.forEach { this.nodes.add(it) } }.also {
            scope.nodes.clear()
        }
    }

    private fun buildParallel(vararg nodes: AnimationNode): AnimationNode {
        val scope = scope()
        scope.nodes.addAll(nodes.toList())
        return scope.inParallel { nodes.forEach { this.nodes.add(it) } }.also {
            scope.nodes.clear()
        }
    }

    @Test
    fun `sequential plays children in order`() {
        val log = mutableListOf<String>()
        val a = TestAnimationNode("a", log)
        val b = TestAnimationNode("b", log)
        val c = TestAnimationNode("c", log)

        val seq = buildSequential(a, b, c)
        seq.play()

        assertThat(log).containsExactly("a:start")

        a.finish()
        assertThat(log).containsExactly("a:start", "a:end", "b:start")

        b.finish()
        assertThat(log).containsExactly("a:start", "a:end", "b:start", "b:end", "c:start")

        c.finish()
        assertThat(log).containsExactly(
            "a:start", "a:end", "b:start", "b:end", "c:start", "c:end",
        )
    }

    @Test
    fun `parallel plays all children concurrently`() {
        val log = mutableListOf<String>()
        val a = TestAnimationNode("a", log)
        val b = TestAnimationNode("b", log)

        val par = buildParallel(a, b)
        par.play()

        assertThat(log).containsExactly("a:start", "b:start")

        a.finish()
        assertThat(log).containsExactly("a:start", "b:start", "a:end")

        b.finish()
        assertThat(log).containsExactly("a:start", "b:start", "a:end", "b:end")
    }

    @Test
    fun `parallel completes when last child finishes`() {
        val a = TestAnimationNode("a")
        val b = TestAnimationNode("b")
        val par = buildParallel(a, b)

        var completed = false
        par.onEndAnimation { completed = true }
        par.play()

        a.finish()
        assertThat(completed).isFalse()

        b.finish()
        assertThat(completed).isTrue()
    }

    @Test
    fun `sequential completes when last child finishes`() {
        val a = TestAnimationNode("a")
        val b = TestAnimationNode("b")
        val seq = buildSequential(a, b)

        var completed = false
        seq.onEndAnimation { completed = true }
        seq.play()

        a.finish()
        assertThat(completed).isFalse()

        b.finish()
        assertThat(completed).isTrue()
    }

    @Test
    fun `onEndAnimation callbacks compose`() {
        val node = TestAnimationNode("n")
        val log = mutableListOf<String>()

        node.onEndAnimation { log.add("first") }
        node.onEndAnimation { log.add("second") }
        node.play()
        node.finish()

        assertThat(log).containsExactly("first", "second")
    }

    @Test
    fun `onStartAnimation callbacks compose`() {
        val node = TestAnimationNode("n")
        val log = mutableListOf<String>()

        node.onStartAnimation { log.add("first") }
        node.onStartAnimation { log.add("second") }
        node.play()

        assertThat(log).containsExactly("first", "second")
    }

    @Test
    fun `cancel prevents callbacks from firing`() {
        val node = TestAnimationNode("n")
        var startFired = false
        var endFired = false

        node.onStartAnimation { startFired = true }
        node.onEndAnimation { endFired = true }
        node.cancel()
        node.play()

        assertThat(startFired).isFalse()
        assertThat(endFired).isFalse()
    }

    @Test
    fun `cancel propagates to parallel children`() {
        val a = TestAnimationNode("a")
        val b = TestAnimationNode("b")
        val par = buildParallel(a, b)

        var aEnded = false
        var bEnded = false
        a.onEndAnimation { aEnded = true }
        b.onEndAnimation { bEnded = true }
        par.play()
        par.cancel()

        a.finish()
        b.finish()
        assertThat(aEnded).isFalse()
        assertThat(bEnded).isFalse()
    }

    @Test
    fun `cancel propagates to sequential children`() {
        val a = TestAnimationNode("a")
        val b = TestAnimationNode("b")
        val seq = buildSequential(a, b)

        var seqEnded = false
        seq.onEndAnimation { seqEnded = true }
        seq.play()
        seq.cancel()

        a.finish()
        assertThat(seqEnded).isFalse()
        assertThat(b.isAnimationPlaying).isFalse()
    }

    @Test
    fun `cancel is idempotent`() {
        val node = TestAnimationNode("n")
        node.cancel()
        node.cancel()
    }

    @Test
    fun `isAnimationPlaying reflects lifecycle`() {
        val node = TestAnimationNode("n")
        assertThat(node.isAnimationPlaying).isFalse()

        node.play()
        assertThat(node.isAnimationPlaying).isTrue()

        node.finish()
        assertThat(node.isAnimationPlaying).isFalse()
    }

    @Test
    fun `noop fires onEndAnimation immediately`() {
        var fired = false
        val handle = HeroAnimationScope.noop()
        handle.onEndAnimation { fired = true }

        assertThat(fired).isTrue()
    }

    @Test
    fun `noop fires onStartAnimation immediately`() {
        var fired = false
        val handle = HeroAnimationScope.noop()
        handle.onStartAnimation { fired = true }

        assertThat(fired).isTrue()
    }

    @Test
    fun `noop is not playing`() {
        val handle = HeroAnimationScope.noop()
        assertThat(handle.isAnimationPlaying).isFalse()
    }

    @Test
    fun `empty parallel completes immediately`() {
        val par = buildParallel()
        var completed = false
        par.onEndAnimation { completed = true }
        par.play()

        assertThat(completed).isTrue()
    }

    @Test
    fun `empty sequential completes immediately`() {
        val seq = buildSequential()
        var completed = false
        seq.onEndAnimation { completed = true }
        seq.play()

        assertThat(completed).isTrue()
    }

    @Test
    fun `nested composites complete correctly`() {
        val a = TestAnimationNode("a")
        val b = TestAnimationNode("b")
        val c = TestAnimationNode("c")

        val inner = buildSequential(a, b)
        val outer = buildParallel(inner, c)

        var completed = false
        outer.onEndAnimation { completed = true }
        outer.play()

        a.finish()
        assertThat(completed).isFalse()

        c.finish()
        assertThat(completed).isFalse()

        b.finish()
        assertThat(completed).isTrue()
    }

    @Test
    fun `per-node onEndAnimation fires at correct time in sequence`() {
        val a = TestAnimationNode("a")
        val b = TestAnimationNode("b")
        val log = mutableListOf<String>()

        a.onEndAnimation { log.add("a:callback") }
        b.onEndAnimation { log.add("b:callback") }

        val seq = buildSequential(a, b)
        seq.onEndAnimation { log.add("seq:callback") }
        seq.play()

        a.finish()
        assertThat(log).containsExactly("a:callback")

        b.finish()
        assertThat(log).containsExactly("a:callback", "b:callback", "seq:callback")
    }
}

private class TestAnimationNode(
    private val name: String,
    private val log: MutableList<String> = mutableListOf(),
) : AnimationNode() {
    private var doOnEnd: (() -> Unit)? = null

    override fun doPlay(doOnAnimationStart: () -> Unit, doOnAnimationEnd: () -> Unit) {
        doOnAnimationStart()
        log.add("$name:start")
        doOnEnd = {
            log.add("$name:end")
            doOnAnimationEnd()
        }
    }

    fun finish() {
        doOnEnd?.invoke()
        doOnEnd = null
    }
}
