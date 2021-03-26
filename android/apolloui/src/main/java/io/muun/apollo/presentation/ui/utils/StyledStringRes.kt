package io.muun.apollo.presentation.ui.utils

import android.content.Context
import android.graphics.Typeface
import android.text.Annotation
import android.text.ParcelableSpan
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.SpannedString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.utils.StyledStringRes.Companion.ARG


class StyledStringRes(private val context: Context,
                      @StringRes private val resId: Int,
                      private val onLinkClick: (String) -> Unit) {

    constructor(context: Context, @StringRes resId: Int): this(context, resId, {}) // for Java

    companion object {
        private const val FONT_COLOR = "fontColor"
        private const val FONT_COLOR_BLUE = "muunBlue"
        private const val FONT_COLOR_BLACK = "muunBlack"

        private const val FONT_STYLE = "fontStyle"
        private const val FONT_STYLE_NORMAL = "normal"
        private const val FONT_STYLE_BOLD = "bold"
        private const val FONT_STYLE_ITALIC = "italic"
        private const val FONT_STYLE_UNDERLINED = "underlined"

        private const val LINK = "link"

        private const val ROLE = "role"
        private const val ROLE_TITLE = "h1"
        private const val ROLE_PARAGRAPH = "p"
        private const val ROLE_EMPHASIS = "em"

        const val ARG = "arg"

        // NOTE: the following priority constants will be used to define the order in which spans
        // are applied when rendering the string. This is important when spans nest or overlap,
        // because the last span to be applied overrides everything set before in that range.

        // For example, if two ForegroundColorSpans are nested or overlapping, the order of
        // application determines the final color the text will have in the shared segment. Second
        // span overrides first span.

        // In our case, we need roles with children (such as TITLE or PARAGRAPH) to be applied
        // before inner roles (like EMPHASIS), so the child's properties override the parent's.
        private const val ROLE_TITLE_PRIORITY = 3
        private const val ROLE_PARAGRAPH_PRIORITY = 2
        private const val ROLE_EMPHASIS_PRIORITY = 1
    }

    private var sb: StyleBuilder = StyleBuilder("") // will be replaced

    fun toCharSequence(vararg args: String): CharSequence {
        val charSeq = context.getText(resId).trim()

        // If the string resource doesn't have any styled spans, avoid errors and just return it:
        if (charSeq !is SpannedString) return charSeq

        // Create a copy of the title text as a StyleBuilder to add and remove spans:
        sb = StyleBuilder(charSeq)

        // Apply ALL "arg" annotations FIRST
        sb.applyArgAnnotations(*args)

        // Count the annotations, but don't obtain the list right now. If we insert or remove
        // characters using the builder, the spans will be displaced:
        val annotationCount = sb.getAnnotationCount()

        // For each annotation insert/map to a custom span:
        for (i in 0 until annotationCount) {
            val annotation = sb.getAnnotations()[i]
            val range = sb.getSpanRange(annotation)

            apply(annotation, range)
        }

        // Now the spannableString contains both the annotation spans and the other(s) we added.
        return sb.toCharSequence()
    }

    private fun apply(a: Annotation, range: Range) {
        when (a.key) {
            FONT_COLOR -> applyColor(a, range)
            FONT_STYLE -> applyStyle(a, range)
            LINK -> applyLink(a, range)
            ROLE -> applyRole(a, range)

            else ->
                throw IllegalArgumentException("Annotation ${a.key} not supported")
        }
    }

    private fun applyColor(a: Annotation, range: Range) {
        // Match annotation to color resource:
        val colorRes = when (a.value) {
            FONT_COLOR_BLUE -> R.color.blue
            FONT_COLOR_BLACK -> R.color.text_primary_color

            else ->
                throw IllegalArgumentException("Color ${a.value} not supported")
        }

        // Create span:
        sb.addSpans(ForegroundColorSpan(color(colorRes)), range = range)
    }

    private fun applyStyle(a: Annotation, range: Range) {
        // Match annotation to style ID:
        val newSpan: ParcelableSpan = when (a.value) {
            FONT_STYLE_NORMAL -> StyleSpan(Typeface.NORMAL)
            FONT_STYLE_BOLD   -> StyleSpan(Typeface.BOLD)
            FONT_STYLE_ITALIC -> StyleSpan(Typeface.ITALIC)
            FONT_STYLE_UNDERLINED -> UnderlineSpan()

            else ->
                throw IllegalArgumentException("Style ${a.value} not supported")
        }

        // Create span:
        sb.addSpans(newSpan, range = range)
    }

    private fun applyLink(a: Annotation, range: Range) {
        sb.addSpans(
            ForegroundColorSpan(color(R.color.blue)),
            LinkSpan(a.value ?: "", onLinkClick),
            UnderlineSpan(),
            range = range
        )
    }

    private fun applyRole(a: Annotation, range: Range) {
        when (a.value) {
            ROLE_TITLE -> applyTitleRole(sb, range)
            ROLE_PARAGRAPH -> applyParagraphRole(sb, range)
            ROLE_EMPHASIS -> applyEmphasisRole(sb, range)

            else ->
                throw IllegalArgumentException("Role ${a.value} not supported")
        }
    }

    private fun applyTitleRole(sb: StyleBuilder, range: Range) {
        sb.addSpans(
            ForegroundColorSpan(color(R.color.text_primary_color)),
            StyleSpan(Typeface.BOLD),
            RelativeSizeSpan(1.125f),
            range = range,
            priority = ROLE_TITLE_PRIORITY
        )

        // Replace trailing spaces with some margin:
        while (range.end < sb.length && sb[range.end] == ' ') sb.remove(range.end)
        sb.addSpace(range.end, 0.2f)

        // Add some more margin above:
        sb.addSpace(range.start, 0.2f)
    }

    private fun applyParagraphRole(sb: StyleBuilder, range: Range) {
        sb.addSpans(
            ForegroundColorSpan(color(R.color.text_secondary_color)),
            range = range,
            priority = ROLE_PARAGRAPH_PRIORITY
        )

        // Replace trailing spaces with some margin:
        while (range.end < sb.length && sb[range.end] == ' ') sb.remove(range.end)
        sb.addSpace(range.end, 0.8f)
    }

    private fun applyEmphasisRole(sb: StyleBuilder, range: Range) {
        sb.addSpans(
            StyleSpan(Typeface.BOLD),
            ForegroundColorSpan(color(R.color.text_primary_color)),
            range = range,
            priority = ROLE_EMPHASIS_PRIORITY
        )
    }

    private fun color(@ColorRes resId: Int) =
        ContextCompat.getColor(context, resId)

}


private class Range(val start: Int, val end: Int)


private class StyleBuilder(source: CharSequence) {

    val ssb = SpannableStringBuilder(source)

    val length get() =
        ssb.length

    /**
     * Get all annotations except for "arg" annotations. They have different, special "regime". To
     * use them, see applyArgAnnotations method.
     */
    fun getAnnotations() =
        ssb.getSpans(0, ssb.length, Annotation::class.java)
            .filter {
                annotation -> annotation.key != ARG
            }

    fun getAnnotationCount() =
        getAnnotations().size

    fun addSpans(vararg spans: Any, range: Range, priority: Int = 0) {
        var flags = 0

        flags = flags or Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        flags = flags or (priority shl Spannable.SPAN_PRIORITY_SHIFT and Spannable.SPAN_PRIORITY)

        spans.forEach {
            ssb.setSpan(it, range.start, range.end, flags)
        }
    }

    fun getSpanRange(span: Any) =
        Range(ssb.getSpanStart(span), ssb.getSpanEnd(span))

    fun remove(at: Int) =
        ssb.replace(at, at + 1, "")

    fun addSpace(at: Int, factor: Float) {
        // Add some extra vertical space by introducing a cute resized empty line:
        ssb.insert(at, "\n\n")
        ssb.setSpan(RelativeSizeSpan(factor), at, at + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /**
     * Use our annotation mechanism to apply argument replacement. This should be done PRIOR to any
     * other application of style annotations, to avoid messing with other annotation span's ranges.
     */
    fun applyArgAnnotations(vararg args: Any) =
        ssb.applyArgAnnotations(*args)

    operator fun get(index: Int): Char =
        ssb[index]

    fun toCharSequence() =
        ssb // already a CharSequence
}

/**
 * Use our annotation mechanism to apply argument replacement. This should be done PRIOR to any
 * other application of style annotations, to avoid messing with other annotation span's ranges.
 */
fun SpannableStringBuilder.applyArgAnnotations(vararg args: Any) {

    val annotations = this.getSpans(0, this.length, Annotation::class.java)
    annotations.forEach { annotation ->

        when (annotation.key) {
            ARG -> {
                val argIndex = Integer.parseInt(annotation.value)
                when (val arg = args[argIndex]) {

                    is String -> {
                        this.replace(
                            this.getSpanStart(annotation),
                            this.getSpanEnd(annotation),
                            arg
                        )
                    }

                    // TODO add support for other arg types?
                }
            }
        }
    }
}
