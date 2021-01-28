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

class MuunDialog private constructor(
    private val layout: Int = 0,    // By default, we'll use AlertDialog default layout
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
    private val onClickActions: MutableMap<Int, View.OnClickListener> = mutableMapOf()) {

    class Builder {
        private var layout: Int = 0    // By default, we'll use AlertDialog default layout
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

        fun layout(@LayoutRes layout: Int) = apply { this.layout = layout }
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

        fun build() = MuunDialog(
            layout,
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
            onClickActions
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
            buildWithDefaultLayout(builder, context)

        } else {
            buildWithCustomLayout(context, builder)
        }

        alertDialog.show()

        return alertDialog
    }

    private fun buildWithDefaultLayout(builder: AlertDialog.Builder, ctx: Context): AlertDialog {


        val resolvedTitle: CharSequence? = title ?: resolveString(ctx, titleResId)
        val resolvedMessage: CharSequence? = message ?: resolveString(ctx, messageResId)

        // If we are showing a DEFAULT dialog it MUST have at least a title or a message
        Preconditions.checkState( resolvedTitle != null || resolvedMessage != null)

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

        val resolvedTitle: CharSequence? = title ?: resolveString(context, titleResId)
        val resolvedMessage: CharSequence? = message ?: resolveString(context, messageResId)

        if (resolvedTitle != null) {
            val customTitle = customLayout.findViewById<TextView>(R.id.dialog_title)
            customTitle.text = resolvedTitle
            customTitle.visibility = View.VISIBLE
        }

        if (resolvedMessage != null) {
            val customMessage = customLayout.findViewById<TextView>(R.id.dialog_message)
            customMessage.text = resolvedMessage
        }

        for (viewId in onClickActions.keys) {
            customLayout.findViewById<View>(viewId).setOnClickListener(onClickActions[viewId])
        }

        builder.setView(customLayout)

        val alertDialog = builder.create()

        if (positiveButtonResId != 0) {
            val positiveButton = customLayout.findViewById<TextView>(R.id.positive_button)
            positiveButton.text = context.getString(positiveButtonResId)
            positiveButton.setOnClickListener {
                positiveButtonAction?.call()
                alertDialog.dismiss()
            }
            positiveButton.visibility = View.VISIBLE
            customLayout.findViewById<View>(R.id.dialog_button_container).visibility = View.VISIBLE
        }

        if (negativeButtonResId != 0) {
            val negativeButton = customLayout.findViewById<TextView>(R.id.negative_button)
            negativeButton.text = context.getString(negativeButtonResId)
            negativeButton.setOnClickListener {
                negativeButtonAction?.call()
                alertDialog.dismiss()
            }
            negativeButton.visibility = View.VISIBLE
            customLayout.findViewById<View>(R.id.dialog_button_container).visibility = View.VISIBLE
        }

        return alertDialog
    }

    private fun resolveString(context: Context, resId: Int): CharSequence? {
        if (resId != 0) {
            return context.getString(resId)
        }

        return null
    }
}