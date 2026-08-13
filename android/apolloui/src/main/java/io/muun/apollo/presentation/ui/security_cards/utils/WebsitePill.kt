package io.muun.apollo.presentation.ui.security_cards.utils

import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.view.MuunHeader

fun MuunHeader.addWebsitePill(
    siteUrl: String,
    id: Int = View.generateViewId(),
    onClick: View.OnClickListener? = null,
) {
    val chip = Chip(context).apply {
        this.id = id
        text = siteUrl
        setChipIconResource(R.drawable.ic_language)
        chipIconTint = ContextCompat.getColorStateList(
            context, R.color.provider_pill_text
        )
        isChipIconVisible = true
        chipIconSize = resources.getDimension(
            R.dimen.security_cards_checkout_website_pill_icon_size
        )
        chipMinHeight = resources.getDimension(
            R.dimen.security_cards_checkout_website_pill_min_height
        )
        chipStrokeColor = ContextCompat.getColorStateList(
            context, R.color.provider_pill_border
        )
        chipStrokeWidth = resources.getDimension(
            R.dimen.security_cards_checkout_website_pill_stroke_width
        )
        chipBackgroundColor = ContextCompat.getColorStateList(
            context, android.R.color.transparent
        )
        setTextColor(
            ContextCompat.getColor(context, R.color.provider_pill_text)
        )
        chipStartPadding = resources.getDimension(
            R.dimen.security_cards_checkout_website_pill_padding_start
        )
        chipEndPadding = resources.getDimension(
            R.dimen.security_cards_checkout_website_pill_padding_end
        )
        textSize = 16f
        isClickable = onClick != null
        isFocusable = onClick != null
        onClick?.let(::setOnClickListener)
    }

    val layoutParams = Toolbar.LayoutParams(
        Toolbar.LayoutParams.WRAP_CONTENT,
        Toolbar.LayoutParams.WRAP_CONTENT,
    ).apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        marginEnd = context.resources.getDimensionPixelSize(
            R.dimen.security_cards_checkout_website_pill_margin_end
        )
    }
    toolbar.addView(chip, layoutParams)
}
