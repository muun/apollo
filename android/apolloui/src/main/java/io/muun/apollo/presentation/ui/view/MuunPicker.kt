package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import io.muun.apollo.databinding.ViewPickerBinding

class MuunPicker @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0) :
    Picker<MuunPicker.Option, ViewPickerBinding>(c, a, s) {

    class Option(
        id: Int,
        val title: CharSequence,
        val description: CharSequence,
        val status: PickerCard.Status,
    ) : Picker.Option(id)

    private val titleView get() = binding.pickerTitle
    private val cardContainer get() = binding.pickerCardContainer

    private var selectedView: PickerCard? = null

    private var onOptionPickListener: OnOptionPickListener? = null

    override fun bindingInflater(): BindingInflater<ViewPickerBinding> {
        return ViewPickerBinding::inflate
    }

    override fun setTitle(title: CharSequence) {
        titleView.text = title
        titleView.visibility = VISIBLE
    }

    override fun addOption(option: Option) {
        val view = PickerCard(context)

        view.id = option.id
        view.title = option.title
        view.description = option.description
        view.status = option.status

        if (view.status == PickerCard.Status.SELECTED) {
            selectedView = view
        }

        view.setOnClickListener {
            selectedView?.status = PickerCard.Status.NORMAL
            post {  // Needed to give time for layout/UI to reflect above change
                onOptionPickListener?.onOptionPick(option.id)
            }
        }

        cardContainer.addView(view)
    }

    override fun setOnOptionPickListener(listener: OnOptionPickListener) {
        this.onOptionPickListener = listener
    }
}
