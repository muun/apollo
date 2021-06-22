package io.muun.apollo.presentation.ui.view

import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.DrawableRes
import io.muun.apollo.presentation.ui.base.BaseActivity
import io.muun.apollo.presentation.ui.view.MuunActionDrawer.OnActionClickListener
import java.util.*

open class DrawerDialogFragment : MuunBottomSheetDialogFragment(), OnActionClickListener {

    var titleResId = 0

    private val actionList: MutableList<Action> = ArrayList()

    fun setTitle(resId: Int): DrawerDialogFragment {
        titleResId = resId
        return this
    }

    /**
     * Add an Action to this ActionDrawerDialog.
     */
    fun addAction(actionId: Int, label: String): DrawerDialogFragment {
        actionList.add(actionBuilder(actionId, label).build())
        return this
    }

    /**
     * Add an Action to this ActionDrawerDialog.
     */
    fun addAction(actionId: Int, @DrawableRes iconRes: Int, label: String): DrawerDialogFragment {
        val builder = actionBuilder(actionId, label)
        builder.iconRes(iconRes)
        actionList.add(builder.build())
        return this
    }

    /**
     * Add an Action to this ActionDrawerDialog.
     */
    fun addAction(actionId: Int, icon: Drawable, label: String): DrawerDialogFragment {
        val builder = actionBuilder(actionId, label)
        builder.icon(icon)
        actionList.add(builder.build())
        return this
    }

    private fun actionBuilder(actionId: Int, label: String): Action.Builder {
        return Action.Builder()
            .id(actionId)
            .label(label)
    }

    override fun createContentView(): View {
        val actionDrawer = createActionDrawer()
        if (titleResId > 0) {
            actionDrawer.setTitle(titleResId)
        }
        actionDrawer.setOnItemClickListener(this)
        for (action in actionList) {
            actionDrawer.addAction(action.actionId, action.icon, action.iconRes, action.label)
        }
        return actionDrawer
    }

    protected open fun createActionDrawer(): MuunActionDrawer {
        return MuunActionDrawer(requireContext())
    }

    override fun onActionClick(actionId: Int) {
        if (activity == null) {
            return  // Shouldn't really happen but this is Android so... defensive check
        }

        (activity as BaseActivity<*>).onDialogResult(this, actionId, null)
        dismiss()
    }

    private class Action(
        val actionId: Int = 0,
        val label: String? = null,

        // NOTE:
        // One of the following two fields should be set:
        // Custom Drawable, when an asset was dynamically loaded after the Drawer was initialized.
        val icon: Drawable? = null,

        // Drawable resource, when a bundled asset was specified during Fragment construction.
        val iconRes: Int? = null) {


        class Builder {
            private var actionId: Int = 0
            private var label: String? = null
            private var icon: Drawable? = null
            private var iconRes: Int? = null

            fun id(id: Int) =
                apply {
                    this.actionId = id
                }

            fun label(label: String) =
                apply {
                    this.label = label
                }

            fun icon(drawable: Drawable) =
                apply {
                    this.icon = drawable
                }

            fun iconRes(@DrawableRes iconRes: Int) =
                apply {
                    this.iconRes = iconRes
                }

            fun build(): Action {
                check(icon != null || iconRes != null)          // Only one of these should be set
                return Action(actionId, label, icon, iconRes)
            }
        }
    }
}