package io.muun.apollo.presentation.ui.view

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import butterknife.BindDimen
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.utils.UiUtils
import rx.functions.Action0

class FloatingOverflowMenu(
    context: Context,
    @LayoutRes menuLayoutId: Int
) : FloatingView(LayoutInflater.from(context).inflate(menuLayoutId, null)) {

    @BindDimen(R.dimen.muun_menu_offset)
    @JvmField
    internal var menuOffset: Int = 0

    val contentView: View = view

    fun addOnClickListener(@IdRes viewId: Int, onMenuUnpairClick: Action0) {
        view.findViewById<View>(viewId).setOnClickListener { v -> onMenuUnpairClick.call() }
    }

    /**
     * Show this floating overflow menu, at an specific offset relative to the TOP-END corner of
     * the parent's Window. We use the toolbar parameter to correctly set vertical offset.
     */
    fun show(parent: View, toolbar: View) {

        // This is needed to correctly place FloatingMenu. LOTS of issues regarding Android
        // status bar, notches, multi-window scenarios, etc... Believe me, you want this.
        // The problem arises because the PopupWindow API places the widget relative to a Window,
        // so in order to correctly place our FloatingMenu we need to account for status bar height,
        // which varies significantly in the scenarios above. We solve that by placing the menu
        // relative to our header (already below the status bar).
        val toolbarLocation = UiUtils.locateViewInWindow(toolbar)

        super.show(parent,
            Gravity.TOP or Gravity.END,
            menuOffset,
            menuOffset + toolbarLocation.top
        )
    }

    data class Builder(
        val activity: Activity,
        @LayoutRes val menuLayoutId: Int,
        @IdRes val parent: Int,
        @IdRes val toolbar: Int,
        val listeners: MutableMap<Int, Action0>) {

        // This is a workaround for Java's inability to call constructor with optional params
        // TODO: kotlinize caller class and remove this
        constructor(
            activity: Activity,
            menuLayoutId: Int,
            parent: Int,
            toolbar: Int
        ) : this(activity, menuLayoutId, parent, toolbar, mutableMapOf())

        fun addOnClickListener(@IdRes viewId: Int, onClick: Action0): Builder {
            listeners[viewId] = onClick
            return this
        }

        fun build(): FloatingOverflowMenu {
            val floatingOverflowMenu = FloatingOverflowMenu(activity, menuLayoutId)

            for (listener in listeners) {
                floatingOverflowMenu.addOnClickListener(listener.key, listener.value)
            }

            return floatingOverflowMenu
        }
    }
}
