package io.muun.apollo.presentation.ui.new_operation;

import io.muun.apollo.R;
import io.muun.apollo.domain.errors.BugDetected;
import io.muun.apollo.domain.libwallet.DecodedInvoice;
import io.muun.apollo.domain.model.BitcoinAmount;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.CurrencyDisplayMode;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.PaymentAnalysis;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.apollo.domain.model.SubmarineSwapReceiver;
import io.muun.apollo.presentation.analytics.Analytics;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.InvoiceExpirationCountdownTimer;
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.edit_fee.EditFeeActivity;
import io.muun.apollo.presentation.ui.fragments.new_op_error.NewOperationErrorFragment;
import io.muun.apollo.presentation.ui.helper.MoneyHelper;
import io.muun.apollo.presentation.ui.home.HomeActivity;
import io.muun.apollo.presentation.ui.listener.OnBackPressedListener;
import io.muun.apollo.presentation.ui.listener.SimpleTextWatcher;
import io.muun.apollo.presentation.ui.utils.ExtensionsKt;
import io.muun.apollo.presentation.ui.utils.StyledStringRes;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.apollo.presentation.ui.view.MuunAmountInput;
import io.muun.apollo.presentation.ui.view.MuunButton;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;
import io.muun.apollo.presentation.ui.view.MuunPill;
import io.muun.apollo.presentation.ui.view.NoticeBanner;
import io.muun.apollo.presentation.ui.view.RichText;
import io.muun.apollo.presentation.ui.view.StatusMessage;
import io.muun.apollo.presentation.ui.view.TextInputWithBackHandling;
import io.muun.common.Optional;
import io.muun.common.Rules;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.model.ExchangeRateProvider;
import io.muun.common.utils.BitcoinUtils;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import butterknife.BindColor;
import butterknife.BindView;
import butterknife.BindViews;
import butterknife.OnClick;
import icepick.State;
import timber.log.Timber;

import javax.inject.Inject;
import javax.money.MonetaryAmount;
import javax.validation.constraints.NotNull;

import static android.animation.LayoutTransition.APPEARING;
import static android.animation.LayoutTransition.DISAPPEARING;

public class NewOperationActivity extends BaseActivity<NewOperationPresenter> implements
        NewOperationView {

    public static final String OPERATION_URI = "operation_uri";
    public static final String ORIGIN = "origin";

    private static final int ANIMATION_DURATION_MS = 300;
    private static final int EDIT_FEE_REQUEST_CODE = 1001;

    private static final long INVOICE_EXPIRATION_WARNING_TIME_IN_SECONDS = 60;

    /**
     * Create an Intent to launch this Activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context,
                                                @NotNull NewOperationOrigin origin,
                                                @NotNull OperationUri uri) {
        // NOTE: we use CLEAR_TOP to kill the previous instance of this Activity when launched,
        // so that only one of these exists at the same time (avoiding potentially awful confusion
        // if two different links are clicked). This only works as intended because no other
        // Activities will be on top of this one (we never startActivity here).

        return new Intent(context, NewOperationActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(OPERATION_URI, uri.toString())
                .putExtra(ORIGIN, origin.name());
    }

    @Inject
    Analytics analytics;

    // Components:
    @BindView(R.id.root_view)
    ConstraintLayout root;

    @BindView(R.id.overlay_container)
    ViewGroup overlayContainer;

    @BindView(R.id.new_operation_header)
    MuunHeader header;

    @BindView(R.id.scrollable_layout)
    View scrollableLayout;

    @BindView(R.id.new_operation_receiver)
    MuunPill receiver;

    @BindView(R.id.target_address)
    TextView receiverAddress;

    @BindView(R.id.new_operation_resolving)
    View resolvingSpinner;

    @BindView(R.id.muun_amount_input)
    MuunAmountInput amountInput;

    @BindView(R.id.use_all_funds)
    TextView useAllFundsView;

    @BindView(R.id.selected_amount)
    TextView selectedAmount;

    @BindView(R.id.amount_label)
    TextView amountLabel;

    @BindView(R.id.separator_amount)
    View amountSeparator;

    @BindView(R.id.invoice_expiration_countdown)
    TextView invoiceExpirationCountdown;

    @BindView(R.id.one_conf_notice_banner)
    NoticeBanner noticeBanner;

    @BindView(R.id.button_layout_anchor)
    View buttonLayoutAnchor;

    @BindView(R.id.muun_next_step_button)
    MuunButton actionButton;

    @BindView(R.id.muun_note_input)
    TextInputWithBackHandling descriptionInput;

    @BindView(R.id.notes_content)
    TextView descriptionContent;

    @BindView(R.id.notes_label)
    TextView descriptionLabel;

    @BindView(R.id.fee_label)
    TextView feeLabel;

    @BindView(R.id.fee_amount)
    TextView feeAmount;

    @BindView(R.id.total_amount)
    TextView totalAmount;

    @BindView(R.id.total_label)
    TextView totalLabel;

    @BindView(R.id.status_message)
    StatusMessage statusMessage;

    @BindView(R.id.insufficient_funds_message)
    StatusMessage insufficientFundsMessage;

    // Groups (to toggle visibility):
    @BindViews({R.id.muun_amount_input, R.id.use_all_funds})
    View[] amountEditableViews;

    @BindViews({R.id.amount_label, R.id.selected_amount, R.id.separator_amount})
    View[] amountSelectedViews;

    @BindViews({
            R.id.fee_label, R.id.fee_amount,
            R.id.total_label, R.id.total_amount,
            R.id.notes_label, R.id.notes_content
    })
    View[] noteEnteredViews;

    // Resources:
    @BindColor(R.color.text_secondary_color)
    int currencyTextColor;

    @BindColor(R.color.text_primary_color)
    int amountNumberTextColor;

    @BindColor(R.color.red)
    int errorColor;

    // State:
    @State
    CurrencyDisplayMode currencyDisplayMode;
    private NewOperationStep step;
    private NewOperationForm form;

    private boolean confirmationInProgress;

    @Nullable
    private InvoiceExpirationCountdownTimer countdownTimer;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.activity_new_operation;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();

        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) == 0) {
            // HACK ALERT
            // This Activity was not launched with the CLEAR_TOP flag, because it started from
            // an <intent-filter> defined in the manifest. Flags cannot be specified for these
            // Intents (pff), and we really need CLEAR_TOP, so... well, this:
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finishActivity();
        }

        final Optional<OperationUri> maybeUri = getValidIntentUri(intent);

        if (maybeUri.isPresent()) {
            final OperationUri uri = maybeUri.get();

            if (uri.isAsync()) {
                goToStep(NewOperationStep.RESOLVING);

            } else {
                goToStep(NewOperationStep.ENTER_AMOUNT); // assume loading is fast enough
            }

            if (uri.isLn()) {
                header.showTitle(R.string.new_operation_swap_title);
            }

            final NewOperationOrigin origin = getOrigin(intent);

            presenter.onViewCreated(uri, origin);

        } else {
            showTextToast(getString(R.string.error_no_valid_payment_details_provided));
            finishActivity();
        }
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        header.attachToActivity(this);
        header.showTitle(R.string.title_new_operation);
        header.setNavigation(Navigation.BACK);

        amountInput.setOnChangeListener(this::onAmountChange);

        useAllFundsView.setOnClickListener(view -> onUseAllFundsClick());
        useAllFundsView.setEnabled(false);

        descriptionInput.addTextChangedListener(new SimpleTextWatcher(this::onDescriptionChange));
        descriptionInput.setOnBackPressedListener(this::onBackPressed);

        selectedAmount.setOnClickListener(this::onDisplayedAmountClick);
        feeAmount.setOnClickListener(this::onDisplayedAmountClick);
        totalAmount.setOnClickListener(this::onDisplayedAmountClick);
    }

    @Override
    protected boolean isPresenterPersistent() {
        return true;
    }

    @Override
    public void onStop() {
        super.onStop();

        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
    }

    private CharSequence getFormattedDestinationData(SubmarineSwapReceiver receiver) {

        final CharSequence publicKeyText = Html.fromHtml(getString(
                R.string.new_operation_receiving_node_public_key,
                receiver.getPublicKey()
        ).replace(" ", "&nbsp;"));

        CharSequence networkAddressText = "";

        if (!TextUtils.isEmpty(receiver.getDisplayNetworkAddress())) {
            networkAddressText = Html.fromHtml(getString(
                    R.string.new_operation_receiving_node_network_address,
                    receiver.getDisplayNetworkAddress()
            ).replace(" ", "&nbsp;"));
        }

        final String linkText = getString(R.string.see_in_node_explorer).toUpperCase();

        return TextUtils.concat(
                publicKeyText,
                "\n\n",
                networkAddressText,
                "\n\n",
                linkBuilder.lightningNodeLink(receiver, linkText)
        );
    }

    @Override
    public void setLoading(boolean isLoading) {
        actionButton.setLoading(isLoading);

        final boolean isSwap = form != null && form.payReq != null && form.payReq.getSwap() != null;

        feeLabel.setClickable(!isSwap && !isLoading);

        // TODO move onNextButton and onBackPressed logic to presenter and set flag in handleError
        if (!isLoading) {
            confirmationInProgress = false;
        }
    }

    @Override
    public void showErrorScreen(NewOperationErrorType type) {
        presenter.reportError(type);
        showOverlayFragment(NewOperationErrorFragment.create(type));
    }

    private void showOverlayFragment(Fragment fragment) {
        UiUtils.lastResortHideKeyboard(this);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.overlay_container, fragment)
                .commitNow();

        overlayContainer.setVisibility(View.VISIBLE);
        ExtensionsKt.isUserInteractionEnabled(scrollableLayout, false);
    }

    private Optional<Fragment> getOverlayFragment() {
        return Optional.ofNullable(
                getSupportFragmentManager().findFragmentById(R.id.overlay_container)
        );
    }

    /**
     * Reflect results from the PaymentAnalysis in the view.
     */
    public void setPaymentAnalysis(PaymentAnalysis analysis) {
        setBalanceAnalysis(analysis);
        setAmountAnalysis(analysis);
        setDescriptionAnalysis(analysis);

        amountInput.setExchangeRateProvider(
                new ExchangeRateProvider(analysis.getRateWindow().rates)
        );
    }

    private void setBalanceAnalysis(PaymentAnalysis analysis) {
        final MonetaryAmount balance = analysis.getTotalBalance().inInputCurrency;

        final CharSequence balanceText = TextUtils.concat(
                getString(R.string.available_balance),
                ": ",
                MoneyHelper.formatLongMonetaryAmount(
                        balance,
                        true,
                        currencyDisplayMode
                )
        );

        amountInput.setSecondaryAmount(balanceText);
        useAllFundsView.setEnabled(true);
    }

    private void setAmountAnalysis(PaymentAnalysis analysis) {
        // Updates to amount input:
        final boolean showAmountInputError = (
                analysis.isAmountTooSmall() || !analysis.getCanPayWithoutFee()
        );

        amountInput.setAmountError(showAmountInputError);

        // Updates to summary rows:
        final boolean takeFeeFromAmount = analysis.getPayReq().getTakeFeeFromAmount();

        if (step == NewOperationStep.ENTER_DESCRIPTION && takeFeeFromAmount) {
            // In this case, we show the original amount (without discounting fee)
            // Note that we say this amount is valid, regardless of `showAmountInputErrors`, because
            // they shouldn't be displayed during this step.
            // Note: in useAllFunds/takeFeeFromAmount originalAmount == total
            setDetailText(selectedAmount, analysis.getTotal(), true);
        } else {
            // Otherwise, show the analyzed amount:
            setDetailText(selectedAmount, analysis.getAmount(), !showAmountInputError);
        }

        setDetailText(feeAmount, analysis.getFee(), analysis.getCanPayWithSelectedFee());
        setDetailText(totalAmount, analysis.getTotal(), analysis.getCanPayWithSelectedFee());

        setAmountAnalysisForSwap(analysis);

        // Updates to action button:
        if (step == NewOperationStep.ENTER_AMOUNT) {
            actionButton.setEnabled(!showAmountInputError);

        } else if (step == NewOperationStep.CONFIRM) {
            actionButton.setEnabled(analysis.isValid());
        }

        if (step == NewOperationStep.CONFIRM) {
            insufficientFundsMessage.setVisibility(View.GONE);
            statusMessage.setVisibility(View.GONE);

            if (analysis.getPayReq().getTakeFeeFromAmount()) {
                statusMessage.setWarning(
                        R.string.use_all_funds_warning_message,
                        R.string.use_all_funds_warning_desc,
                        false,
                        ':'
                );
            }

            if (!analysis.getCanPayWithSelectedFee()) {

                final CharSequence styledDesc = ExtensionsKt.getStyledString(
                        this,
                        R.string.new_op_insufficient_funds_warn_desc
                );

                insufficientFundsMessage.setWarning(
                        getString(R.string.new_op_insufficient_funds_warn_message),
                        styledDesc,
                        false,
                        ':'
                );

                insufficientFundsMessage.setOnClickListener(v -> onFeeLabelClick());
            }
        }
    }

    private void setAmountAnalysisForSwap(PaymentAnalysis analysis) {
        final SubmarineSwap swap = analysis.getPayReq().getSwap();

        if (swap == null || analysis.getLightningFee() == null || analysis.getSweepFee() == null) {
            return; // We're dealing with an amount less invoice
        }

        BitcoinAmount totalFee = analysis.getSweepFee().add(analysis.getLightningFee());

        if (analysis.getFee() != null) {
            totalFee = totalFee.add(analysis.getFee());
        }

        setDetailText(feeAmount, totalFee, analysis.getCanPayWithSelectedFee());

        // Fee is not editable for submarine swaps:
        feeLabel.setCompoundDrawables(null, null, null, null);
        feeLabel.setClickable(false);
    }

    private void setDescriptionAnalysis(PaymentAnalysis analysis) {
        final boolean descriptionValid = !analysis.isDescriptionTooShort();

        descriptionContent.setText(analysis.getPayReq().getDescription());

        if (step == NewOperationStep.ENTER_DESCRIPTION) {
            actionButton.setEnabled(descriptionValid);
        }
    }

    private void setDetailText(TextView view, @Nullable BitcoinAmount value, boolean isValid) {
        view.setText(toRichTextOrBlank(value, isValid));
    }

    private void setDetailText(TextView view, @Nullable MonetaryAmount value, boolean isValid) {
        view.setText(toRichTextOrBlank(value, isValid));
    }

    private void setReceiverContact(Contact contact) {
        receiver.setText(contact.publicProfile.getFullName());
        receiver.setPictureUri(contact.publicProfile.profilePictureUrl);
        receiver.setPictureVisible(true);
        receiverAddress.setVisibility(View.GONE);
    }

    private void setReceiverAddress(String addr) {
        receiverAddress.setText(UiUtils.ellipsize(addr));
        receiverAddress.setVisibility(View.VISIBLE);
        receiver.setVisibility(View.GONE);

        receiverAddress.setOnClickListener((v) -> {
            final TitleAndDescriptionDrawer dialog = new TitleAndDescriptionDrawer();
            dialog.setDescription(addr);
            dialog.setTitle(R.string.new_operation_dialog_full_address);
            showDrawerDialog(dialog);

            presenter.reportShowDestinationInfo();
        });
    }

    private void setReceiverInvoice(DecodedInvoice invoice, SubmarineSwap swap) {

        // 1. set receiver/destination data
        if (!TextUtils.isEmpty(swap.getReceiver().getAlias())) {
            receiverAddress.setText(swap.getReceiver().getAlias());

        } else {
            receiverAddress.setText(UiUtils.ellipsize(invoice.getDestinationPublicKey()));
        }

        receiverAddress.setOnClickListener((v) -> {

            final TitleAndDescriptionDrawer dialog = new TitleAndDescriptionDrawer();
            dialog.setDescription(getFormattedDestinationData(swap.getReceiver()));
            dialog.setTitle(R.string.new_operation_receiving_node_data);
            showDrawerDialog(dialog);

            presenter.reportShowDestinationInfo();
        });

        receiverAddress.setVisibility(View.VISIBLE);
        receiver.setVisibility(View.GONE);

        // 2. Start invoice expiration countdown
        countdownTimer = buildCountDownTimer(invoice.remainingMillis());
        countdownTimer.start();

        buttonLayoutAnchor.setVisibility(View.VISIBLE);
        invoiceExpirationCountdown.setVisibility(View.VISIBLE);

        final Integer confirmationsNeeded = swap.getFundingOutput().getConfirmationsNeeded();
        if (confirmationsNeeded != null && confirmationsNeeded > 0) {
            noticeBanner.setText(
                    new StyledStringRes(this, R.string.new_op_1_conf_notice_banner)
                            .toCharSequence()
            );

            noticeBanner.setOnClickListener(v -> {
                final TitleAndDescriptionDrawer dialog = new TitleAndDescriptionDrawer();
                dialog.setTitle(R.string.new_op_1_conf_notice_title);
                dialog.setDescription(getString(R.string.new_op_1_conf_notice_desc));
                showDrawerDialog(dialog);
            });

            noticeBanner.setVisibility(View.VISIBLE);
        }

        // We need to post since buttonLayoutAnchor is not measured yet.
        new Handler().post(
                () -> UiUtils.setMarginBottom(scrollableLayout, buttonLayoutAnchor.getHeight())
        );
    }

    @Override
    public void setCurrencyDisplayMode(CurrencyDisplayMode mode) {
        this.currencyDisplayMode = mode;
        amountInput.setCurrencyDisplayMode(mode);
    }

    @Override
    public void setForm(NewOperationForm newForm) {

        final PaymentRequest payReq = newForm.payReq;

        if (payReq == null) {
            goToStep(NewOperationStep.RESOLVING);
            return;
        }

        amountInput.setValue(newForm.amount);

        if (newForm.isDescriptionConfirmed) {
            descriptionInput.setText(newForm.description);
        }

        if (this.form != null) {
            return; // don't auto-change the step after the 1st time the form was set
        }

        this.form = newForm;

        switch (payReq.getType()) {
            case TO_ADDRESS:
                setReceiverAddress(payReq.getAddress());
                break;

            case TO_CONTACT:
                setReceiverContact(payReq.getContact());
                break;

            case TO_LN_INVOICE:
                setReceiverInvoice(payReq.getInvoice(), payReq.getSwap());
                break;

            default:
                throw new MissingCaseError(payReq.getType());
        }

        if (newForm.isAmountConfirmed && newForm.isDescriptionConfirmed) {
            goToStep(NewOperationStep.CONFIRM);

        } else if (newForm.isAmountConfirmed) {
            goToStep(NewOperationStep.ENTER_DESCRIPTION);

        } else {
            goToStep(NewOperationStep.ENTER_AMOUNT);
        }
    }

    @Override
    public void editFee(PaymentRequest payReq) {
        requestExternalResult(
                this,
                EDIT_FEE_REQUEST_CODE,
                EditFeeActivity.getStartActivityIntent(this, payReq)
        );
    }

    @Override
    public void onExternalResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == EDIT_FEE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                final Optional<Double> selectedFeeRate = EditFeeActivity.getResult(data);

                selectedFeeRate.ifPresent(feeRate -> {
                            if (feeRate >= Rules.OP_MINIMUM_FEE_RATE) {
                                presenter.updateFeeRate(feeRate);

                            } else {
                                Timber.e(new BugDetected("Invalid fee rate selected: " + feeRate));
                            }
                        }
                );
            }

        }
    }

    /**
     * Inform the View if we lose or regain connection.
     */
    public void setConnectedToNetwork(boolean isConnected) {
        actionButton.setCoverText(isConnected ? null : getString(R.string.no_internet));
    }

    private void onAmountChange(MonetaryAmount amount) {
        presenter.updateAmount(amount);
    }

    private void onDescriptionChange(String description) {
        presenter.updateDescription(description);
    }

    private void onUseAllFundsClick() {
        if (! presenter.canUseAllFunds()) {
            return;
        }

        goToStep(NewOperationStep.ENTER_DESCRIPTION);
        presenter.confirmAmountUseAllFunds();
    }

    private void goToResolvingStep() {
        root.setLayoutTransition(null);
        showLoadingSpinner(true);

        // Avoid capturing focus and showing keyboard:
        amountInput.setVisibility(View.GONE);
        descriptionInput.setVisibility(View.GONE);
    }

    private void goToEnterAmountStep() {
        root.setLayoutTransition(null);
        showLoadingSpinner(false);

        // Always request focus before changing views to GONE
        // otherwise you might cause the previous input to lose focus
        // which in turn causes the keyboard to hide.
        changeVisibility(amountEditableViews, View.VISIBLE);
        amountInput.requestFocus();

        changeVisibility(amountSelectedViews, View.GONE);
        changeVisibility(noteEnteredViews, View.GONE);
        descriptionInput.setVisibility(View.GONE);

        actionButton.setText(R.string.confirm_amount);
        actionButton.setEnabled(!amountInput.isEmpty()); // needed when coming back to this step

        insufficientFundsMessage.setVisibility(View.GONE);
        statusMessage.setVisibility(View.GONE);
    }

    private void goToEnterDescriptionStep() {
        root.setLayoutTransition(null);
        showLoadingSpinner(false);

        final boolean isDescriptionTextVisible = descriptionContent.getVisibility() == View.VISIBLE;
        final float originX = amountInput.getX();
        final float originY = amountInput.getY() - amountInput.getHeight();

        changeVisibility(amountSelectedViews, View.VISIBLE);
        descriptionInput.setVisibility(View.VISIBLE);
        descriptionInput.requestFocus();

        // The amount input needs to be invisible in order to still be able to calculate the
        // adjustable text size.
        changeVisibility(amountEditableViews, View.INVISIBLE);
        changeVisibility(noteEnteredViews, View.GONE);

        // We only animate forward for now, since we prioritize efforts on the most common flow.
        // TODO: add the backward animation.
        if (!isDescriptionTextVisible) {
            doAnimateTransition(selectedAmount, revealFrom(-originX, originY));
            doAnimateTransition(amountLabel, revealFrom(originX, originY));
            doAnimateTransition(amountSeparator, revealFrom(0f, originY));
            doAnimateTransition(descriptionInput, revealFrom(
                    0f,
                    ((ViewGroup) descriptionInput.getParent()).getHeight())
            );
        }

        descriptionLabel.setVisibility(View.INVISIBLE);
        descriptionContent.setVisibility(View.INVISIBLE);

        actionButton.setText(R.string.confirm_note);

        final Editable text = descriptionInput.getText();
        actionButton.setEnabled(text != null && !TextUtils.isEmpty(text.toString()));

        insufficientFundsMessage.setVisibility(View.GONE);
        statusMessage.setVisibility(View.GONE);
    }

    /**
     * TODO: the animations here are sometimes dropping a couple frames due to the amount of stuff
     * we are doing on `OperationsActions::prepareOperation`. We should do something to
     * run more things on background and/or preload fees and total value.
     */
    private void goToConfirmStep() {
        hideSoftKeyboard();
        showLoadingSpinner(false);

        changeVisibility(amountSelectedViews, View.VISIBLE);

        descriptionInput.setVisibility(View.GONE);
        amountInput.setVisibility(View.GONE);
        descriptionContent.setText(descriptionInput.getText());

        changeVisibility(amountEditableViews, View.GONE);

        final LayoutTransition transition = new LayoutTransition();
        transition.setDuration(ANIMATION_DURATION_MS);

        // We only want to animate the effect that some appearing or disappearing view
        // has in other views, not the changes in the changed view itself.
        transition.disableTransitionType(APPEARING);
        transition.disableTransitionType(DISAPPEARING);

        root.setLayoutTransition(transition);

        descriptionLabel.setVisibility(View.VISIBLE);
        descriptionContent.setVisibility(View.VISIBLE);

        UiUtils.fadeIn(feeLabel);
        UiUtils.fadeIn(feeAmount);

        UiUtils.fadeIn(totalLabel);
        UiUtils.fadeIn(totalAmount);

        actionButton.setText(R.string.new_operation_confirm);
    }

    private void showLoadingSpinner(boolean showLoading) {
        resolvingSpinner.setVisibility(showLoading ? View.VISIBLE : View.GONE);
        root.setVisibility(showLoading ? View.GONE : View.VISIBLE);
    }

    private static Animation revealFrom(float deltaX, float deltaY) {
        final TranslateAnimation translateLabel = new TranslateAnimation(
                deltaX,
                0,
                deltaY,
                0
        );
        translateLabel.setDuration(ANIMATION_DURATION_MS);
        return translateLabel;
    }

    private static void doAnimateTransition(View view, Animation animation) {
        view.setAnimation(animation);
        UiUtils.fadeIn(view);
    }

    private void goToStep(NewOperationStep step) {
        this.step = step;

        switch (step) {
            case RESOLVING:
                analytics.report(new AnalyticsEvent.S_NEW_OP_LOADING());
                goToResolvingStep();
                break;

            case ENTER_AMOUNT:
                analytics.report(new AnalyticsEvent.S_NEW_OP_AMOUNT());
                goToEnterAmountStep();
                break;

            case ENTER_DESCRIPTION:
                analytics.report(new AnalyticsEvent.S_NEW_OP_DESCRIPTION());
                goToEnterDescriptionStep();
                break;

            case CONFIRM:
                analytics.report(new AnalyticsEvent.S_NEW_OP_CONFIRMATION());
                goToConfirmStep();
                break;

            default:
                throw new MissingCaseError(step);
        }
    }

    protected CharSequence toRichTextOrBlank(@Nullable BitcoinAmount amount, boolean isValid) {
        final MonetaryAmount amountToDisplay;

        if (amount == null) {
            // Nothing to show.
            amountToDisplay = null;

        } else if (form.displayInAlternateCurrency) {
            // Show BTC if current display is in FIAT, and the other way around.
            if (MoneyHelper.isBtc(form.amount)) {
                amountToDisplay = amount.inPrimaryCurrency;
            } else {
                amountToDisplay = BitcoinUtils.satoshisToBitcoins(amount.inSatoshis);
            }

        } else {
            // Show the amount as it originally was.
            amountToDisplay = amount.inInputCurrency;
        }

        return toRichTextOrBlank(amountToDisplay, isValid);
    }

    private CharSequence toRichTextOrBlank(@Nullable MonetaryAmount amount, boolean isValid) {
        if (amount == null) {
            return "";
        }

        return MoneyHelper.toLongRichText(
                amount,
                isValid ? amountNumberTextColor : errorColor,
                isValid ? currencyTextColor : errorColor,
                currencyDisplayMode
        );
    }

    private NewOperationOrigin getOrigin(Intent intent) {
        final String originExtra = intent.getStringExtra(ORIGIN);

        if (originExtra != null) {
            return NewOperationOrigin.valueOf(originExtra);
        } else {
            return NewOperationOrigin.EXTERNAL_LINK; // we landed from a URL click
        }
    }

    private Optional<OperationUri> getValidIntentUri(Intent intent) {
        final String uriString;

        if (intent.hasExtra(OPERATION_URI)) {
            uriString = intent.getStringExtra(OPERATION_URI);

        } else if (intent.getData() != null) {
            uriString = intent.getDataString();

        } else {
            return Optional.empty();
        }

        try {
            return Optional.of(OperationUri.fromString(uriString));

        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private void changeVisibility(View[] views, int visibility) {
        for (View aView : views) {
            aView.setVisibility(visibility);
        }
    }

    @OnClick(R.id.fee_label)
    protected void onFeeLabelClick() {
        presenter.editFee();
    }

    @OnClick(R.id.muun_next_step_button)
    protected void onNextButtonClick() {
        logStepChange("onNextButtonClick");

        switch (step) {
            case RESOLVING:
                break; // no next button during resolving step

            case ENTER_AMOUNT:
                goToStep(NewOperationStep.ENTER_DESCRIPTION);
                presenter.setAmountConfirmed(true);
                break;

            case ENTER_DESCRIPTION:
                goToStep(NewOperationStep.CONFIRM);
                presenter.setDescriptionConfirmed(true);
                break;

            case CONFIRM:
                presenter.confirmOperation();
                confirmationInProgress = true;
                break;

            default:
                throw new MissingCaseError(step);
        }
    }

    protected void onDisplayedAmountClick(View view) {
        form.displayInAlternateCurrency = !form.displayInAlternateCurrency;
        presenter.setDisplayInAlternateCurrency(form.displayInAlternateCurrency);
    }

    @Override
    public void onBackPressed() {
        if (confirmationInProgress) {
            return;
        }

        if (shouldIgnoreBackAndExit()) {
            return;
        }

        // TODO this code is repeated in SingleFragmentActivity, which right now we can't inherit.
        if (handleBackByOverlayFragment()) {
            return;
        }

        logStepChange("onBackPressed");

        switch (step) {
            case RESOLVING:
                finishAndGoToHome();
                break;

            case ENTER_AMOUNT:
                finishAndGoToHome();
                break;

            case ENTER_DESCRIPTION:
                if (form.isAmountFixed) {
                    showAbortDialog();

                } else {
                    goToStep(NewOperationStep.ENTER_AMOUNT);
                    presenter.setAmountConfirmed(false);
                    // Manually calling updateAmount to correctly handle useAllFunds flag on back
                    presenter.updateAmount(form.amount);
                }
                break;

            case CONFIRM:
                goToStep(NewOperationStep.ENTER_DESCRIPTION);
                presenter.setDescriptionConfirmed(false);
                break;

            default:
                throw new MissingCaseError(step);
        }
    }

    private boolean handleBackByOverlayFragment() {
        final Fragment overlayFragment = getOverlayFragment().orElse(null);

        if (overlayFragment instanceof OnBackPressedListener) {
            return ((OnBackPressedListener) overlayFragment).onBackPressed();
        } else {
            return false;
        }
    }

    private void showAbortDialog() {

        final MuunDialog muunDialog = new MuunDialog.Builder()
                .title(R.string.new_operation_abort_alert_title)
                .message(R.string.new_operation_abort_alert_body)
                .positiveButton(R.string.abort, this::finishAndGoToHome)
                .negativeButton(R.string.cancel, null)
                .build();

        showDialog(muunDialog);
    }

    private void logStepChange(String desc) {
        Timber.d("%s from %s step %s", desc, this.getClass().getCanonicalName(), step.name());
    }

    private void finishAndGoToHome() {
        if (isTaskRoot()) {
            startActivity(HomeActivity.getStartActivityIntent(this));
        }

        finishActivity();
    }

    private void hideSoftKeyboard() {
        final View currentFocus = this.getCurrentFocus();
        final InputMethodManager inputMethodManager =
                (InputMethodManager) this.getSystemService(Activity.INPUT_METHOD_SERVICE);

        if (currentFocus != null && inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }

    private InvoiceExpirationCountdownTimer buildCountDownTimer(long remainingMillis) {
        return new InvoiceExpirationCountdownTimer(this, remainingMillis) {

            @Override
            protected void onTextUpdate(long remainingSeconds, CharSequence timeText) {

                final String prefixText = ctx.getString(R.string.new_operation_invoice_exp_prefix);

                final CharSequence text = TextUtils.concat(prefixText, " ", timeText);

                final RichText richText = new RichText(text);

                if (remainingSeconds < INVOICE_EXPIRATION_WARNING_TIME_IN_SECONDS) {
                    richText.setForegroundColor(ContextCompat.getColor(ctx, R.color.red));
                }

                invoiceExpirationCountdown.setText(text);
            }

            @Override
            public void onFinish() {
                presenter.showErrorScreen(NewOperationErrorType.INVOICE_EXPIRED);
            }
        };
    }
}
