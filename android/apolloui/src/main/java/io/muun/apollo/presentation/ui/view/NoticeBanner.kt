package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.utils.UiUtils

class NoticeBanner @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0):
    MuunView(c, a, s) {

    companion object {
        val viewProps: ViewProps<NoticeBanner> = ViewProps.Builder<NoticeBanner>().run {
            addString(android.R.attr.text) { v: NoticeBanner, str: String? -> v.setText(str!!)}
            addRef(android.R.attr.background) { v: NoticeBanner, resId: Int? ->
                v.setBackgroundResource(resId!!)
            }
            addRef(R.attr.icon) { v: NoticeBanner, resId: Int? -> v.setIcon(resId!!) }
            addRef(R.attr.tint) { v: NoticeBanner, resId: Int? -> v.setTint(resId!!) }
            build()
        }
    }

    @BindView(R.id.banner_icon)
    internal lateinit var icon: ImageView

    @BindView(R.id.banner_text)
    internal lateinit var textView: TextView

    override fun getLayoutResource(): Int =
        R.layout.view_notice_banner

    override fun setUp(context: Context?, attrs: AttributeSet?) {
        super.setUp(context, attrs)

        viewProps.transfer(attrs, this)
    }

    fun setText(text: String) {
        textView.text = text
    }

    fun setText(text: CharSequence) {
        textView.text = text
    }

    fun setIcon(@DrawableRes iconRes: Int) {
        icon.setImageDrawable(ContextCompat.getDrawable(context, iconRes))
    }

    fun setTint(@ColorRes colorId: Int) {
        UiUtils.setTint(icon, colorId)
    }
}