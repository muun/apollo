package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import io.muun.apollo.R
import io.muun.apollo.databinding.FeeManualItemBinding
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.presentation.ui.utils.locale
import javax.money.MonetaryAmount

class FeeManualItem @JvmOverloads constructor(
    context: Context,
    a: AttributeSet? = null,
    s: Int = 0
) : MuunView<FeeManualItemBinding>(context, a, s) {

    private val titleView get() = binding.title
    private val amountView get() = binding.amount

    var bitcoinUnit = BitcoinUnit.BTC

    override fun bindingInflater(): BindingInflater<FeeManualItemBinding> {
        return FeeManualItemBinding::inflate
    }

    var title: String = ""
        set(value) {
            field = value
            titleView.text = value
        }

    var amount: MonetaryAmount? = null
        set(value) {
            field = value
            amountView.text = MoneyHelper.formatLongMonetaryAmount(
                value!!,
                bitcoinUnit,
                locale()
            )
        }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)

        if (selected) {
            amountView.visibility = VISIBLE
            title = context.getString(R.string.manual_fee_entered)
        } else {
            amountView.visibility = GONE
            title = context.getString(R.string.enter_fee_manually)
        }
    }
}