package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.annotation.AnimRes
import butterknife.BindColor
import butterknife.BindDrawable
import butterknife.BindView
import io.muun.apollo.R
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
    style: Int = 0
) : MuunView(context, attrs, style) {

    // Components:
    @BindView(R.id.badge_text)
    lateinit var text: TextView

    // Resources:

    @BindDrawable(R.drawable.new_op_badge_blue_bkg)
    lateinit var outgoingTxBkg: Drawable

    @BindColor(R.color.blue)
    @JvmField
    internal var outgoingTxColor: Int = 0

    @BindDrawable(R.drawable.new_op_badge_green_bkg)
    lateinit var incomingTxBkg: Drawable

    @BindColor(R.color.green)
    @JvmField
    internal var incomingTxColor: Int = 0

    override val layoutResource: Int
        get() = R.layout.view_new_op_badge

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