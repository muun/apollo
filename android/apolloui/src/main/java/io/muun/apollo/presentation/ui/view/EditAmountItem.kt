package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import butterknife.BindColor
import butterknife.BindDrawable
import butterknife.BindView
import butterknife.OnClick
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.domain.utils.locale
import io.muun.apollo.presentation.ui.bundler.MonetaryAmountBundler
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.presentation.ui.utils.locale
import io.muun.apollo.presentation.ui.utils.setDrawableTint
import javax.money.MonetaryAmount

class EditAmountItem @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0):
    MuunView(c, a, s) {

    interface EditAmountHandler {
        fun onEditAmount(amount: MonetaryAmount?)
    }

    companion object {
        val viewProps: ViewProps<EditAmountItem> = ViewProps.Builder<EditAmountItem>()
            .addString(R.attr.label) { us, labelText -> us.setLabel(labelText) }
            .build()
    }

    @BindView(R.id.amount_label)
    lateinit var label: TextView

    @BindView(R.id.selected_amount)
    lateinit var selectedAmount: TextView

    @BindView(R.id.add_amount)
    lateinit var addAmountButton: View

    // Resources:

    @BindDrawable(R.drawable.ic_edit_black_24)
    lateinit var editIcon: Drawable

    @BindColor(R.color.muun_button_primary_bg_disabled)
    @JvmField
    internal var disabledIconColor: Int = 0

    @BindColor(R.color.blue)
    @JvmField
    internal var enabledIconColor: Int = 0

    // State:

    @State(MonetaryAmountBundler::class)
    @JvmField
    var amount: MonetaryAmount? = null

    private lateinit var editAmountHandler: EditAmountHandler

    override val layoutResource: Int
        get() = R.layout.edit_amount_item

    override fun setUp(context: Context, attrs: AttributeSet?) {
        super.setUp(context, attrs)
        viewProps.transfer(attrs, this)
    }

    fun setLabel(labelText: CharSequence) {
        label.text = labelText
    }

    fun setAmount(amount: MonetaryAmount, mode: CurrencyDisplayMode) {
        this.amount = amount
        selectedAmount.text = MoneyHelper.formatLongMonetaryAmount(amount, mode, locale())
        selectedAmount.visibility = View.VISIBLE
        addAmountButton.visibility = View.GONE

        label.setCompoundDrawablesWithIntrinsicBounds(null, null, editIcon, null)
        label.setOnClickListener {
            editAmountHandler.onEditAmount(amount)
        }
    }

    fun resetAmount() {
        amount = null
        selectedAmount.visibility = View.GONE
        addAmountButton.visibility = View.VISIBLE
        label.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
        label.setOnClickListener(null)
    }

    fun setLoading(isloading: Boolean) {
        this.isClickable = !isloading
        updateEditIconColor(isloading)
    }

    fun setEditAmountHandler(editAmountHandler: EditAmountHandler) {
        this.editAmountHandler = editAmountHandler
    }

    @OnClick(R.id.add_amount)
    fun onAddAmountClick() {
        editAmountHandler.onEditAmount(amount)
    }

    private fun updateEditIconColor(isloading: Boolean) {
        val tintColor = if (isloading) disabledIconColor else enabledIconColor
        label.setDrawableTint(tintColor)
    }
}