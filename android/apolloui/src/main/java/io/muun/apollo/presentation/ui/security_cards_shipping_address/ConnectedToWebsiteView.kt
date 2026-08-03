package io.muun.apollo.presentation.ui.security_cards_shipping_address

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import io.muun.apollo.R
import io.muun.apollo.databinding.ViewConnectedToWebsiteBinding

class ConnectedToWebsiteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewConnectedToWebsiteBinding

    init {
        LayoutInflater.from(context)
            .inflate(R.layout.view_connected_to_website, this)
        binding = ViewConnectedToWebsiteBinding.bind(this)
    }

    fun setWebsiteUrl(url: String) {
        binding.textViewTitle.text =
            context.getString(R.string.security_cards_connected_to_website_title, url)
    }

    fun setOnCloseClick(listener: OnClickListener) {
        binding.imageViewClose.setOnClickListener(listener)
    }
}
