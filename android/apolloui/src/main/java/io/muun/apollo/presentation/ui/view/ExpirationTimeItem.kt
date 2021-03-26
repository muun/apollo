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
import io.muun.apollo.presentation.ui.bundler.MonetaryAmountBundler
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.presentation.ui.utils.UiUtils
import javax.money.MonetaryAmount

class ExpirationTimeItem @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0):
    MuunView(c, a, s) {

    companion object {

        val viewProps: ViewProps<ExpirationTimeItem> = ViewProps.Builder<ExpirationTimeItem>()
            .addString(R.attr.label) { us, labelText -> us.setLabel(labelText) }
            .build()
    }

    @BindView(R.id.expiration_time_label)
    lateinit var label: TextView

    @BindView(R.id.expiration_time_value)
    lateinit var expirationTime: TextView

    @BindView(R.id.expiration_time_loading)
    lateinit var loadingView: LoadingView

    override fun getLayoutResource(): Int {
        return R.layout.expiration_time_item
    }

    override fun setUp(context: Context, attrs: AttributeSet?) {
        super.setUp(context, attrs)
        viewProps.transfer(attrs, this)
    }

    fun setLabel(labelText: CharSequence) {
        label.text = labelText
    }

    fun setLoading(isLoading: Boolean) {
        loadingView.visibility = if (isLoading) View.VISIBLE else View.GONE
        expirationTime.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    fun setExpirationTime(expirationTimeText: CharSequence) {
        expirationTime.text = expirationTimeText
    }
}