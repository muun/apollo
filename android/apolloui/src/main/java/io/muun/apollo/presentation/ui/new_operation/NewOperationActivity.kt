package io.muun.apollo.presentation.ui.new_operation

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.text.Html
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.IntentSanitizer
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.ActivityNewOperationBinding
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_MANUALLY_ENTER_FEE
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_NEW_OP_AMOUNT
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_NEW_OP_CONFIRMATION
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_NEW_OP_DESCRIPTION
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_SELECT_FEE
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
import io.muun.apollo.presentation.ui.MuunCountdownTimer
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity
import io.muun.apollo.presentation.ui.base.di.PerActivity
import io.muun.apollo.presentation.ui.fragments.manual_fee.ManualFeeFragment
import io.muun.apollo.presentation.ui.fragments.new_op_error.NewOperationErrorFragment
import io.muun.apollo.presentation.ui.fragments.recommended_fee.RecommendedFeeFragment
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
import newop.EnterAmountState
import newop.EnterDescriptionState
import newop.PaymentIntent
import timber.log.Timber
import javax.inject.Inject
import javax.money.MonetaryAmount

@PerActivity
class NewOperationActivity : SingleFragmentActivity<NewOperationPresenter>(),
    NewOperationView,
    MuunCountdownTimer.CountDownTimerListener {

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

    @Inject
    lateinit var viewModel: NewOperationViewModel

    private val nfcReaderLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            presenter.handleSecurityCard2faResult(result)
        }

    // Components:
    private val root: ConstraintLayout
        get() = binding.rootView

    private val overlayContainer: ViewGroup
        get() = binding.overlayContainer

    private val muunHeader: MuunHeader
        get() = binding.newOperationHeader

    private val scrollableLayout: View
        get() = binding.scrollableLayout

    private val resolvingSpinner: View
        get() = binding.newOperationResolving

    private val amountInput: MuunAmountInput
        get() = binding.muunAmountInput

    private val useAllFundsView: TextView
        get() = binding.useAllFunds

    private val descriptionInput: TextInputWithBackHandling
        get() = binding.muunNoteInput

    private val actionButton: MuunButton
        get() = binding.muunNextStepButton

    private val receiver: MuunPill
        get() = binding.newOperationReceiver

    private val receiverAddress: TextView
        get() = binding.targetAddress

    private val selectedAmount: TextView
        get() = binding.selectedAmount

    private val amountLabel: TextView
        get() = binding.amountLabel

    private val amountSeparator: View
        get() = binding.separatorAmount

    private val descriptionContent: TextView
        get() = binding.notesContent

    private val descriptionLabel: TextView
        get() = binding.notesLabel

    private val feeLabel: TextView
        get() = binding.feeLabel

    private val feeAmount: TextView
        get() = binding.feeAmount

    private val totalAmount: TextView
        get() = binding.totalAmount

    private val totalLabel: TextView
        get() = binding.totalLabel
    private val statusMessage: StatusMessage
        get() = binding.statusMessage

    private val insufficientFundsMessage: StatusMessage
        get() = binding.insufficientFundsMessage

    // Groups (to toggle visibility):
    private val amountEditableViews: Array<View>
        get() = arrayOf(binding.muunAmountInput, binding.useAllFunds)

    private val amountSelectedViews: Array<View>
        get() = arrayOf(binding.amountLabel, binding.selectedAmount, binding.separatorAmount)

    private val statusMessageViews: Array<View>
        get() = arrayOf(binding.statusMessage, binding.insufficientFundsMessage)

    private val noteEnteredViews: Array<View>
        get() = arrayOf(
            binding.feeLabel,
            binding.feeAmount,
            binding.totalLabel,
            binding.totalAmount,
            binding.notesLabel,
            binding.notesContent,
        )

    // Lightning specific
    private val invoiceExpirationCountdown: TextView
        get() = binding.invoiceExpirationCountdown

    private val noticeBanner: NoticeBanner
        get() = binding.oneConfNoticeBanner

    private val buttonLayoutAnchor: View
        get() = binding.buttonLayoutAnchor

    private var countdownTimer: MuunCountdownTimer? = null

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.activity_new_operation

    override fun bindingInflater(): (LayoutInflater) -> ViewBinding {
        return ActivityNewOperationBinding::inflate
    }

    override fun getBinding(): ActivityNewOperationBinding {
        return super.getBinding() as ActivityNewOperationBinding
    }

    override fun getHeader(): MuunHeader =
        muunHeader

    override fun getFragmentsContainer(): Int =
        R.id.overlay_container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: add timber.i() for intent.getAction() and intent.getData().getScheme() iff not null

        if (intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP == 0) {
            // HACK ALERT
            // This Activity was not launched with the CLEAR_TOP flag, because it started from
            // an <intent-filter> defined in the manifest. Flags cannot be specified for these
            // Intents (pff), and we really need CLEAR_TOP, so... well, this:
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

            // We log penalties but don't throw and exception for it because it could happen that
            // there are non supported args but the uri can still be usable
            val penalties = mutableListOf<String>()

            // Sanitize the intent before restarting to prevent UnsafeIntentLaunch
            val sanitizedIntent = IntentSanitizer.Builder()
                .allowComponent(ComponentName(this, NewOperationActivity::class.java))
                .allowData { uri -> uri.scheme in listOf("bitcoin", "lightning", "muun") }
                .allowExtra(OPERATION_URI, String::class.java)
                .allowExtra(ORIGIN, String::class.java)
                .allowFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .build()
                .sanitize(intent) { error ->
                    penalties.add(error)
                }

            if (penalties.isNotEmpty()) {
                Timber.e("Invalid NewOp Intent uri: %s", penalties.toString())

                // Add 1 breadcrumb per penalty, strings are long and will be cutoff otherwise
                penalties.forEach { penalty ->
                    Timber.i("NewOp Intent Penalty: %s", penalty)
                }
            }

            startActivity(sanitizedIntent)
            finishActivity()
            return
        }

        val newOpOrigin = getOrigin(intent)
        Timber.i("NewOp Origin: %s", newOpOrigin)

        try {
            val uri = getValidIntentUri(intent)

            val operationUri: OperationUri = OperationUri.fromString(uri)
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

        } catch (e: Exception) {

            Timber.e(e)

            showTextToast(getString(R.string.error_no_valid_payment_details_provided))
            presenter.handleError(e, newOpOrigin)
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
        viewModel.subscribeToAllSensors(this, this)
    }

    override fun onPause() {
        super.onPause()
        viewModel.unsubscribeFromAllSensors()
    }

    override fun onStop() {
        super.onStop()
        if (countdownTimer != null) {
            countdownTimer!!.cancel()
            countdownTimer = null
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        viewModel.onGestureDetected(event)
        return super.onTouchEvent(event)
    }

    private fun getValidIntentUri(intent: Intent): String {
        return intent.getStringExtra(OPERATION_URI)     // from startActivity
            ?: intent.dataString                          // deeplink
            ?: throw InvalidOperationUriException(
                "Invalid NewOp Intent uri"
            )                                                   // uri is not valid for our scheme
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
        generateAppEvent(S_NEW_OP_AMOUNT().eventId)
        updateReceiver(state.resolved.paymentIntent, receiver)

        val balance = BitcoinAmount.fromLibwallet(state.totalBalance)
        val newAmount = state.amount.inInputCurrency.adapt()

        amountInput.isEnabled = true
        amountInput.setAmountError(false) // Actually needed to set correct textColor in some cases
        if (amountInput.value.isZero || newAmount.isPositive) {
            // Ugly hack to work around state machine's current API short comings
            // TODO: change state machine changeCurrency API
            amountInput.value = newAmount
        }
        amountInput.setSecondaryAmount(
            "${getString(R.string.available_balance)}: %s",
            balance.inInputCurrency
        )

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
        generateAppEvent("amount_input_error")
        amountInput.setAmountError(true)
        actionButton.isEnabled = false
    }

    override fun goToEnterDescriptionState(
        state: EnterDescriptionState,
        receiver: Receiver,
        btcUnit: BitcoinUnit,
    ) {
        generateAppEvent(S_NEW_OP_DESCRIPTION().eventId)

        updateReceiver(state.resolved.paymentIntent, receiver)

        show1ConfNotice(state.validated.swapInfo?.isOneConf ?: false)

        val isValid = !state.validated.feeNeedsChange
        val isSatSelectedAsCurrency = amountInput.isSatSelectedAsCurrency
        setAmount(
            selectedAmount,
            DisplayAmount(state.amountInfo.amount, btcUnit, isSatSelectedAsCurrency, isValid)
        )

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

    override fun goToConfirmState(
        state: ConfirmStateViewModel,
        receiver: Receiver,
        btcUnit: BitcoinUnit,
    ) {
        generateAppEvent(S_NEW_OP_CONFIRMATION().eventId)

        updateReceiver(state.paymentIntent, receiver)

        show1ConfNotice(state.validated.swapInfo?.isOneConf ?: false)

        val isValid = !state.validated.feeNeedsChange
        val isSatSelectedAsCurrency = amountInput.isSatSelectedAsCurrency
        setAmount(
            selectedAmount,
            DisplayAmount(state.amountInfo.amount, btcUnit, isSatSelectedAsCurrency, isValid)
        )
        setAmount(
            feeAmount,
            DisplayAmount(state.validated.fee, btcUnit, isSatSelectedAsCurrency, isValid)
        )
        setAmount(
            totalAmount,
            DisplayAmount(state.validated.total, btcUnit, isSatSelectedAsCurrency, isValid)
        )

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
            presenter.handleConfirmClick(nfcReaderLauncher)
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
        generateAppEvent(S_MANUALLY_ENTER_FEE().eventId)
        showOverlayFragment(RecommendedFeeFragment(), true)
    }

    override fun goToEditFeeManually() {
        generateAppEvent(S_SELECT_FEE().eventId)
        replaceFragment(ManualFeeFragment(), true)
    }

    override fun goToConfirmedFee() {
        generateAppEvent("s_new_op_error")
        // Go back to confirm Step
        clearFragmentBackStack()
        hideFragmentOverlay()
    }

    override fun showAbortDialog() {
        generateAppEvent("s_new_op_abort_dialog")
        MuunDialog.Builder()
            .title(R.string.new_operation_abort_alert_title)
            .message(R.string.new_operation_abort_alert_body)
            .positiveButton(R.string.yes_cancel) {
                generateAppEvent("s_new_op_abort_dialog_yes")
                presenter.finishAndGoHome()
            }
            .negativeButton(R.string.no)
            .onDismiss {
                generateAppEvent("s_new_op_abort_dialog_dismiss")
                presenter.cancelAbort()
            }
            .build()
            .let(this::showDialog)
    }

    override fun finishAndGoHome() {
        if (isTaskRoot) {
            startActivity(HomeActivity.getStartActivityIntent(this))
        }
        finishActivity()
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
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
        generateAppEvent("s_error_screen")
        showOverlayFragment(NewOperationErrorFragment.create(type), false)
        scrollableLayout.setUserInteractionEnabled(false)
    }

    override fun onCountDownTick(remainingSeconds: Long) {
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

    override fun onCountDownFinish() {
        presenter.handleNewOpError(NewOperationErrorType.INVOICE_EXPIRED)
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
        toggleCurrencyChange(selectedAmount)
        toggleCurrencyChange(feeAmount)
        toggleCurrencyChange(totalAmount)
    }

    private fun toggleCurrencyChange(view: TextView) {
        val displayAmt: DisplayAmount? = view.tag as? DisplayAmount
        if (displayAmt != null) {    // If view isn't being show yet, we do nothing

            val (_, selectedCurrencyCode) = view.text.toString().split(" ")

            val amountToDisplay = displayAmt.rotateCurrency(selectedCurrencyCode)

            view.text = toRichText(amountToDisplay, displayAmt.getBitcoinUnit(), displayAmt.isValid)
        }
    }

    private fun showLoadingSpinner(showLoading: Boolean) {
        resolvingSpinner.visibility = if (showLoading) View.VISIBLE else View.GONE
        root.visibility = if (showLoading) View.GONE else View.VISIBLE
    }

    private fun updateReceiver(paymentIntent: PaymentIntent, receiver: Receiver) {
        val uri = paymentIntent.uri
        when (paymentIntent.getPaymentType()) {
            PaymentRequest.Type.TO_ADDRESS -> setReceiver(uri.address)
            PaymentRequest.Type.TO_CONTACT -> setReceiver(receiver.contact!!)
            PaymentRequest.Type.TO_LN_INVOICE -> setReceiver(uri.invoice, receiver.swap!!)
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
        countdownTimer?.cancel()
        countdownTimer = MuunCountdownTimer(invoice.remainingMillis(), this)
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
        view.text = toRichText(amount, displayAmount.getBitcoinUnit(), displayAmount.isValid)
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
        generateAppEvent("back_pressed")
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

    private fun generateAppEvent(eventName: String) {
        viewModel.generateAppEvent(eventName)
    }
}
