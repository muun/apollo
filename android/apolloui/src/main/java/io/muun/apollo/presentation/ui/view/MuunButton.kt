package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Parcelable
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import butterknife.BindView
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.utils.UiUtils

class MuunButton @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0) :
    MuunView(c, a, s) {

    companion object {
        val viewProps: ViewProps<MuunButton> = ViewProps.Builder<MuunButton>()
            .addString(android.R.attr.text) { obj: MuunButton, text: String -> obj.setText(text) }
            .addSize(android.R.attr.textSize) { obj: MuunButton, pixelSize: Int ->
                obj.setTextSize(pixelSize)
            }
            .addDimension(android.R.attr.layout_width) { obj: MuunButton, widthSpec: Int ->
                obj.setWidthSpec(widthSpec)
            }
            .addRef(android.R.attr.background) { obj: MuunButton, resId: Int ->
                obj.setBackgroundResource(resId)
            }
            .addBoolean(android.R.attr.textAllCaps) { obj: MuunButton, allCaps: Boolean ->
                obj.setTextAllCaps(allCaps)
            }
            .addSize(android.R.attr.paddingLeft) { obj: MuunButton, paddingLeft: Int ->
                obj.paddingLeft = paddingLeft
            }
            .addSize(android.R.attr.paddingTop) { obj: MuunButton, paddingTop: Int ->
                obj.paddingTop = paddingTop
            }
            .addSize(android.R.attr.paddingRight) { obj: MuunButton, paddingRight: Int ->
                obj.paddingRight = paddingRight
            }
            .addSize(android.R.attr.paddingBottom) { obj: MuunButton, paddingBottom: Int ->
                obj.paddingBottom = paddingBottom
            }
            .addInt(android.R.attr.textStyle) { obj: MuunButton, style: Int ->
                obj.setTextStyle(style)
            }
            .addColorList(android.R.attr.textColor) { obj: MuunButton, colors: ColorStateList ->
                obj.setTextColor(colors)
            }
            .addInt(android.R.attr.typeface) { obj: MuunButton, typefaceIndex: Int ->
                obj.setTypeface(typefaceIndex)
            }
            .addString(android.R.attr.fontFamily) { obj: MuunButton, fontFamily: String ->
                obj.setFontFamily(fontFamily)
            }
            .build()

        const val TEXTSTYLE_UNDEFINED = -1

        private const val SANS = 1
        private const val SERIF = 2
        private const val MONOSPACE = 3
    }

    @BindView(R.id.muun_button_button)
    lateinit var button: Button

    @BindView(R.id.muun_button_progress_bar)
    lateinit var progressBar: ProgressBar

    @BindView(R.id.muun_button_cover)
    lateinit var coverView: TextView

    // This is meant for private (e.g only this class) use, but IcePick requires it to be public.
    // If you are an external caller move along to setLoading/isLoading methods.
    @State
    @JvmField
    var mIsLoading = false

    // This is meant for private (e.g only this class) use, but IcePick requires it to be public.
    // If you are an external caller move along to setEnabled/isEnabled methods.
    @JvmField
    @State
    var mIsEnabled = false

    @JvmField
    @State
    var backgroundRes: Int? = null

    @JvmField
    @State
    var buttonText: String? = null

    @JvmField
    @State
    var coverText: String? = null

    // These fields do not use @State because they are only set from attrs (and/or at creation)
    // if they could change after creation, they should use it.
    private var textStyle = 0
    private var typeface: Typeface? = null
    private var fontFamily: String? = null

    override val layoutResource: Int
        get() = R.layout.muun_button

    override fun setUp(context: Context, attrs: AttributeSet?) {
        // Setting defaults in here because setUp is called INSIDE the view constructor
        textStyle = TEXTSTYLE_UNDEFINED
        mIsEnabled = true
        super.setUp(context, attrs)
        viewProps.transfer(attrs, this)
        setTypefaceFromAttrs()
        if (backgroundRes == null) {
            backgroundRes = R.drawable.muun_button_default_bg
        }

        // Normally, progressBar color is set through `colorAccent`, but we don't use themes:
        progressBar.indeterminateDrawable
            .setColorFilter(button.currentTextColor, PorterDuff.Mode.MULTIPLY)
        updateFromState()
    }

    override fun onRestoreInstanceState(parcelable: Parcelable) {
        super.onRestoreInstanceState(parcelable)
        setLoading(mIsLoading)
        isEnabled = mIsEnabled
    }

    override fun setOnClickListener(clickListener: OnClickListener?) {
        button.setOnClickListener(clickListener)
    }

    override fun callOnClick(): Boolean {
        /*
         * This method goes hand in hand with @setOnClickListener. If we assign #onClickListener
         * to an inner view, we should redirect @callOnClick too. A similar case could be made for
         * #performOnClick.
         */
        return button.callOnClick()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        mIsEnabled = enabled
        updateFromState()
    }

    override fun setBackgroundResource(@DrawableRes resId: Int) {
        backgroundRes = resId
        updateFromState()
    }

    fun setText(text: CharSequence) {
        buttonText = text.toString()
        button.text = text
    }

    fun setText(@StringRes resid: Int) {
        setText(context.getString(resid))
    }

    /**
     * Set button's text size, in px.
     */
    fun setTextSize(pixelSize: Int) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, pixelSize.toFloat())
    }

    /**
     * Set text size to a given unit and value.  See [ ] for the possible dimension units.
     */
    private fun setTextSize(unit: Int, size: Float) {
        button.setTextSize(unit, size)
    }

    fun setTextAllCaps(allCaps: Boolean) {
        button.isAllCaps = allCaps
    }

    fun setTextColor(color: Int) {
        button.setTextColor(color)
    }

    fun setTextColor(colors: ColorStateList) {
        button.setTextColor(colors)
    }

    /**
     * Set button's left padding, in px.
     */
    fun setPaddingLeft(paddingLeft: Int) {
        super.setPadding(0, paddingTop, paddingRight, paddingBottom)
        UiUtils.setPaddingLeft(button, paddingLeft)
    }

    /**
     * Set button's top padding, in px.
     */
    fun setPaddingTop(paddingTop: Int) {
        super.setPadding(paddingLeft, 0, paddingRight, paddingBottom)
        UiUtils.setPaddingTop(button, paddingTop)
    }

    /**
     * Set button's right padding, in px.
     */
    fun setPaddingRight(paddingRight: Int) {
        super.setPadding(paddingLeft, paddingTop, 0, paddingBottom)
        UiUtils.setPaddingRight(button, paddingRight)
    }

    /**
     * Set button's bottom padding, in px.
     */
    fun setPaddingBottom(paddingBottom: Int) {
        super.setPadding(paddingLeft, paddingTop, paddingRight, 0)
        UiUtils.setPaddingBottom(button, paddingBottom)
    }

    fun setWidth(widthSpec: Int) {
        setWidthSpec(widthSpec)
        requestLayout()
    }

    /**
     * Set the loading state on this button.
     */
    fun setLoading(isLoading: Boolean) {
        this.mIsLoading = isLoading
        updateFromState()
    }

    fun isLoading(): Boolean {
        return mIsLoading
    }

    fun setCoverText(coverText: String?) {
        this.coverText = coverText
        updateFromState()
    }

    /**
     * Set button's typeface and style in which the text should be displayed.
     *
     * @see TextView.setTypeface
     */
    fun setTypeface(tf: Typeface, style: Int) {
        setTextStyle(style)
        typeface = tf
        button.setTypeface(tf, style)
    }

    private fun setTypeface(typefaceIndex: Int) {
        when (typefaceIndex) {
            SANS -> typeface = Typeface.SANS_SERIF
            SERIF -> typeface = Typeface.SERIF
            MONOSPACE -> typeface = Typeface.MONOSPACE
            else -> {}
        }
    }

    private fun setTextStyle(style: Int) {
        textStyle = style
    }

    private fun setFontFamily(fontFamily: String) {
        this.fontFamily = fontFamily
    }

    private fun setWidthSpec(widthSpec: Int) {
        // widthSpec could be an exact size (in px) or MATCH_PARENT, WRAP_CONTENT constants
        button.layoutParams.width = widthSpec
    }

    private fun setTypefaceFromAttrs() {
        // @see android.widget.TextView#setTypefaceFromAttrs()
        if (fontFamily != null) {
            val tf = Typeface.create(fontFamily, textStyle)
            if (tf != null) {
                button.typeface = tf
                return
            }
        }
        if (textStyle != TEXTSTYLE_UNDEFINED || typeface != null) {
            button.setTypeface(typeface, textStyle)
        }
    }

    private fun updateFromState() {
        when {
            coverText != null -> updateFromCoveredState()
            mIsLoading -> updateFromLoadingState()
            mIsEnabled -> updateFromEnabledState()
            else -> updateFromDisabledState()
        }
    }

    private fun updateFromEnabledState() {
        button.text = buttonText
        button.isEnabled = true
        button.visibility = VISIBLE
        updateButtonBackground(backgroundRes!!, android.R.attr.state_enabled)

        progressBar.visibility = GONE
        coverView.visibility = GONE
    }

    private fun updateFromDisabledState() {
        button.text = buttonText
        button.isEnabled = false
        button.visibility = VISIBLE
        updateButtonBackground(backgroundRes!!)

        progressBar.visibility = GONE
        coverView.visibility = GONE
    }

    private fun updateFromLoadingState() {
        button.text = ""
        button.isEnabled = false
        button.visibility = VISIBLE
        updateButtonBackground(backgroundRes!!, android.R.attr.state_enabled)

        progressBar.visibility = VISIBLE
        coverView.visibility = GONE
    }

    private fun updateFromCoveredState() {
        button.visibility = GONE
        progressBar.visibility = GONE
        coverView.text = coverText
        coverView.visibility = VISIBLE
    }

    private fun updateButtonBackground(newBackgroundRes: Int, vararg backgroundStates: Int) {
        button.background = UiUtils.getDrawableForState(
            ContextCompat.getDrawable(context, newBackgroundRes),
            backgroundStates
        )
    }
}