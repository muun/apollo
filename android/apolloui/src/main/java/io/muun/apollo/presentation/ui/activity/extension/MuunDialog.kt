package io.muun.apollo.presentation.ui.activity.extension

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.utils.UiUtils
import io.muun.common.utils.Preconditions
import rx.functions.Action0
import javax.annotation.CheckReturnValue

typealias MuunDialogInitializer = (View, AlertDialog) -> Unit

class MuunDialog private constructor(
    private val layout: Int = 0,    // By default, we'll use AlertDialog default layout
    private val dialogInit: MuunDialogInitializer? = null,
    private val style: Int = R.style.MuunAlertDialog,
    private val fixedWidthInDp: Int = 0,
    private val titleResId: Int = 0,
    private val title: CharSequence? = null,
    private val messageResId: Int = 0,
    private val message: CharSequence? = null,
    private val positiveButtonResId: Int = 0,
    private var positiveButtonColorId: Int = 0,
    private val positiveButtonAction: Action0? = null,
    private val negativeButtonResId: Int = 0,
    private var negativeButtonColorId: Int = 0,
    private val negativeButtonAction: Action0? = null,
    private val dismissActions: MutableList<DialogInterface.OnDismissListener> = mutableListOf(),
    // Only for custom layout dialogs
    private val onClickActions: MutableMap<Int, View.OnClickListener> = mutableMapOf(),
    private val cancelOnTouchOutside: Boolean?,
) {

    class Builder {
        private var layout: Int = 0    // By default, we'll use AlertDialog default layout
        private var dialogInit: MuunDialogInitializer? = null
        private var style: Int = R.style.MuunAlertDialog
        private var fixedWidthInDp: Int = 0
        private var titleResId: Int = 0
        private var title: CharSequence? = null
        private var messageResId: Int = 0
        private var message: CharSequence? = null
        private var positiveButtonResId: Int = 0
        private var positiveButtonColorId: Int = 0
        private var positiveButtonAction: Action0? = null
        private var negativeButtonResId: Int = 0
        private var negativeButtonColorId: Int = 0
        private var negativeButtonAction: Action0? = null
        private val dismissActions: MutableList<DialogInterface.OnDismissListener> = mutableListOf()

        // Only for custom layout dialogs
        private val onClickActions: MutableMap<Int, View.OnClickListener> = mutableMapOf()
        private var cancelOnTouchOutside: Boolean? = null

        @CheckReturnValue
        fun layout(@LayoutRes layout: Int) = apply {
            this.layout = layout
        }

        @CheckReturnValue
        fun layout(@LayoutRes layout: Int, dialogInit: MuunDialogInitializer) = apply {
            this.layout = layout
            this.dialogInit = dialogInit
        }

        @CheckReturnValue
        fun fixedWidthInDp(widthInDp: Int) = apply {
            this.fixedWidthInDp = widthInDp
        }

        @CheckReturnValue
        fun style(@StyleRes style: Int) = apply { this.style = style }

        @CheckReturnValue
        fun title(@StringRes titleResId: Int) = apply {
            this.titleResId = titleResId
            this.title = null
        }

        @CheckReturnValue
        fun title(title: CharSequence) = apply {
            this.title = title
            this.titleResId = 0
        }

        @CheckReturnValue
        fun message(@StringRes messageResId: Int) = apply {
            this.messageResId = messageResId
            this.message = null
        }

        @CheckReturnValue
        fun message(message: CharSequence) = apply {
            this.message = message
            this.messageResId = 0
        }

        fun positiveButton(@StringRes resId: Int, color: Int = 0, action: Action0? = null) =
            apply {
                this.positiveButtonResId = resId
                this.positiveButtonColorId = color
                this.positiveButtonAction = action
            }

        @CheckReturnValue
        fun negativeButton(@StringRes resId: Int, color: Int = 0, action: Action0? = null) =
            apply {
                this.negativeButtonResId = resId
                this.negativeButtonColorId = color
                this.negativeButtonAction = action
            }

        @CheckReturnValue
        fun onDismiss(action: DialogInterface.OnDismissListener) =
            apply { this.dismissActions.add(action) }

        @CheckReturnValue
        fun addOnClickAction(@IdRes viewId: Int, action: View.OnClickListener) = apply {
            this.onClickActions[viewId] = action
        }

        @CheckReturnValue
        fun setCancelOnTouchOutside(cancel: Boolean) =
            apply {
                this.cancelOnTouchOutside = cancel
            }

        @CheckReturnValue
        fun build() = MuunDialog(
            layout,
            dialogInit,
            style,
            fixedWidthInDp,
            titleResId,
            title,
            messageResId,
            message,
            positiveButtonResId,
            positiveButtonColorId,
            positiveButtonAction,
            negativeButtonResId,
            negativeButtonColorId,
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

                dismissActions.iterator().forEach {
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

        // Workaround for nasty dialog width bug in foldable devices and tablets regarding
        // welcome to Muun dialog (Relative layout is causing trouble? ConstraintLayout has same
        // issue.)
        if (fixedWidthInDp != 0) {
            alertDialog.window!!.setLayout(
                UiUtils.dpToPx(context, fixedWidthInDp),
                LayoutParams.WRAP_CONTENT
            )
        }

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
            with(view.findViewById<TextView>(R.id.positive_button)) {
                text = context.getString(positiveButtonResId)
                if(positiveButtonColorId != 0) {
                    setTextColor(positiveButtonColorId)
                }
                setOnClickListener {
                    positiveButtonAction?.call()
                    dialog.dismiss()
                }
                visibility = View.VISIBLE
            }
        }

        if (negativeButtonResId != 0) {
            with(view.findViewById<TextView>(R.id.negative_button)) {
                text = context.getString(negativeButtonResId)
                if(negativeButtonColorId != 0) {
                    setTextColor(negativeButtonColorId)
                }
                setOnClickListener {
                    negativeButtonAction?.call()
                    dialog.dismiss()
                }
                visibility = View.VISIBLE
            }
        }
    }

    private fun resolveString(context: Context, resId: Int): CharSequence? {
        if (resId != 0) {
            return context.getString(resId)
        }

        return null
    }
}