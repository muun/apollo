package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.view.View
import io.muun.apollo.presentation.ui.base.BaseActivity

class PickerDialogFragment: MuunBottomSheetDialogFragment(), Picker.OnOptionPickListener {

    private var pickerFactory: ((Context) -> Picker<*>)? = null

    fun setPickerFactory(pickerFactory: (Context) -> Picker<*>) {
        this.pickerFactory = pickerFactory
    }

    override fun createContentView(): View {
        val factory = pickerFactory ?: throw IllegalStateException("Call setPickerFactory first!")
        val picker = factory(requireContext())

        picker.setOnOptionPickListener(this)

        return picker
    }

    override fun onOptionPick(optionId: Int) {
        (activity as BaseActivity<*>).onDialogResult(this, optionId, null)
        dismiss()
    }
}