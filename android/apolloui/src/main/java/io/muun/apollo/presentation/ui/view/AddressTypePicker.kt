package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.UiUtils

class AddressTypePicker @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0):
    Picker<AddressTypePicker.Option>(c, a, s) {

    class Option(
        id: Int,
        val title: CharSequence,
        val description: CharSequence,
        val status: AddressTypeCard.Status
    ): Picker.Option(id)

    @BindView(R.id.picker_card_container)
    lateinit var cardContainer: LinearLayout

    private var onOptionPickListener: OnOptionPickListener? = null

    override val layoutResource: Int
        get() = R.layout.view_address_type_picker

    override fun addOption(option: Option) {
        val view = AddressTypeCard(context)

        view.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            bottomMargin = UiUtils.dpToPx(context, 16)
        }

        view.id = option.id
        view.title = option.title
        view.description = option.description
        view.status = option.status

        view.setOnClickListener {
            onOptionPickListener?.onOptionPick(option.id)
        }

        cardContainer.addView(view)
    }

    override fun setOnOptionPickListener(listener: OnOptionPickListener) {
        this.onOptionPickListener = listener
    }
}
