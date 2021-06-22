package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import javax.money.MonetaryAmount

class FeeManualItem @JvmOverloads constructor(context: Context, a: AttributeSet? = null, s: Int = 0)
    : MuunView(context, a, s) {

    @BindView(R.id.title)
    lateinit var titleView: TextView

    @BindView(R.id.amount)
    lateinit var amountView: TextView

    var currencyDisplayMode = CurrencyDisplayMode.BTC

    override fun getLayoutResource() =
        R.layout.fee_manual_item

    var title: String = ""
        set(value) {
            field = value
            titleView.text = value
        }

    var amount: MonetaryAmount? = null
        set(value) {
            field = value
            amountView.text = MoneyHelper.formatLongMonetaryAmount(value, currencyDisplayMode)
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