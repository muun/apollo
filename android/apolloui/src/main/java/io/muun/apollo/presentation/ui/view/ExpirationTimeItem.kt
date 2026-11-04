package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import io.muun.apollo.R
import io.muun.apollo.databinding.ExpirationTimeItemBinding

class ExpirationTimeItem @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0):
    MuunView<ExpirationTimeItemBinding>(c, a, s) {

    companion object {

        val viewProps: ViewProps<ExpirationTimeItem> = ViewProps.Builder<ExpirationTimeItem>()
            .addString(R.attr.label) { us, labelText -> us.setLabel(labelText) }
            .build()
    }

    private val label get() = binding.expirationTimeLabel
    private val expirationTime get() = binding.expirationTimeValue
    private val loadingView get() = binding.expirationTimeLoading

    override fun bindingInflater(): BindingInflater<ExpirationTimeItemBinding> {
        return ExpirationTimeItemBinding::inflate
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