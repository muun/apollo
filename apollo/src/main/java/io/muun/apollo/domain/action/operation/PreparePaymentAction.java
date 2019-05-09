package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.preferences.ExchangeRateWindowRepository;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.data.preferences.TransactionSizeRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.HardwareWalletActions;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.action.contacts.FetchSingleContactAction;
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction;
import io.muun.apollo.domain.model.BitcoinAmount;
import io.muun.apollo.domain.model.ExchangeRateWindow;
import io.muun.apollo.domain.model.FeeWindow;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.PreparedPayment;
import io.muun.apollo.domain.model.User;
import io.muun.apollo.domain.utils.FeeCalculator;
import io.muun.common.crypto.hwallet.HardwareWalletState;
import io.muun.common.model.ExchangeRateProvider;
import io.muun.common.model.SizeForAmount;
import io.muun.common.rx.RxHelper;
import io.muun.common.utils.BitcoinUtils;

import android.support.annotation.VisibleForTesting;
import rx.Observable;

import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.money.CurrencyUnit;
import javax.money.convert.CurrencyConversion;

@Singleton
public class PreparePaymentAction extends BaseAsyncAction1<PaymentRequest, PreparedPayment> {

    private final FetchNextTransactionSizeAction fetchNextTransactionSize;
    private final FetchRealTimeDataAction fetchRealTimeData;
    private final FetchSingleContactAction fetchSingleContact;

    private final UserRepository userRepository;
    private final FeeWindowRepository feeWindowRepository;
    private final ExchangeRateWindowRepository exchangeRateWindowRepository;
    private final TransactionSizeRepository transactionSizeRepository;

    // TODO: remove this dependency when the action bag is destroyed:
    private final HardwareWalletActions hardwareWalletActions;

    /**
     * Build an Operation from an PaymentRequest, pre-fetching outdated information if needed.
     */
    @Inject
    public PreparePaymentAction(FetchNextTransactionSizeAction fetchNextTransactionSize,
                                FetchRealTimeDataAction fetchRealTimeData,
                                FetchSingleContactAction fetchSingleContact,
                                UserRepository userRepository,
                                FeeWindowRepository feeWindowRepository,
                                ExchangeRateWindowRepository exchangeRateWindowRepository,
                                TransactionSizeRepository transactionSizeRepository,
                                HardwareWalletActions hardwareWalletActions) {

        this.fetchNextTransactionSize = fetchNextTransactionSize;
        this.fetchRealTimeData = fetchRealTimeData;
        this.fetchSingleContact = fetchSingleContact;

        this.userRepository = userRepository;
        this.feeWindowRepository = feeWindowRepository;
        this.exchangeRateWindowRepository = exchangeRateWindowRepository;
        this.transactionSizeRepository = transactionSizeRepository;

        this.hardwareWalletActions = hardwareWalletActions;
    }

    @Override
    public Observable<PreparedPayment> action(PaymentRequest payReq) {
        return Observable.defer(() -> preparePayment(payReq));
    }

    private Observable<PreparedPayment> preparePayment(PaymentRequest payReq) {
        // We need to ensure our local data is up to date before creating an Operation from this
        // Draft. If everything is already fresh (almost always the case) these calls will return
        // immediately:
        final List<Observable<?>> preparations = new LinkedList<>();

        preparations.add(fetchRealTimeData.action());
        preparations.add(fetchNextTransactionSize.action());

        if (payReq.contact != null) {
            preparations.add(fetchSingleContact.action(payReq.contact));
        }

        if (payReq.hardwareWallet != null) {
            // NOTE: normally, we would use this preparation stage to ensure we have the latest
            // data about the hardware wallet's UTXO set and state. Due to the current UI and data
            // flow, just we assume the data is fresh enough.
        }

        return Observable.zip(preparations, RxHelper::toVoid)
                .map(ignored -> preparePaymentFromRequest(payReq));
    }

    /**
     * Prepare a payment from a PaymentRequest.
     */
    @VisibleForTesting
    public PreparedPayment preparePaymentFromRequest(PaymentRequest payReq) {
        // Gather required information:
        final User user = userRepository.fetchOne();
        final FeeWindow feeWindow = feeWindowRepository.fetchOne();
        final ExchangeRateWindow rateWindow = exchangeRateWindowRepository.fetchOne();
        final List<SizeForAmount> txSize = getSizeProgression(payReq);

        // Create currency converters:
        final ExchangeRateProvider rates = new ExchangeRateProvider(rateWindow.rates);
        final CurrencyUnit inputCurrency = payReq.amount.getCurrency();
        final CurrencyUnit primaryCurrency = user.primaryCurrency;

        final CurrencyConversion toBtc = rates.getCurrencyConversion("BTC");
        final CurrencyConversion toInput = rates.getCurrencyConversion(inputCurrency);
        final CurrencyConversion toPrimary = rates.getCurrencyConversion(primaryCurrency);

        // Payment amount, as given by the user:
        final long amountInSatoshis = BitcoinUtils.bitcoinsToSatoshis(payReq.amount.with(toBtc));

        // Now, we need to decide the actual amount we're going to spend, and the fees charged along
        // the way. These are different for regular and swap transactions:
        final long outputAmountInSatoshis;
        final long sweepFeeInSatoshis;

        if (payReq.swap != null) {
            // Output amount given by Swapper, which includes a sweep fee:
            outputAmountInSatoshis = payReq.swap.fundingOutput.outputAmountInSatoshis;
            sweepFeeInSatoshis = payReq.swap.sweepFeeInSatoshis;

        } else {
            // Output amount is the payment amount, sweep fee is zero:
            outputAmountInSatoshis = amountInSatoshis;
            sweepFeeInSatoshis = 0;
        }

        // With the actual output amount, let's calculate the effective fee for this transaction:
        final long feeInSatoshis = new FeeCalculator(feeWindow.feeInSatoshisPerByte, txSize)
                .getFeeForAmount(outputAmountInSatoshis);

        // And knowing that, the actual total we'll spend:
        final long totalInSatoshis = outputAmountInSatoshis + feeInSatoshis;

        // Convert everything to everything:
        final BitcoinAmount amount = new BitcoinAmount(
                amountInSatoshis,
                payReq.amount,
                payReq.amount.with(toPrimary)
        );

        final BitcoinAmount fee = new BitcoinAmount(
                feeInSatoshis,
                BitcoinUtils.satoshisToBitcoins(feeInSatoshis).with(toInput),
                BitcoinUtils.satoshisToBitcoins(feeInSatoshis).with(toPrimary)
        );

        final BitcoinAmount sweepFee = new BitcoinAmount(
                sweepFeeInSatoshis,
                BitcoinUtils.satoshisToBitcoins(sweepFeeInSatoshis).with(toInput),
                BitcoinUtils.satoshisToBitcoins(sweepFeeInSatoshis).with(toPrimary)
        );

        final BitcoinAmount total = new BitcoinAmount(
                totalInSatoshis,
                BitcoinUtils.satoshisToBitcoins(totalInSatoshis).with(toInput),
                BitcoinUtils.satoshisToBitcoins(totalInSatoshis).with(toPrimary)
        );

        // Done!
        return new PreparedPayment(
                amount,
                fee,
                sweepFee,
                total,
                payReq.description,
                rateWindow.windowHid
        );
    }

    private List<SizeForAmount> getSizeProgression(PaymentRequest payReq) {
        if (payReq.type == PaymentRequest.Type.FROM_HARDWARE_WALLET) {
            final HardwareWalletState state = hardwareWalletActions
                    .getHardwareWalletState(payReq.hardwareWallet.hid);

            return state.getSizeForAmounts();

        } else {
            return transactionSizeRepository.getNextTransactionSize().sizeProgression;
        }
    }
}
