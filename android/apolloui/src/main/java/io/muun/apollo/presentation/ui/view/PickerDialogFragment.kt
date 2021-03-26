package io.muun.apollo.presentation.ui.view

import android.view.View
import io.muun.apollo.presentation.ui.base.BaseActivity
import io.muun.apollo.presentation.ui.view.Picker.OnOptionChosenListener
import java.util.*

class PickerDialogFragment : MuunBottomSheetDialogFragment(), OnOptionChosenListener {

    private class Option(val id: Int, val label: String, val checked: Boolean = false)

    private var titleResId = 0

    private var optionList: MutableList<Option> = ArrayList()

    fun setTitle(resId: Int): PickerDialogFragment {
        titleResId = resId
        return this
    }

    /**
     * Add an Option to this PickerDialogFragment.
     */
    fun addOption(optionId: Int, label: String, checked: Boolean): PickerDialogFragment {
        optionList.add(Option(optionId, label, checked))
        return this
    }

    override fun createContentView(): View {
        val picker = Picker(requireContext())

        if (titleResId > 0) {
            picker.setTitle(titleResId)
        }

        for (option in optionList) {
            picker.addOption(option.id, option.label, option.checked)
        }

        picker.setOnOptionChosenListener(this)

        return picker
    }

    override fun onOptionChosen(optionId: Int) {
        (activity as BaseActivity<*>).onDialogResult(this, optionId, null)
        dismiss()
    }
}