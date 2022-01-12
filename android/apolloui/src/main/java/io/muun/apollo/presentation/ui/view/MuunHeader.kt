package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.graphics.PorterDuff
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import butterknife.BindColor
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.utils.isInNightMode

class MuunHeader : MuunView {

    companion object {
        val viewProps: ViewProps<MuunHeader> = ViewProps.Builder<MuunHeader>()
            .addBoolean(R.attr.elevated) { obj: MuunHeader, isElevated: Boolean ->
                obj.setElevated(isElevated)
            }
            .addRef(R.attr.titleTextAppearance) { obj: MuunHeader, id: Int ->
                obj.setTitleTextAppearance(id)
            }
            .build()
    }

    enum class Navigation {
        NONE, BACK, EXIT
    }

    @BindView(R.id.muun_header_toolbar)
    lateinit var toolbar: Toolbar

    @BindView(R.id.muun_header_indicator_text)
    lateinit var indicatorText: TextView

    @BindView(R.id.muun_header_drop_shadow)
    lateinit var dropShadow: View

    @JvmField
    @BindColor(R.color.toolbarColor)
    var defaultBackgroundColor = 0

    private var actionBar: ActionBar? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    override fun setUp(context: Context, attrs: AttributeSet?) {
        super.setUp(context, attrs)
        setBackgroundColor(defaultBackgroundColor)
        viewProps.transfer(attrs, this)
    }

    override val layoutResource: Int
        get() = R.layout.muun_header

    override fun setBackgroundColor(@ColorInt color: Int) {
        super.setBackgroundColor(color) // if not set on parent, elevation breaks
        toolbar.setBackgroundColor(color)
    }

    /**
     * Configure the navigation button.
     */
    fun setNavigation(navigation: Navigation) {
        checkAttached()
        return when (navigation) {
            Navigation.NONE -> {
                setNavigationVisible(false)
            }
            Navigation.BACK -> {
                setNavigationVisible(true)
                setNavigationIcon(R.drawable.ic_arrow_back)
            }
            Navigation.EXIT -> {
                setNavigationVisible(true)
                setNavigationIcon(R.drawable.ic_close)
            }
        }
    }

    /**
     * Show a title, taken from a string resource.
     */
    fun showTitle(@StringRes titleRes: Int) {
        showTitle(context.getString(titleRes))
    }

    /**
     * Show a title, taken as a literal string.
     */
    fun showTitle(title: String?) {
        checkAttached()
        actionBar!!.setDisplayShowTitleEnabled(true)
        actionBar!!.title = title
        toolbar.title = title
    }

    /**
     * Hide the title.
     */
    fun hideTitle() {
        checkAttached()
        actionBar!!.setDisplayShowTitleEnabled(false)
        toolbar.title = "" // a little hack, of the kind rarely necessary in Android
    }

    /**
     * Set to true to display a drop shadow below the Header.
     */
    fun setElevated(isElevated: Boolean) {
        // In dark theme, we disable shadow from top navbar
        // TODO: This is a temporary fix, we might work on this later
        var setElevated = isElevated
        if (this.isInNightMode()) {
            setElevated = false
        }
        dropShadow.visibility = if (setElevated) VISIBLE else GONE
    }

    /**
     * Set Header title.
     */
    fun setTitleTextAppearance(@StyleRes id: Int) {
        toolbar.setTitleTextAppearance(context, id)
    }

    /**
     * Set the right-hand indicator text (or `null` to remove it).
     */
    fun setIndicatorText(text: CharSequence?) {
        indicatorText.text = text
        indicatorText.visibility = if (TextUtils.isEmpty(text)) GONE else VISIBLE
    }

    /**
     * Remove all widgets and decorations, leave the header empty.
     */
    fun clear() {
        hideTitle()
        setNavigation(Navigation.NONE)
        setIndicatorText(null)
        setElevated(false)
        toolbar.menu.clear()
    }

    /**
     * Attach this Header to provide the ActionBar for an Activity.
     */
    fun attachToActivity(activity: AppCompatActivity) {
        activity.setSupportActionBar(toolbar)
        actionBar = activity.supportActionBar
    }

    private fun setNavigationVisible(isVisible: Boolean) {
        actionBar!!.setDisplayShowHomeEnabled(isVisible)
        actionBar!!.setDisplayHomeAsUpEnabled(isVisible)
    }

    private fun setNavigationIcon(@DrawableRes iconRes: Int) {
        // Set the icon:
        actionBar!!.setHomeAsUpIndicator(iconRes)

        // Tint:
        val drawable = toolbar.navigationIcon
        val color = ContextCompat.getColor(context, R.color.icon_color)
        drawable!!.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
    }

    private fun checkAttached() {
        checkNotNull(actionBar) { "No ActionBar: attachToActivity() was not called" }
    }
}