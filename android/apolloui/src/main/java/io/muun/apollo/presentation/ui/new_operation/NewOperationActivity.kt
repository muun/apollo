package io.muun.apollo.presentation.ui.new_operation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.text.Html
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import butterknife.BindView
import butterknife.BindViews
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.domain.analytics.NewOperationOrigin
import io.muun.apollo.domain.libwallet.adapt
import io.muun.apollo.domain.libwallet.destinationPubKey
import io.muun.apollo.domain.libwallet.remainingMillis
import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.Contact
import io.muun.apollo.domain.model.OperationUri
import io.muun.apollo.domain.model.PaymentRequest
import io.muun.apollo.domain.model.SubmarineSwap
import io.muun.apollo.domain.model.SubmarineSwapReceiver
import io.muun.apollo.domain.utils.locale
import io.muun.apollo.presentation.ui.MuunCountdownTimer
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity
import io.muun.apollo.presentation.ui.base.di.PerActivity
import io.muun.apollo.presentation.ui.fragments.manual_fee.ManualFeeFragment
import io.muun.apollo.presentation.ui.fragments.new_op_error.NewOperationErrorFragment
import io.muun.apollo.presentation.ui.fragments.recommended_fee.RecommendedFeeFragment
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.presentation.ui.home.HomeActivity
import io.muun.apollo.presentation.ui.listener.SimpleTextWatcher
import io.muun.apollo.presentation.ui.new_operation.NewOperationView.Receiver
import io.muun.apollo.presentation.ui.utils.NewOperationInvoiceFormatter
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.UiUtils
import io.muun.apollo.presentation.ui.utils.getStyledString
import io.muun.apollo.presentation.ui.utils.setUserInteractionEnabled
import io.muun.apollo.presentation.ui.view.MuunAmountInput
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation
import io.muun.apollo.presentation.ui.view.MuunPill
import io.muun.apollo.presentation.ui.view.NoticeBanner
import io.muun.apollo.presentation.ui.view.RichText
import io.muun.apollo.presentation.ui.view.StatusMessage
import io.muun.apollo.presentation.ui.view.TextInputWithBackHandling
import io.muun.common.exception.MissingCaseError
import io.muun.common.utils.BitcoinUtils
import newop.EnterAmountState
import newop.EnterDescriptionState
import newop.PaymentIntent
import javax.money.MonetaryAmount

@PerActivity
class NewOperationActivity : SingleFragmentActivity<NewOperationPresenter>(), NewOperationView {

    companion object {

        private const val OPERATION_URI = "operation_uri"
        private const val ORIGIN = "origin"

        private const val INVOICE_EXPIRATION_WARNING_TIME_IN_SECONDS: Long = 60

        /**
         * Create an Intent to launch this Activity.
         */
        fun getIntent(context: Context, origin: NewOperationOrigin, uri: OperationUri): Intent {

            // NOTE: we use CLEAR_TOP to kill the previous instance of this Activity when launched,
            // so that only one of these exists at the same time (avoiding potentially awful
            // confusion if two different links are clicked). This only works as intended because no
            // other Activities will be on top of this one (we never startActivity here).
            return Intent(context, NewOperationActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(OPERATION_URI, uri.toString())
                .putExtra(ORIGIN, origin.name)
        }
    }

    // Components:
    @BindView(R.id.root_view)
    lateinit var root: ConstraintLayout

    @BindView(R.id.overlay_container)
    lateinit var overlayContainer: ViewGroup

    @BindView(R.id.new_operation_header)
    lateinit var muunHeader: MuunHeader

    @BindView(R.id.scrollable_layout)
    lateinit var scrollableLayout: View

    @BindView(R.id.new_operation_resolving)
    lateinit var resolvingSpinner: View

    @BindView(R.id.muun_amount_input)
    lateinit var amountInput: MuunAmountInput

    @BindView(R.id.use_all_funds)
    lateinit var useAllFundsView: TextView

    @BindView(R.id.muun_note_input)
    lateinit var descriptionInput: TextInputWithBackHandling

    @BindView(R.id.muun_next_step_button)
    lateinit var actionButton: MuunButton

    @BindView(R.id.new_operation_receiver)
    lateinit var receiver: MuunPill

    @BindView(R.id.target_address)
    lateinit var receiverAddress: TextView

    @BindView(R.id.selected_amount)
    lateinit var selectedAmount: TextView

    @BindView(R.id.amount_label)
    lateinit var amountLabel: TextView

    @BindView(R.id.separator_amount)
    lateinit var amountSeparator: View

    @BindView(R.id.notes_content)
    lateinit var descriptionContent: TextView

    @BindView(R.id.notes_label)
    lateinit var descriptionLabel: TextView

    @BindView(R.id.fee_label)
    lateinit var feeLabel: TextView

    @BindView(R.id.fee_amount)
    lateinit var feeAmount: TextView

    @BindView(R.id.total_amount)
    lateinit var totalAmount: TextView

    @BindView(R.id.total_label)
    lateinit var totalLabel: TextView

    @BindView(R.id.status_message)
    lateinit var statusMessage: StatusMessage

    @BindView(R.id.insufficient_funds_message)
    lateinit var insufficientFundsMessage: StatusMessage

    // Groups (to toggle visibility):
    @BindViews(R.id.muun_amount_input, R.id.use_all_funds)
    lateinit var amountEditableViews: Array<View>

    @BindViews(R.id.amount_label, R.id.selected_amount, R.id.separator_amount)
    lateinit var amountSelectedViews: Array<View>

    @BindViews(R.id.status_message, R.id.insufficient_funds_message)
    lateinit var statusMessageViews: Array<View>

    @BindViews(
        R.id.fee_label,
        R.id.fee_amount,
        R.id.total_label,
        R.id.total_amount,
        R.id.notes_label,
        R.id.notes_content
    )
    lateinit var noteEnteredViews: Array<View>

    // Lightning specific
    @BindView(R.id.invoice_expiration_countdown)
    lateinit var invoiceExpirationCountdown: TextView

    @BindView(R.id.one_conf_notice_banner)
    lateinit var noticeBanner: NoticeBanner

    @BindView(R.id.button_layout_anchor)
    lateinit var buttonLayoutAnchor: View

    // State:

    @State
    @JvmField
    var displayInAlternateCurrency: Boolean = false

    private var countdownTimer: MuunCountdownTimer? = null

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.activity_new_operation

    override fun getHeader(): MuunHeader =
        muunHeader

    override fun getFragmentsContainer(): Int =
        R.id.overlay_container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP == 0) {
            // HACK ALERT
            // This Activity was not launched with the CLEAR_TOP flag, because it started from
            // an <intent-filter> defined in the manifest. Flags cannot be specified for these
            // Intents (pff), and we really need CLEAR_TOP, so... well, this:
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finishActivity()
        }

        val newOpOrigin = getOrigin(intent)
        val uri = getValidIntentUri(intent)
        val operationUri = try {
            OperationUri.fromString(uri)
        } catch (e: Exception) {
            null
        }

        if (operationUri != null) {
            when {
                operationUri.isLn ->
                    presenter.startForInvoice(operationUri.lnInvoice.get(), newOpOrigin)

                operationUri.isLnUrl -> {
                    presenter.handleLnUrl(operationUri.lnUrl.get())
                    finishActivity()
                    return
                }

                else ->
                    presenter.startForBitcoinUri(uri, operationUri, newOpOrigin)
            }

        } else {
            showTextToast(getString(R.string.error_no_valid_payment_details_provided))
            finishActivity()
        }
    }

    override fun initializeUi() {
        super.initializeUi()

        header.attachToActivity(this)
        header.showTitle(R.string.title_new_operation)
        header.setNavigation(Navigation.BACK)

        useAllFundsView.setOnClickListener { presenter.confirmUseAllFunds() }
        useAllFundsView.isEnabled = false

        descriptionInput.addTextChangedListener(SimpleTextWatcher {
            actionButton.isEnabled = !TextUtils.isEmpty(it)
        })
        descriptionInput.setOnBackPressedListener { onBackPressed() }

        feeLabel.setOnClickListener { presenter.editFee() }

        selectedAmount.setOnClickListener { handleToggleCurrencyChange() }
        feeAmount.setOnClickListener { handleToggleCurrencyChange() }
        totalAmount.setOnClickListener { handleToggleCurrencyChange() }
    }

    override fun isPresenterPersistent(): Boolean {
        return true
    }

    override fun onResume() {
        super.onResume()
        if (amountInput.visibility == View.VISIBLE) {
            amountInput.requestFocusInput()
        }
    }

    override fun onStop() {
        super.onStop()
        if (countdownTimer != null) {
            countdownTimer!!.cancel()
            countdownTimer = null
        }
    }

    private fun getValidIntentUri(intent: Intent): String {
        return intent.getStringExtra(OPERATION_URI)    // from startActivity
            ?: intent.dataString                                            // deeplink
            ?: throw IllegalStateException("Invalid New Op Intent")         // should not happen
    }

    override fun setLoading(isLoading: Boolean) {
        actionButton.setLoading(isLoading)
    }

    override fun goToResolvingState() {
        root.layoutTransition = null
        showLoadingSpinner(true)

        // Avoid capturing focus and showing keyboard:
        amountInput.visibility = View.GONE
        descriptionInput.visibility = View.GONE
    }

    override fun goToEnterAmountState(state: EnterAmountState, receiver: Receiver) {

        updateReceiver(state.resolved.paymentIntent, receiver)

        val balance = BitcoinAmount.fromLibwallet(state.totalBalance)
        val balanceText = MoneyHelper.formatLongMonetaryAmount(
            balance.inInputCurrency,
            true,
            amountInput.bitcoinUnit,
            applicationContext.locale()
        )
        val newAmount = state.amount.inInputCurrency.adapt()

        amountInput.isEnabled = true
        amountInput.setAmountError(false) // Actually needed to set correct textColor in some cases
        if (amountInput.value.isZero || newAmount.isPositive) {
            // Ugly hack to work around state machine's current API short comings
            // TODO: change state machine changeCurrency API
            amountInput.value = newAmount
        }
        amountInput.setSecondaryAmount("${getString(R.string.available_balance)}: $balanceText")

        root.layoutTransition = null
        showLoadingSpinner(false)

        // Always request focus before changing views to GONE
        // otherwise you might cause the previous input to lose focus
        // which in turn causes the keyboard to hide.

        amountEditableViews.changeVisibility(View.VISIBLE)
        amountInput.requestFocusInput()

        amountSelectedViews.changeVisibility(View.GONE)
        noteEnteredViews.changeVisibility(View.GONE)
        descriptionInput.visibility = View.GONE

        actionButton.setText(R.string.confirm_amount)
        actionButton.isEnabled = !amountInput.isEmpty // needed when coming back to this step

        statusMessageViews.changeVisibility(View.GONE)

        val paymentContext = state.resolved.paymentContext
        amountInput.setExchangeRateProvider(paymentContext.buildExchangeRateProvider())
        amountInput.setOnChangeListener { old: MonetaryAmount, new: MonetaryAmount ->
            amountInput.setAmountError(false)
            actionButton.isEnabled = true
            presenter.updateAmount(old, new, state)
        }

        useAllFundsView.isEnabled = !balance.isZero

        actionButton.setOnClickListener {
            presenter.confirmAmount(amountInput.value, false)
        }
    }

    override fun setAmountInputError() {
        amountInput.setAmountError(true)
        actionButton.isEnabled = false
    }

    override fun goToEnterDescriptionState(state: EnterDescriptionState, receiver: Receiver) {

        updateReceiver(state.resolved.paymentIntent, receiver)

        show1ConfNotice(state.validated.swapInfo?.isOneConf ?: false)

        val isValid = !state.validated.feeNeedsChange
        setAmount(selectedAmount, DisplayAmount(state.amountInfo.amount, getBitcoinUnit(), isValid))

        descriptionInput.setText(state.note)

        root.layoutTransition = null
        showLoadingSpinner(false)

        val isDescriptionTextVisible = descriptionContent.visibility == View.VISIBLE
        val originX = amountInput.x
        val originY = amountInput.y - amountInput.height
        val descriptionParentHeight = (descriptionInput.parent as ViewGroup).height.toFloat()

        amountSelectedViews.changeVisibility(View.VISIBLE)
        descriptionInput.visibility = View.VISIBLE
        descriptionInput.requestFocus()

        // The amount input needs to be invisible in order to still be able to calculate the
        // adjustable text size.
        amountEditableViews.changeVisibility(View.INVISIBLE)
        noteEnteredViews.changeVisibility(View.GONE)

        // We only animate forward for now, since we prioritize efforts on the most common flow.
        // TODO: add the backward animation.
        if (!isDescriptionTextVisible) {
            selectedAmount.doAnimateTransition(revealFrom(-originX, originY))
            amountLabel.doAnimateTransition(revealFrom(originX, originY))
            amountSeparator.doAnimateTransition(revealFrom(0f, originY))
            descriptionInput.doAnimateTransition(revealFrom(0f, descriptionParentHeight))
        }

        descriptionLabel.visibility = View.INVISIBLE
        descriptionContent.visibility = View.INVISIBLE

        actionButton.setText(R.string.confirm_note)

        val text = descriptionInput.text
        actionButton.isEnabled = text != null && !TextUtils.isEmpty(text.toString())
        actionButton.setOnClickListener {
            presenter.confirmDescription(descriptionInput.text.toString())
        }

        statusMessageViews.changeVisibility(View.GONE)
    }

    override fun goToConfirmState(state: ConfirmStateViewModel, receiver: Receiver) {

        updateReceiver(state.paymentIntent, receiver)

        show1ConfNotice(state.validated.swapInfo?.isOneConf ?: false)

        val isValid = !state.validated.feeNeedsChange
        setAmount(selectedAmount, DisplayAmount(state.amountInfo.amount, getBitcoinUnit(), isValid))
        setAmount(feeAmount, DisplayAmount(state.validated.fee, getBitcoinUnit(), isValid))
        setAmount(totalAmount, DisplayAmount(state.validated.total, getBitcoinUnit(), isValid))

        descriptionContent.text = state.note

        UiUtils.lastResortHideKeyboard(this)
        showLoadingSpinner(false)

        amountSelectedViews.changeVisibility(View.VISIBLE)

        descriptionInput.visibility = View.GONE
        amountEditableViews.changeVisibility(View.GONE)

        // We only want to animate the effect that some appearing or disappearing view
        // has in other views, not the changes in the changed view itself.
        root.disableTransitions()

        descriptionLabel.visibility = View.VISIBLE
        descriptionContent.visibility = View.VISIBLE

        UiUtils.fadeIn(feeLabel)
        UiUtils.fadeIn(feeAmount)

        if (receiver.swap != null) {
            // Fee is not editable for submarine swaps:
            feeLabel.setCompoundDrawables(null, null, null, null)
            feeLabel.isClickable = false
        }

        UiUtils.fadeIn(totalLabel)
        UiUtils.fadeIn(totalAmount)

        actionButton.setText(R.string.new_operation_confirm)
        actionButton.setOnClickListener {

            if (receiver.swap == null) {
                presenter.confirmOperation()
            } else {
                presenter.confirmSwapOperation()
            }
        }
        actionButton.isEnabled = isValid

        statusMessageViews.changeVisibility(View.GONE)
        if (state.amountInfo.takeFeeFromAmount) {
            statusMessage.setWarning(
                R.string.use_all_funds_warning_message,
                R.string.use_all_funds_warning_desc,
                false,
                ':'
            )
        }

        if (state.validated.feeNeedsChange) {
            insufficientFundsMessage.setWarning(
                getString(R.string.new_op_insufficient_funds_warn_message),
                getStyledString(R.string.new_op_insufficient_funds_warn_desc),
                false,
                ':'
            )

            insufficientFundsMessage.setOnClickListener { presenter.editFee() }
        }
    }

    override fun goToEditFeeState() {
        showOverlayFragment(RecommendedFeeFragment(), true)
    }

    override fun goToEditFeeManually() {
        replaceFragment(ManualFeeFragment(), true)
    }

    override fun goToConfirmedFee() {
        // Go back to confirm Step
        clearFragmentBackStack()
        hideFragmentOverlay()
    }

    override fun showAbortDialog() {
        MuunDialog.Builder()
            .title(R.string.new_operation_abort_alert_title)
            .message(R.string.new_operation_abort_alert_body)
            .positiveButton(R.string.abort) { presenter.finishAndGoHome() }
            .negativeButton(R.string.cancel, null)
            .onDismiss { presenter.cancelAbort() }
            .build()
            .let(this::showDialog)
    }

    override fun finishAndGoHome() {
        if (isTaskRoot) {
            startActivity(HomeActivity.getStartActivityIntent(this))
        }
        finishActivity()
    }

    override fun onBackPressed() {

        if (shouldIgnoreBackAndExit() || handleBackByOverlayFragment()) {
            return
        }

        presenter.goBack()
    }

    override fun setInitialBitcoinUnit(bitcoinUnit: BitcoinUnit) {
        amountInput.setInitialBitcoinUnit(bitcoinUnit) // set initial bitcoin unit to user's pref
    }

    override fun showErrorScreen(type: NewOperationErrorType) {
        showOverlayFragment(NewOperationErrorFragment.create(type), false)
        scrollableLayout.setUserInteractionEnabled(false)
    }

    // PRIVATE STUFF

    private fun getOrigin(intent: Intent): NewOperationOrigin {
        val originExtra = intent.getStringExtra(ORIGIN)
        return if (originExtra != null) {
            NewOperationOrigin.valueOf(originExtra)
        } else {
            NewOperationOrigin.EXTERNAL_LINK // we landed from a URL click
        }
    }

    private fun handleToggleCurrencyChange() {
        displayInAlternateCurrency = !displayInAlternateCurrency
        toggleCurrencyChange(selectedAmount)
        toggleCurrencyChange(feeAmount)
        toggleCurrencyChange(totalAmount)
    }

    private fun toggleCurrencyChange(view: TextView) {
        val displayAmt: DisplayAmount? = view.tag as? DisplayAmount
        if (displayAmt != null) {    // If view isn't being show yet, we do nothing

            val amountToDisplay = if (displayInAlternateCurrency) {

                val (_, currencyCode) = view.text.toString().split(" ")

                // Show BTC if current display is in FIAT, and the other way around.
                if (currencyCode == "BTC" || currencyCode == "SAT") {
                    displayAmt.amount.inPrimaryCurrency
                } else {
                    BitcoinUtils.satoshisToBitcoins(displayAmt.amount.inSatoshis)
                }
            } else {
                // Show the amount as it originally was.
                displayAmt.amount.inInputCurrency
            }

            view.text = toRichText(amountToDisplay, displayAmt.bitcoinUnit, displayAmt.isValid)
        }
    }

    private fun showLoadingSpinner(showLoading: Boolean) {
        resolvingSpinner.visibility = if (showLoading) View.VISIBLE else View.GONE
        root.visibility = if (showLoading) View.GONE else View.VISIBLE
    }

    private fun updateReceiver(paymentIntent: PaymentIntent, receiver: Receiver) {
        val uri = paymentIntent.uri
        when (val paymentRequestType = paymentIntent.getPaymentType()) {
            PaymentRequest.Type.TO_ADDRESS -> setReceiver(uri.address)
            PaymentRequest.Type.TO_CONTACT -> setReceiver(receiver.contact!!)
            PaymentRequest.Type.TO_LN_INVOICE -> setReceiver(uri.invoice, receiver.swap!!)
            else -> throw MissingCaseError(paymentRequestType)
        }
    }

    private fun setReceiver(address: String) {
        receiverAddress.text = UiUtils.ellipsize(address)
        receiverAddress.visibility = View.VISIBLE
        receiver.visibility = View.GONE
        receiverAddress.setOnClickListener {
            val dialog = TitleAndDescriptionDrawer()
            dialog.setDescription(address)
            dialog.setTitle(R.string.new_operation_dialog_full_address)
            showDrawerDialog(dialog)
            presenter.reportShowDestinationInfo()
        }
    }

    private fun setReceiver(contact: Contact) {
        receiver.setText(contact.publicProfile.fullName)
        receiver.setPictureUri(contact.publicProfile.profilePictureUrl)
        receiver.setPictureVisible(true)
        receiverAddress.visibility = View.GONE
    }

    private fun setReceiver(invoice: libwallet.Invoice, swap: SubmarineSwap) {

        // 1. set receiver/destination data
        if (!TextUtils.isEmpty(swap.receiver.alias)) {
            receiverAddress.text = swap.receiver.alias
        } else {
            receiverAddress.text = UiUtils.ellipsize(invoice.destinationPubKey())
        }

        receiverAddress.setOnClickListener {
            val dialog = TitleAndDescriptionDrawer()
            dialog.setDescription(getFormattedDestinationData(swap.receiver))
            dialog.setTitle(R.string.new_operation_receiving_node_data)
            showDrawerDialog(dialog)
            presenter.reportShowDestinationInfo()
        }
        receiverAddress.visibility = View.VISIBLE
        receiver.visibility = View.GONE

        // 2. Start invoice expiration countdown
        countdownTimer = buildCountDownTimer(invoice.remainingMillis())
        countdownTimer?.start()
        buttonLayoutAnchor.visibility = View.VISIBLE
        invoiceExpirationCountdown.visibility = View.VISIBLE

        // We need to post since buttonLayoutAnchor is not measured yet.
        Handler().post {
            UiUtils.setMarginBottom(scrollableLayout, buttonLayoutAnchor.height)
        }
    }


    private fun getFormattedDestinationData(receiver: SubmarineSwapReceiver): CharSequence {
        val publicKeyText: CharSequence = Html.fromHtml(
            getString(
                R.string.new_operation_receiving_node_public_key,
                receiver.publicKey
            ).replace(" ", "&nbsp;")
        )

        var networkAddressText: CharSequence = ""
        if (!TextUtils.isEmpty(receiver.displayNetworkAddress)) {
            networkAddressText = Html.fromHtml(
                getString(
                    R.string.new_operation_receiving_node_network_address,
                    receiver.displayNetworkAddress
                ).replace(" ", "&nbsp;")
            )
        }

        val linkText = getString(R.string.see_in_node_explorer).toUpperCase()

        return TextUtils.concat(
            publicKeyText,
            "\n\n",
            networkAddressText,
            "\n\n",
            linkBuilder.lightningNodeLink(receiver, linkText)
        )
    }

    private fun show1ConfNotice(show1ConfNotice: Boolean) {
        if (show1ConfNotice) {
            noticeBanner.setText(
                StyledStringRes(this, R.string.new_op_1_conf_notice_banner)
                    .toCharSequence()
            )
            noticeBanner.setOnClickListener {
                val dialog = TitleAndDescriptionDrawer()
                dialog.setTitle(R.string.new_op_1_conf_notice_title)
                dialog.setDescription(getString(R.string.new_op_1_conf_notice_desc))
                showDrawerDialog(dialog)
            }
            noticeBanner.visibility = View.VISIBLE
        }
    }

    private fun setAmount(view: TextView, displayAmount: DisplayAmount) {
        val amount = displayAmount.amount.inInputCurrency
        view.text = toRichText(amount, displayAmount.bitcoinUnit, displayAmount.isValid)
        view.tag = displayAmount
    }

    private fun showOverlayFragment(fragment: Fragment, canGoBack: Boolean) {
        UiUtils.lastResortHideKeyboard(this)
        if (canGoBack) {
            replaceFragment(fragment, true)
        } else {
            // Keeping long standing (maybe legacy) behaviour of using commitNow for fragment txs
            // that CAN't go back (namely error fragment). We used to have glitches during the tx
            replaceFragmentNow(fragment)
        }
        overlayContainer.visibility = View.VISIBLE
    }

    /**
     * We are handling back for overlay fragments here, in a centralized (yes, the word which shall
     * not be named) manner, because its easier and more compact than having each fragment intercept
     * and handle its back. Also, most of the times we just simply want to
     * {@code fragment.popBackStack()}.
     */
    private fun handleBackByOverlayFragment(): Boolean {
        val overlay: Fragment? = supportFragmentManager.findFragmentById(R.id.overlay_container)
        if (overlay != null) {
            super.onBackPressed()   // Aka fragment.popBackStack()

            // If we popped all singleFragment, let's hide fragment overlay and reset MuunHeader
            if (supportFragmentManager.findFragmentById(R.id.overlay_container) == null) {
                presenter.goBack()
                hideFragmentOverlay()
            }
            return true
        }
        return false
    }

    private fun hideFragmentOverlay() {
        if (OperationUri.fromString(getValidIntentUri(intent)).isLn) {
            header.showTitle(R.string.new_operation_swap_title)
        } else {
            header.showTitle(R.string.title_new_operation)
        }

        header.setNavigation(Navigation.BACK)
        overlayContainer.visibility = View.GONE
    }

    private fun buildCountDownTimer(remainingMillis: Long): MuunCountdownTimer {
        return object : MuunCountdownTimer(remainingMillis) {


            override fun onTickSeconds(remainingSeconds: Long) {
                val context = this@NewOperationActivity
                val timeText = NewOperationInvoiceFormatter(context).formatSeconds(remainingSeconds)

                val prefixText = getString(R.string.new_operation_invoice_exp_prefix)
                val text = TextUtils.concat(prefixText, " ", timeText)
                val richText = RichText(text)

                if (remainingSeconds < INVOICE_EXPIRATION_WARNING_TIME_IN_SECONDS) {
                    richText.setForegroundColor(ContextCompat.getColor(context, R.color.red))
                }

                invoiceExpirationCountdown.text = text
            }

            override fun onFinish() {
                presenter.handleNewOpError(NewOperationErrorType.INVOICE_EXPIRED)
            }
        }
    }

    // Part of our (ugly) hack to allow SATs as an input currency option
    private fun getBitcoinUnit(): BitcoinUnit =
        amountInput.bitcoinUnit
}