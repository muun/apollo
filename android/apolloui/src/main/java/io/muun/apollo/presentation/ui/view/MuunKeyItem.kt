package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.widget.TextView
import butterknife.BindColor
import butterknife.BindView
import io.muun.apollo.R

class MuunKeyItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    style: Int = 0
): MuunView(context, attrs, style) {

    @BindView(R.id.text)
    internal lateinit var textView: TextView

    @BindColor(R.color.muun_blue_pale)
    @JvmField
    internal var oddColorBg: Int = 0

    @BindColor(R.color.muun_white)
    @JvmField
    internal var evenColorBg: Int = 0

    @BindColor(R.color.muun_blue)
    @JvmField
    internal var digitColor: Int = 0

    override fun getLayoutResource() =
        R.layout.muun_key_item

    var index: Int = 1
        set(value) {
            field = value
            render()
        }

    var keyPart: String = ""
        set(value) {
            field = value
            render()
        }

    private fun render() {
        var indexText = RichText("%02d".format(index + 1))
            .setRelativeSize(0.7f)
            .setBold()

        val keyPartTexts = keyPart.map {
            when {
                it.isDigit() -> renderDigit(it)
                it.isUpperCase() -> renderUpperCase(it)
                else -> renderOther(it)
            }
        }

        textView.text = TextUtils.concat(indexText, ".  ", *keyPartTexts.toTypedArray())
        textView.setBackgroundColor(if (index % 2 == 0) evenColorBg else oddColorBg)
    }

    private fun renderDigit(ch: Char) =
        RichText(ch).setForegroundColor(digitColor).setFontFamily("monospace")

    private fun renderUpperCase(ch: Char) =
        RichText(ch).setUnderline().setFontFamily("monospace")

    private fun renderOther(ch: Char) =
        RichText(ch).setFontFamily("monospace")
}