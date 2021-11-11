package io.muun.apollo.presentation.ui.activity.extension

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import io.muun.apollo.R
import io.muun.common.utils.Preconditions
import rx.functions.Action0

typealias MuunDialogInitializer = (View, AlertDialog) -> Unit

class MuunDialog private constructor(
    private val layout: Int = 0,    // By default, we'll use AlertDialog default layout
    private val dialogInit: MuunDialogInitializer? = null,
    private val style: Int = R.style.MuunAlertDialog,
    private val titleResId: Int = 0,
    private val title: CharSequence? = null,
    private val messageResId: Int = 0,
    private val message: CharSequence? = null,
    private val positiveButtonResId: Int = 0,
    private val positiveButtonAction: Action0? = null,
    private val negativeButtonResId: Int = 0,
    private val negativeButtonAction: Action0? = null,
    private val dismissActions: MutableList<DialogInterface.OnDismissListener> = mutableListOf(),
    // Only for custom layout dialogs
    private val onClickActions: MutableMap<Int, View.OnClickListener> = mutableMapOf(),
    private val cancelOnTouchOutside: Boolean?
) {

    class Builder {
        private var layout: Int = 0    // By default, we'll use AlertDialog default layout
        private var dialogInit: MuunDialogInitializer? = null
        private var style: Int = R.style.MuunAlertDialog
        private var titleResId: Int = 0
        private var title: CharSequence? = null
        private var messageResId: Int = 0
        private var message: CharSequence? = null
        private var positiveButtonResId: Int = 0
        private var positiveButtonAction: Action0? = null
        private var negativeButtonResId: Int = 0
        private var negativeButtonAction: Action0? = null
        private val dismissActions: MutableList<DialogInterface.OnDismissListener> = mutableListOf()
        // Only for custom layout dialogs
        private val onClickActions: MutableMap<Int, View.OnClickListener> = mutableMapOf()
        private var cancelOnTouchOutside: Boolean? = null

        fun layout(@LayoutRes layout: Int) = apply {
            this.layout = layout
        }

        fun layout(@LayoutRes layout: Int, dialogInit: MuunDialogInitializer) = apply {
            this.layout = layout
            this.dialogInit = dialogInit
        }

        fun style(@StyleRes style: Int) = apply { this.style = style }
        fun title(@StringRes titleResId: Int) = apply {
            this.titleResId = titleResId
            this.title = null
        }
        fun title(title: CharSequence) = apply {
            this.title = title
            this.titleResId = 0
        }
        fun message(@StringRes messageResId: Int) = apply {
            this.messageResId = messageResId
            this.message = null
        }
        fun message(message: CharSequence) = apply {
            this.message = message
            this.messageResId = 0
        }

        fun positiveButton(@StringRes resId: Int, action: Action0? = null) =
            apply {
                this.positiveButtonResId = resId
                this.positiveButtonAction = action
            }

        fun negativeButton(@StringRes resId: Int, action: Action0? = null) =
            apply {
                this.negativeButtonResId = resId
                this.negativeButtonAction = action
            }

        fun onDismiss(action: DialogInterface.OnDismissListener) =
            apply { this.dismissActions.add(action) }

        fun addOnClickAction(@IdRes viewId: Int, action: View.OnClickListener) = apply {
            this.onClickActions[viewId] = action
        }

        fun setCancelOnTouchOutside(cancel: Boolean) =
            apply {
                this.cancelOnTouchOutside = cancel
            }

        fun build() = MuunDialog(
            layout,
            dialogInit,
            style,
            titleResId,
            title,
            messageResId,
            message,
            positiveButtonResId,
            positiveButtonAction,
            negativeButtonResId,
            negativeButtonAction,
            dismissActions,
            onClickActions,
            cancelOnTouchOutside
        )
    }

    fun addOnDismissAction(action: DialogInterface.OnDismissListener) {
        this.dismissActions.add(action)
    }

    fun show(context: Context): AlertDialog {

        val builder = AlertDialog.Builder(context, style)
            .setOnDismissListener { alertDialog ->

                dismissActions.forEach {
                    it.onDismiss(alertDialog)
                }
            }

        val alertDialog = if (layout == 0) {
            // Show an Android Alert Dialog with default layout
            buildWithDefaultLayout(context, builder)

        } else {
            buildWithCustomLayout(context, builder)
        }

        cancelOnTouchOutside?.let(alertDialog::setCanceledOnTouchOutside)

        alertDialog.show()

        return alertDialog
    }

    private fun buildWithDefaultLayout(ctx: Context, builder: AlertDialog.Builder): AlertDialog {
        val resolvedTitle: CharSequence? = title ?: resolveString(ctx, titleResId)
        val resolvedMessage: CharSequence? = message ?: resolveString(ctx, messageResId)

        // If we are showing a DEFAULT dialog it MUST have at least a title or a message
        Preconditions.checkState(resolvedTitle != null || resolvedMessage != null)

        if (resolvedTitle != null) {
            builder.setTitle(resolvedTitle)
        }

        if (resolvedMessage != null) {
            builder.setMessage(resolvedMessage)
        }

        if (positiveButtonResId != 0) {
            builder.setPositiveButton(positiveButtonResId) { _, _ ->
                positiveButtonAction?.call()
            }
        }

        if (negativeButtonResId != 0) {
            builder.setNegativeButton(negativeButtonResId) { _, _ ->
                negativeButtonAction?.call()
            }
        }

        return builder.create()
    }

    private fun buildWithCustomLayout(context: Context, builder: AlertDialog.Builder): AlertDialog {
        val customLayout = LayoutInflater.from(context).inflate(layout, null)
        builder.setView(customLayout)

        val alertDialog = builder.create()

        val viewInitOrDefault = dialogInit ?: this::initializeViewWithoutCustomInit
        viewInitOrDefault(customLayout, alertDialog)

        return alertDialog
    }

    /**
     * Default initializer for dialogs with custom Views that include the basic components of
     * a common dialog.
     */
    private fun initializeViewWithoutCustomInit(view: View, dialog: AlertDialog) {
        val context = view.context

        val resolvedTitle: CharSequence? = title ?: resolveString(context, titleResId)
        val resolvedMessage: CharSequence? = message ?: resolveString(context, messageResId)

        if (resolvedTitle != null) {
            val customTitle = view.findViewById<TextView>(R.id.dialog_title)
            customTitle.text = resolvedTitle
            customTitle.visibility = View.VISIBLE
        }

        if (resolvedMessage != null) {
            val customMessage = view.findViewById<TextView>(R.id.dialog_message)
            customMessage.text = resolvedMessage
        }

        for (viewId in onClickActions.keys) {
            view.findViewById<View>(viewId).setOnClickListener(onClickActions[viewId])
        }

        if (positiveButtonResId != 0) {
            val positiveButton = view.findViewById<TextView>(R.id.positive_button)
            positiveButton.text = context.getString(positiveButtonResId)
            positiveButton.setOnClickListener {
                positiveButtonAction?.call()
                dialog.dismiss()
            }
            positiveButton.visibility = View.VISIBLE
            view.findViewById<View>(R.id.dialog_button_container).visibility = View.VISIBLE
        }

        if (negativeButtonResId != 0) {
            val negativeButton = view.findViewById<TextView>(R.id.negative_button)
            negativeButton.text = context.getString(negativeButtonResId)
            negativeButton.setOnClickListener {
                negativeButtonAction?.call()
                dialog.dismiss()
            }
            negativeButton.visibility = View.VISIBLE
            view.findViewById<View>(R.id.dialog_button_container).visibility = View.VISIBLE
        }

    }

    private fun resolveString(context: Context, resId: Int): CharSequence? {
        if (resId != 0) {
            return context.getString(resId)
        }

        return null
    }
}