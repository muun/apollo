package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.utils.StyledStringRes

class MuunInfoBox @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0):
    MuunView(c, a, s) {

    companion object {
        val viewProps: ViewProps<MuunInfoBox> = ViewProps.Builder<MuunInfoBox>()
            .addRef(R.attr.picture) { v: MuunInfoBox, resId: Int? -> v.setPicture(resId!!)}
            .addString(R.attr.title) { v: MuunInfoBox, str: String? -> v.setTitle(str!!) }
            .addString(R.attr.description) { v: MuunInfoBox, str: String? ->
                v.setDescription(str!!)
            }
            .build()
    }

    @BindView(R.id.picture)
    lateinit var iconView: ImageView

    @BindView(R.id.title)
    lateinit var titleView: TextView

    @BindView(R.id.description)
    lateinit var descriptionView: HtmlTextView

    lateinit var onLinkClick: (linkId: String) -> Unit

    override fun getLayoutResource() =
        R.layout.muun_info_box

    override fun setUp(context: Context, attrs: AttributeSet?) {
        super.setUp(context, attrs)

        onLinkClick = {} // set here because setUp (in constructor) runs before static initializers
        viewProps.transfer(attrs, this)
    }

    fun setPicture(@DrawableRes resId: Int) {
        iconView.setImageResource(resId)
    }

    fun setTitle(@StringRes resId: Int) {
        setTitle(StyledStringRes(context, resId, this::callOnLink).toCharSequence())
    }

    fun setTitle(text: CharSequence) {
        titleView.text = text
    }

    fun setDescription(@StringRes resId: Int) {
        setDescription(StyledStringRes(context, resId, this::callOnLink).toCharSequence())
    }

    fun setDescription(text: CharSequence) {
        descriptionView.text = text
    }

    fun setOnLinkClickListener(f: (String) -> Unit) {
        onLinkClick = f
    }

    private fun callOnLink(linkId: String) {
        if (::onLinkClick.isInitialized) {
            onLinkClick(linkId)
        }
    }
}