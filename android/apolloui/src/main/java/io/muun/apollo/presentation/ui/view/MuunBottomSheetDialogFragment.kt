package io.muun.apollo.presentation.ui.view

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.coordinatorlayout.widget.CoordinatorLayout
import butterknife.ButterKnife
import butterknife.Unbinder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

abstract class MuunBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private val bottomSheetCallback: BottomSheetCallback = object : BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                dismiss()
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {}
    }

    private lateinit var contentView: View

    private lateinit var butterKnifeUnbinder: Unbinder

    override fun onStart() {
        super.onStart()
        getBehavior(contentView).state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onPause() {
        super.onPause()
        // Auto-dismiss. We want the fragment to be removed once it goes to the background.
        // This helps us avoid saving state to handle this fragment recreation (HARD) in case
        // activity/fragment gets destroyed while in background.
        dismiss()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        butterKnifeUnbinder = ButterKnife.bind(this, requireActivity())
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)

        contentView = createContentView()

        dialog.setContentView(contentView)
        getBehavior(contentView).addBottomSheetCallback(bottomSheetCallback)

        return dialog
    }

    protected abstract fun createContentView(): View

    @CallSuper
    override fun onDestroyView() {
        butterKnifeUnbinder.unbind()
        super.onDestroyView()
    }

    private fun getBehavior(content: View): BottomSheetBehavior<*> {
        val lp = (content.parent as ViewGroup).layoutParams as CoordinatorLayout.LayoutParams
        return lp.behavior as BottomSheetBehavior<*>
    }
}