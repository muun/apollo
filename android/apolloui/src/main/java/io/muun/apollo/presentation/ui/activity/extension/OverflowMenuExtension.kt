package io.muun.apollo.presentation.ui.activity.extension

import android.view.Menu
import android.view.MenuItem
import android.view.View
import io.muun.apollo.presentation.analytics.Analytics
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.ActivityExtension
import io.muun.apollo.presentation.ui.base.di.PerActivity
import io.muun.apollo.presentation.ui.view.FloatingOverflowMenu
import rx.functions.Action0
import javax.inject.Inject

@PerActivity
class OverflowMenuExtension @Inject constructor(val analytics: Analytics) : ActivityExtension() {

    interface CustomOverflowMenu {
        fun onOverflowMenuCreate(contentView: View)
    }

    private var floatingMenu: FloatingOverflowMenu? = null

    private lateinit var builder: FloatingOverflowMenu.Builder

    fun setUpOverFlowMenu(builder: FloatingOverflowMenu.Builder) {
        this.builder = builder
        val wrappedListeners = mutableMapOf<Int, Action0>()
        for (listener in builder.listeners) {
            wrappedListeners[listener.key] = Action0 {
                listener.value.call()
                hideMenu()
            }
        }

        this.builder.listeners.clear()
        this.builder.listeners.putAll(wrappedListeners)
    }

    /**
     * Think of it as onResume.
     * Prepare the Screen's standard options menu to be displayed.  This is
     * called right before the menu is shown, every time it is shown.  You can
     * use this method to efficiently enable/disable items or otherwise
     * dynamically modify the contents.
     */
    fun onPrepareOptionsMenu(menu: Menu) {
        if (!::builder.isInitialized) {
            return
        }

        floatingMenu = builder.build()

        // This allows activities to set dynamic content to the overflow menu (e.g user pics)
        if (activity is CustomOverflowMenu) {
            (activity as CustomOverflowMenu).onOverflowMenuCreate(floatingMenu!!.contentView)
        }
    }

    fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Currently we have no activity with an OverflowMenu so we leave this mark. Users of this
        // extension should identify overflowMenu menu item with and id and put it here.
        val itemIsOverflowMenu = item.itemId == 0
        if (itemIsOverflowMenu) {
            showMenu()
            return true
        }

        return false
    }

    override fun onStop() {
        super.onStop()
        hideMenu()
        if (floatingMenu != null) {
            floatingMenu = null
        }
    }

    private fun showMenu() {
        floatingMenu!!.dismiss()

        analytics.report(AnalyticsEvent.E_MENU_TAP())

        floatingMenu!!.show(
            activity.findViewById(builder.parent),
            activity.findViewById(builder.toolbar)
        )
    }

    private fun hideMenu() {
        if (floatingMenu != null) {
            floatingMenu!!.dismiss()
        }
    }
}
