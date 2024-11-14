package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.annotation.AnimRes
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.ViewNewOpBadgeBinding
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.presentation.ui.helper.BitcoinHelper
import io.muun.apollo.presentation.ui.helper.isBtc
import io.muun.apollo.presentation.ui.utils.locale
import io.muun.apollo.presentation.ui.utils.setOnEndListener
import io.muun.common.utils.BitcoinUtils
import io.muun.common.utils.Preconditions
import javax.money.MonetaryAmount

class NewOpBadge @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    style: Int = 0,
) : MuunView(context, attrs, style) {

    private val binding: ViewNewOpBadgeBinding
        get() = _binding as ViewNewOpBadgeBinding

    // Components:

    private val text: TextView
        get() = binding.badgeText

    // Resources:

    private val outgoingTxBkg: Drawable?
        get() = ContextCompat.getDrawable(context, R.drawable.new_op_badge_blue_bkg)

    private val outgoingTxColor: Int
        get() = ContextCompat.getColor(context, R.color.blue)

    private val incomingTxBkg: Drawable?
        get() = ContextCompat.getDrawable(context, R.drawable.new_op_badge_green_bkg)

    private val incomingTxColor: Int
        get() = ContextCompat.getColor(context, R.color.green)

    override val layoutResource: Int
        get() = R.layout.view_new_op_badge

    override fun viewBinder(): ((View) -> ViewBinding) {
        return ViewNewOpBadgeBinding::bind
    }

    fun setAmount(amountInBtc: MonetaryAmount, bitcoinUnit: BitcoinUnit) {
        Preconditions.checkArgument(!amountInBtc.isZero)
        Preconditions.checkArgument(amountInBtc.isBtc())

        val amountInSats = BitcoinUtils.bitcoinsToSatoshis(amountInBtc)
        var content = BitcoinHelper.formatFlexBitcoinAmount(
            amountInSats, true, bitcoinUnit, locale()
        )

        if (amountInBtc.isPositive) {
            background = incomingTxBkg
            text.setTextColor(incomingTxColor)
            content = "+$content"

        } else {
            background = outgoingTxBkg
            text.setTextColor(outgoingTxColor)
        }

        text.text = content
    }

    fun startAnimation(@AnimRes animRes: Int) {

        visibility = View.VISIBLE

        val animation: Animation = AnimationUtils.loadAnimation(context, animRes)
        animation.setOnEndListener {
            visibility = View.GONE
        }

        startAnimation(animation)
    }
}