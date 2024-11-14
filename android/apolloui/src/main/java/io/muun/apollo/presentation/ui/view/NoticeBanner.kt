package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.ViewNoticeBannerBinding
import io.muun.apollo.presentation.ui.utils.UiUtils

class NoticeBanner @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0) :
    MuunView(c, a, s) {

    companion object {
        val viewProps: ViewProps<NoticeBanner> = ViewProps.Builder<NoticeBanner>().run {
            addString(android.R.attr.text) { v: NoticeBanner, str: String -> v.setText(str) }
            addRef(android.R.attr.background) { v: NoticeBanner, resId: Int? ->
                v.setBackgroundResource(resId!!)
            }
            addRef(R.attr.icon) { v: NoticeBanner, resId: Int? -> v.setIcon(resId!!) }
            addRef(R.attr.tint) { v: NoticeBanner, resId: Int? -> v.setTint(resId!!) }
            build()
        }
    }

    private val binding: ViewNoticeBannerBinding
        get() = _binding as ViewNoticeBannerBinding

    private val icon: ImageView
        get() = binding.bannerIcon

    private val textView: TextView
        get() = binding.bannerText

    override val layoutResource: Int
        get() = R.layout.view_notice_banner

    override fun viewBinder(): ((View) -> ViewBinding) {
        return ViewNoticeBannerBinding::bind
    }

    override fun setUp(context: Context, attrs: AttributeSet?) {
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