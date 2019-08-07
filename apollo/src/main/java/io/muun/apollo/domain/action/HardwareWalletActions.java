package io.muun.apollo.domain.action;

import io.muun.apollo.data.db.base.ElementNotFoundException;
import io.muun.apollo.data.db.hwallet.HardwareWalletDao;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.ExchangeRateWindowRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.base.AsyncAction1;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.model.ExchangeRateWindow;
import io.muun.apollo.domain.model.HardwareWallet;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.PendingWithdrawal;
import io.muun.apollo.domain.model.User;
import io.muun.apollo.domain.model.trezor.HardwareWalletWithdrawal;
import io.muun.apollo.domain.selector.HardwareWalletStateSelector;
import io.muun.common.crypto.hwallet.ExtendedHardwareWalletState;
import io.muun.common.crypto.hwallet.HardwareWalletState;
import io.muun.common.model.ExchangeRateProvider;
import io.muun.common.rx.ObservableFn;
import io.muun.common.rx.RxHelper;
import io.muun.common.utils.BitcoinUtils;
import io.muun.common.utils.Preconditions;

import rx.Observable;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.money.convert.CurrencyConversion;
import javax.validation.constraints.NotNull;


@Singleton
public class HardwareWalletActions {

    private final HardwareWalletDao hardwareWalletDao;

    private final UserRepository userRepository;
    private final ExchangeRateWindowRepository exchangeRateWindowRepository;

    private final HoustonClient houstonClient;

    public final AsyncAction1<HardwareWallet, HardwareWallet> unpairHardwareWalletAction;
    public final AsyncAction1<HardwareWallet, ExtendedHardwareWalletState>
            fetchHardwareWalletStateAction;

    public final HardwareWalletStateSelector hardwareWalletStateSelector;

    /**
     * Constructor.
     */
    @Inject
    public HardwareWalletActions(HardwareWalletDao hardwareWalletDao,
                                 UserRepository userRepository,
                                 ExchangeRateWindowRepository exchangeRateWindowRepository,
                                 HoustonClient houstonClient,
                                 AsyncActionStore asyncActionStore,
                                 HardwareWalletStateSelector hardwareWalletStateSelector) {

        this.hardwareWalletDao = hardwareWalletDao;
        this.userRepository = userRepository;
        this.exchangeRateWindowRepository = exchangeRateWindowRepository;
        this.houstonClient = houstonClient;

        this.unpairHardwareWalletAction = asyncActionStore
                .get("hwallets/unpair", this::unpairHardwareWallet);

        this.fetchHardwareWalletStateAction = asyncActionStore
                .get("hwallets/state", this::fetchHardwareWalletState);
        this.hardwareWalletStateSelector = hardwareWalletStateSelector;
    }

    public Observable<List<HardwareWallet>> fetchAll() {
        return hardwareWalletDao.fetchPaired();
    }

    public Observable<HardwareWallet> fetchOne(long hid) {
        return hardwareWalletDao.fetchByHid(hid);
    }

    /**
     * Fetch HardwareWallet associated with an Operation, if exists.
     */
    public Observable<HardwareWallet> fetchHardwareWallet(Operation operation) {
        return operation.hardwareWalletHid != null
                ? fetchOne(operation.hardwareWalletHid).first().onErrorReturn(null)
                : Observable.just(null);
    }

    /**
     * Build the opaque payload sent to a HardwareWallet for a withdrawal.
     */
    public HardwareWalletWithdrawal buildWithdrawal(PendingWithdrawal pendingWithdrawal) {

        return HardwareWalletWithdrawal.buildDraft(
                getHardwareWalletState(pendingWithdrawal.hardwareWalletHid),
                pendingWithdrawal.amount.inSatoshis,
                pendingWithdrawal.fee.inSatoshis,
                pendingWithdrawal.receiverAddress
        );
    }

    /**
     * Fetch the list of HardwareWallets, replacing local data.
     */
    public Observable<Void> fetchReplaceHardwareWallets() {
        return houstonClient.fetchHardwareWallets()
                .compose(ObservableFn.flatDoOnNext(i -> hardwareWalletDao.deleteAll()))
                .flatMap(Observable::from)
                .flatMap(hardwareWalletDao::store)
                .lastOrDefault(null)
                .map(RxHelper::toVoid);
    }

    /**
     * Create or update a HardwareWallet, both here and in Houston.
     */
    public Observable<HardwareWallet> createOrUpdate(HardwareWallet walletInfo) {
        return houstonClient.createOrUpdateHardwareWallet(walletInfo)
                .flatMap(houstonHardwareWallet ->
                        hardwareWalletDao.fetchByHid(houstonHardwareWallet.getHid())
                                .first()
                                .map(localHW -> localHW.mergeWithUpdate(houstonHardwareWallet))
                                .compose(ObservableFn.onTypedErrorResumeNext(
                                        ElementNotFoundException.class,
                                        error -> Observable.just(houstonHardwareWallet)
                                ))
                                .flatMap(hardwareWalletDao::store)
                );
    }

    private Observable<HardwareWallet> unpairHardwareWallet(HardwareWallet hardwareWallet) {
        return houstonClient.unpairHardwareWallet(hardwareWallet)
                .flatMap(hardwareWalletDao::store);
    }

    private Observable<ExtendedHardwareWalletState> fetchHardwareWalletState(HardwareWallet hw) {
        return houstonClient.fetchHardwareWalletState(hw)
                .doOnNext(state -> {
                    HardwareWalletStateSelector.Companion.putInCache(hw.getHid(), state);

                })
                .map(state -> {
                    final User user = userRepository.fetchOne();
                    final ExchangeRateWindow rateWindow = exchangeRateWindowRepository.fetchOne();

                    final CurrencyConversion toPrimary = new ExchangeRateProvider(rateWindow.rates)
                            .getCurrencyConversion(user.primaryCurrency);

                    final long balanceInSatoshis = state.getBalanceInSatoshis();

                    return ExtendedHardwareWalletState.from(
                            state,
                            BitcoinUtils.satoshisToBitcoins(balanceInSatoshis).with(toPrimary)
                    );
                });
    }

    /**
     * Get the cached copy of a HardwareWallet's state, or fail if not available.
     */
    @NotNull
    public HardwareWalletState getHardwareWalletState(long hardwareWalletHid) {
        final Map<Long, HardwareWalletState> walletStateByHid = hardwareWalletStateSelector.get();
        final HardwareWalletState hardwareWalletState = walletStateByHid.get(hardwareWalletHid);
        Preconditions.checkNotNull(hardwareWalletState);

        return hardwareWalletState;
    }
}
