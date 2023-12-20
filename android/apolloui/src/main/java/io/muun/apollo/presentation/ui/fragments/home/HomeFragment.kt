package io.muun.apollo.presentation.ui.fragments.home

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import butterknife.BindView
import com.airbnb.lottie.LottieAnimationView
import com.skydoves.balloon.ArrowConstraints
import com.skydoves.balloon.ArrowOrientation
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.OnBalloonClickListener
import com.skydoves.balloon.createBalloon
import io.muun.apollo.R
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.Operation
import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.domain.selector.UtxoSetStateSelector
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.getDrawable
import io.muun.apollo.presentation.ui.view.BalanceView
import io.muun.apollo.presentation.ui.view.BlockClock
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunHomeCard
import io.muun.apollo.presentation.ui.view.NewOpBadge
import io.muun.common.utils.BitcoinUtils
import org.threeten.bp.ZonedDateTime
import kotlin.math.abs

class HomeFragment : SingleFragment<HomeFragmentPresenter>(), HomeFragmentView {

    companion object {
        private const val NEW_OP_ANIMATION_WINDOW = 15L   // In Seconds
    }

    @BindView(R.id.chevron)
    lateinit var chevron: LottieAnimationView

    @BindView(R.id.chevron_container)
    lateinit var chevronContainer: View

    @BindView(R.id.new_op_badge)
    lateinit var newOpBadge: NewOpBadge

    @BindView(R.id.home_balance_view)
    lateinit var balanceView: BalanceView

    @BindView(R.id.home_receive_button)
    lateinit var receiveButton: MuunButton

    @BindView(R.id.home_send_button)
    lateinit var sendButton: MuunButton

    @BindView(R.id.home_taproot_card)
    lateinit var taprootCard: MuunHomeCard

    @BindView(R.id.home_security_center_card)
    lateinit var securityCenterCard: MuunHomeCard

    @BindView(R.id.home_high_fees_card)
    lateinit var highFeesCard: MuunHomeCard

    @BindView(R.id.home_block_clock)
    lateinit var blockClock: BlockClock

    var balloon: Balloon? = null

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource() =
        R.layout.fragment_home

    override fun initializeUi(view: View) {
        setUpGestureDetectors()
        initializeCards()
        setUpClickListeners()
    }

    override fun setUpHeader() {
        parentActivity.header.apply {
            visibility = View.VISIBLE
            showTitle(R.string.app_name)
            setElevated(false)
        }
    }

    private fun setUpGestureDetectors() {
        // The detector detect the fling gesture for the chevron
        // The first detects the fling in the horizontal area around the chevron.
        // The second detects the fling in the chevron itself.
        // Having both allows us to retain the default on click (and thus animation) for the chevron

        val containerDetector = GestureDetector(
            requireContext(), GestureListener(chevron, requireContext(), true)
        )
        chevronContainer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                chevronContainer.performClick()
            } else {
                containerDetector.onTouchEvent(event)
            }
        }

        val chevronDetector = GestureDetector(
            requireContext(), GestureListener(chevron, requireContext(), false)
        )
        chevron.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                chevronContainer.performClick()
            } else {
                chevronDetector.onTouchEvent(event)
            }
        }
    }

    private fun initializeCards() {
        taprootCard.let {
            it.icon = getDrawable(R.drawable.ic_star)
            it.setIconTint(R.color.blue)
            it.body = StyledStringRes(requireContext(), R.string.taproot_card_body)
                .toCharSequence()

            it.setOnClickListener {
                presenter.navigateToTaprootSetup()
            }
        }

        securityCenterCard.let {
            it.icon = getDrawable(R.drawable.ic_lock)
            it.setIconTint(R.color.blue)
            it.body = StyledStringRes(requireContext(), R.string.home_security_center_card_body)
                .toCharSequence()

            it.setOnClickListener {
                presenter.navigateToSecurityCenter()
            }
        }

        highFeesCard.let {
            it.icon = getDrawable(R.drawable.ic_baseline_warning_24px)
            it.body = StyledStringRes(requireContext(), R.string.home_high_fees_card_body)
                .toCharSequence()
        }
    }

    private fun setUpClickListeners() {
        balanceView.setOnClickListener { onBalanceClick() }
        receiveButton.setOnClickListener { presenter.navigateToReceiveScreen() }
        sendButton.setOnClickListener { presenter.navigateToSendScreen() }
        chevron.setOnClickListener { presenter.navigateToOperations() }
        blockClock.setOnClickListener {
            presenter.navigateToClockDetail()
        }
    }

    class GestureListener(private val chevron: View, context: Context, private val down: Boolean) :
        GestureDetector.SimpleOnGestureListener() {

        private val minVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
        private val minTravelDistance = ViewConfiguration.get(context).scaledTouchSlop

        override fun onFling(
            e1: MotionEvent,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {

            // Future reader: for MotionEvent we want rawX/Y, other coordinates suck (BIG TIME)
            // See: https://stackoverflow.com/q/1410885/901465
            if (abs(velocityY) > minVelocity && e1.rawY - e2.rawY > minTravelDistance) {
                chevron.performClick()
                return true
            }

            return super.onFling(e1, e2, velocityX, velocityY)
        }

        override fun onDown(e: MotionEvent): Boolean {
            // onDown determines whether the detector intercepts the event or it keeps looking
            // for someone to handle it. If true, all events are handled by the detector.
            return down
        }

    }

    override fun onPause() {
        super.onPause()

        chevron.removeAllLottieOnCompositionLoadedListener()
        balloon?.dismiss()
        balloon = null
        lockManager.setUnlockListener(null)
    }

    override fun showTooltip() {
        if (balloon != null) {
            return
        }

        if (lockManager.isLockSet) {
            lockManager.setUnlockListener {
                showTooltip()
            }
            return
        }

        this.balloon = createBalloon(parentActivity) {
            setLayout(R.layout.view_new_home_tooltip)
            setArrowSize(15)
            setArrowOrientation(ArrowOrientation.BOTTOM)
            setArrowConstraints(ArrowConstraints.ALIGN_ANCHOR)
            setArrowPosition(0.5f)
            setWidthRatio(1.0f)
            setMarginRight(53)
            setMarginLeft(53)
            setCornerRadius(4f)
            setBackgroundColorResource(R.color.new_home_tooltip)
            setOnBalloonClickListener(OnBalloonClickListener { presenter.navigateToOperations() })
            setBalloonAnimation(BalloonAnimation.NONE)
            setDismissWhenTouchOutside(false)
            setLifecycleOwner(this@HomeFragment)
        }

        chevron.addLottieOnCompositionLoadedListener {
            // Turns out the balloon library has a few known limitations regarding multi line text
            // layout. If we set max width, they seem to solve themselves. But, we can't set a fixed
            // value in the xml so we do this lovely thing here.
            // https://github.com/skydoves/Balloon/issues/55
            val textView =
                balloon!!.getContentView().findViewById<TextView>(R.id.new_home_tooltip_text)
            textView.maxWidth = requireView().width - 56 * 2

            balloon!!.showAlignTop(chevron)
        }
    }

    override fun setState(homeState: HomeFragmentPresenter.HomeState) {
        balanceView.setBalance(homeState)
        setChevronAnimation(homeState.utxoSetState)

        // Due to (complex) business logic reasons, only 1 of these cards is currently displayed
        var displayedMuunHomeCard: MuunHomeCard? = null
        if (!homeState.user.isRecoverable) {
            displayedMuunHomeCard = securityCenterCard

        } else when (homeState.taprootFeatureStatus) {
            UserActivatedFeatureStatus.OFF -> {} // Do nothing
            UserActivatedFeatureStatus.CAN_PREACTIVATE -> displayedMuunHomeCard = taprootCard
            UserActivatedFeatureStatus.CAN_ACTIVATE -> displayedMuunHomeCard = taprootCard
            UserActivatedFeatureStatus.PREACTIVATED -> blockClock.visibility = View.VISIBLE
            UserActivatedFeatureStatus.SCHEDULED_ACTIVATION -> {} // Do nothing
            UserActivatedFeatureStatus.ACTIVE -> {} // Do nothing
        }

        blockClock.value = homeState.blocksToTaproot

        if (displayedMuunHomeCard != null) {
            displayedMuunHomeCard.visibility = View.VISIBLE
            if (displayedMuunHomeCard == taprootCard) {
                securityCenterCard.visibility = View.GONE
                highFeesCard.visibility = View.GONE
            } else {
                taprootCard.visibility = View.GONE
                highFeesCard.visibility = View.GONE
            }
        } else {
            taprootCard.visibility = View.GONE
            securityCenterCard.visibility = View.GONE

            if (homeState.highFees) {
                highFeesCard.visibility = View.VISIBLE
            }
        }
    }

    override fun setNewOp(newOp: Operation, bitcoinUnit: BitcoinUnit) {

        var amountInBtc = BitcoinUtils.satoshisToBitcoins(newOp.amount.inSatoshis)

        val animRes: Int
        when {
            newOp.isOutgoing -> {
                amountInBtc = amountInBtc.negate()
                animRes = R.anim.new_op_badge_outgoing

            }
            newOp.isCyclical -> {
                animRes = R.anim.new_op_badge_outgoing
                amountInBtc = BitcoinUtils.satoshisToBitcoins(newOp.fee.inSatoshis).negate()

            }
            else -> {
                animRes = R.anim.new_op_badge_incoming
            }
        }

        newOpBadge.setAmount(amountInBtc, bitcoinUnit)

        // Only show animation for recently received or sent ops
        if (newOp.creationDate.isAfter(ZonedDateTime.now().minusSeconds(NEW_OP_ANIMATION_WINDOW))) {
            newOpBadge.startAnimation(animRes)
        }
    }

    private fun onBalanceClick() {
        if (balanceView.isFullyInitialized()) {
            val hidden = balanceView.toggleVisibility()
            presenter.setBalanceHidden(hidden)
        }
    }

    private fun setChevronAnimation(utxoSetState: UtxoSetStateSelector.UtxoSetState) {
        val animationRes = when (utxoSetState) {
            UtxoSetStateSelector.UtxoSetState.PENDING -> R.raw.chevron_pending
            UtxoSetStateSelector.UtxoSetState.RBF -> R.raw.chevron_rbf
            UtxoSetStateSelector.UtxoSetState.CONFIRMED -> R.raw.chevron_regular
        }

        chevron.setAnimation(animationRes)
        chevron.playAnimation()
    }
}