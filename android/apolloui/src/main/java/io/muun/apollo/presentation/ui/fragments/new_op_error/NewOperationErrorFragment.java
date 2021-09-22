package io.muun.apollo.presentation.ui.fragments.new_op_error;

import io.muun.apollo.R;
import io.muun.apollo.domain.model.BitcoinAmount;
import io.muun.apollo.domain.model.CurrencyDisplayMode;
import io.muun.apollo.domain.model.PaymentAnalysis;
import io.muun.apollo.domain.model.PaymentContext;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.presentation.ui.base.BaseFragment;
import io.muun.apollo.presentation.ui.helper.MoneyHelper;
import io.muun.apollo.presentation.ui.listener.OnBackPressedListener;
import io.muun.apollo.presentation.ui.new_operation.NewOperationErrorType;
import io.muun.apollo.presentation.ui.utils.ExtensionsKt;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.common.Optional;
import io.muun.common.Rules;
import io.muun.common.exception.MissingCaseError;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import butterknife.BindView;
import butterknife.OnClick;
import icepick.State;

import javax.money.MonetaryAmount;

public class NewOperationErrorFragment
        extends BaseFragment<NewOperationErrorPresenter>
        implements NewOperationErrorView, OnBackPressedListener {

    /**
     * Create a NewOperationErrorFragment with arguments.
     */
    public static NewOperationErrorFragment create(NewOperationErrorType errorType) {
        final NewOperationErrorFragment fragment = new NewOperationErrorFragment();
        fragment.setArguments(createArguments(errorType));

        return fragment;
    }

    private static Bundle createArguments(NewOperationErrorType errorType) {
        final Bundle arguments = new Bundle();
        arguments.putString(ARG_ERROR_TYPE, errorType.name());

        return arguments;
    }

    @BindView(R.id.title)
    TextView title;

    @BindView(R.id.description)
    TextView description;

    @BindView(R.id.insuffient_funds_extras)
    View insufficientFundsExtras;

    @BindView(R.id.insuffient_funds_extras_amount)
    TextView insufficientFundsAmount;

    @BindView(R.id.insuffient_funds_extras_balance)
    TextView insufficientFundsBalance;

    @State
    CurrencyDisplayMode mode;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.new_operation_error_fragment;
    }

    @Override
    protected void initializeUi(View view) {
        super.initializeUi(view);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // TODO this is here because, due to NewOperationActivity's current state, we can't make
        // this Fragment a SingleFragment (which already implements this behavior).
        presenter.setParentPresenter(
                (NewOperationErrorParentPresenter) getParentActivity().getPresenter()
        );

        UiUtils.lastResortHideKeyboard(getParentActivity());
    }

    @Override
    public void setErrorType(NewOperationErrorType errorType) {
        title.setText(getTitleRes(errorType));
        description.setText(ExtensionsKt.getStyledString(this, getDescriptionRes(errorType)));

        description.setOnClickListener(v -> {
            if (errorType == NewOperationErrorType.INVALID_SWAP
                    || errorType == NewOperationErrorType.INVOICE_NO_ROUTE
                    || errorType == NewOperationErrorType.GENERIC) {
                presenter.contactSupport();
                finishActivity();
            }
        });
    }

    @Override
    public void setCurrencyDisplayMode(CurrencyDisplayMode mode) {
        this.mode = mode;
    }

    @Override
    public void setPaymentContextForError(PaymentContext payCtx,
                                          Optional<PaymentRequest> maybePayReq,
                                          NewOperationErrorType errorType) {

        if (!maybePayReq.isPresent()) {
            return;
        }

        // Only error that needs paymentContext for now is INSUFFICIENT_FUNDS
        if (errorType != NewOperationErrorType.INSUFFICIENT_FUNDS) {
            return;
        }

        final PaymentRequest payReq = maybePayReq.get();

        final PaymentAnalysis analysis = payCtx.analyze(payReq);
        final PaymentAnalysis minFeeAnalysis = payCtx
                .analyze(payReq.withFeeRate(Rules.OP_MINIMUM_FEE_RATE));

        final MonetaryAmount minBalance;

        if (payReq.getSwap() != null && analysis.getTotal() != null) {
            // We could compute the fee (and thus, the total amount too) but we can't pay for it
            minBalance = analysis.getTotal().inInputCurrency;

        } else if (minFeeAnalysis.getTotal() != null) {
            // We could compute the minimum fee, but we can't pay for it:
            minBalance = minFeeAnalysis.getTotal().inInputCurrency;

        } else {
            // We couldn't even compute the minimum fee, amount must be above balance. We'll
            // show the minimum fee for all funds (which is, of course, a lie):

            if (minFeeAnalysis.getHasOnChainTransaction()) {
                minBalance = getMinBalanceForOnChain(payCtx, minFeeAnalysis).inInputCurrency;
            } else {
                minBalance = getMinBalanceForOffChain(minFeeAnalysis).inInputCurrency;
            }
        }

        final MonetaryAmount balance = analysis.getTotalBalance().inInputCurrency;

        insufficientFundsAmount.setText(formatLongMonetaryAmount(minBalance));
        insufficientFundsBalance.setText(formatLongMonetaryAmount(balance));
        insufficientFundsExtras.setVisibility(View.VISIBLE);
    }

    private BitcoinAmount getMinBalanceForOffChain(PaymentAnalysis analysis) {
        return analysis.getAmount().add(analysis.getLightningFee());
    }

    /**
     * We'll do a "best effort" guess of what minimum balance the user would need to pay for the
     * requested amount, even though that's not real or accurate (its impossible to calculate the
     * fee for an amount greater than the sum of the utxos, because the fee depends on the number
     * and the type of the utxos used). Our best guess is calculating the fee of a use all funds
     * transaction and adding that to the requested amount.
     */
    private BitcoinAmount getMinBalanceForOnChain(PaymentContext payCtx, PaymentAnalysis analysis) {

        BitcoinAmount realAmount = analysis.getAmount();

        if (analysis.getLightningFee() != null) {
            realAmount = realAmount.add(analysis.getLightningFee());
        }

        if (analysis.getSweepFee() != null) {
            realAmount = realAmount.add(analysis.getSweepFee());
        }

        final BitcoinAmount fakeFee = payCtx
                .analyzeUseAllFunds(analysis.getPayReq())
                .getFee();

        if (fakeFee != null) {
            return realAmount.add(fakeFee);
        } else {
            // Can happen in some cases (eg zero balance or fixed amount too large)
            return realAmount;
        }
    }

    private int getTitleRes(NewOperationErrorType errorType) {
        switch (errorType) {
            case AMOUNT_TOO_SMALL:
                return R.string.error_op_amount_too_small_title;

            case INSUFFICIENT_FUNDS:
                return R.string.error_op_insufficient_funds;

            case INVOICE_UNREACHABLE_NODE:
                return R.string.error_op_invoice_unreachable_node_title;

            case INVOICE_NO_ROUTE:
                return R.string.error_op_invoice_no_route_title;

            case INVOICE_WILL_EXPIRE_SOON:
                return R.string.error_op_invoice_will_expire_soon_title;

            case INVOICE_EXPIRED:
                return R.string.error_op_invoice_expired_title;

            case INVOICE_ALREADY_USED:
                return R.string.error_op_invoice_used_title;

            case INVOICE_MISSING_AMOUNT:
                return R.string.error_op_invoice_invalid_amount_title;

            case INVALID_INVOICE:
                return R.string.error_op_invoice_invalid_title;

            case EXCHANGE_RATE_WINDOW_TOO_OLD:
                return R.string.error_op_exchange_rate_window_too_old_title;

            case INVALID_SWAP:
                return R.string.error_op_generic;

            case CYCLICAL_SWAP:
                return R.string.error_op_cyclical_swap_title;

            case GENERIC:
                return R.string.error_op_generic;

            default:
                throw new MissingCaseError(errorType);
        }
    }

    private int getDescriptionRes(NewOperationErrorType errorType) {
        switch (errorType) {
            case AMOUNT_TOO_SMALL:
                return R.string.error_op_amount_too_small_desc;

            case INSUFFICIENT_FUNDS:
                return R.string.error_op_insufficient_funds_desc;

            case INVOICE_UNREACHABLE_NODE:
                return R.string.error_op_invoice_unreachable_node_desc;

            case INVOICE_NO_ROUTE:
                return R.string.error_op_invoice_no_route_desc;

            case INVOICE_WILL_EXPIRE_SOON:
                return R.string.error_op_invoice_will_expire_soon_desc;

            case INVOICE_EXPIRED:
                return R.string.error_op_invoice_expired_desc;

            case INVOICE_ALREADY_USED:
                return R.string.error_op_invoice_used_desc;

            case INVOICE_MISSING_AMOUNT:
                return R.string.error_op_invoice_invalid_amount_desc;

            case INVALID_INVOICE:
                return R.string.error_op_invoice_invalid_desc;

            case EXCHANGE_RATE_WINDOW_TOO_OLD:
                return R.string.error_op_exchange_rate_window_too_old_desc;

            case INVALID_SWAP:
                return R.string.error_op_generic_desc;

            case CYCLICAL_SWAP:
                return R.string.error_op_cyclical_swap_desc;

            case GENERIC:
                return R.string.error_op_generic_desc;

            default:
                throw new MissingCaseError(errorType);
        }
    }

    private String formatLongMonetaryAmount(MonetaryAmount amount) {
        return MoneyHelper.formatLongMonetaryAmount(
                amount,
                mode,
                io.muun.apollo.domain.utils.ExtensionsKt.locale(requireContext())
        );
    }

    @OnClick(R.id.exit)
    void onExitClick() {
        presenter.goHomeInDefeat();
    }

    @Override
    public boolean onBackPressed() {
        onExitClick();
        return true;
    }
}
