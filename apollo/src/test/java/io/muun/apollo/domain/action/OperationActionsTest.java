package io.muun.apollo.domain.action;

import io.muun.apollo.BaseTest;
import io.muun.apollo.data.db.operation.OperationDao;
import io.muun.apollo.data.db.public_profile.PublicProfileDao;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.os.ClipboardProvider;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.ExchangeRateWindowRepository;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.data.preferences.TransactionSizeRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.NotificationService;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.ExchangeRateWindow;
import io.muun.apollo.domain.model.FeeWindow;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.User;
import io.muun.apollo.template.TemplateHelpers;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.tx.PartiallySignedTransaction;
import io.muun.common.model.ExchangeRateProvider;
import io.muun.common.model.OperationDirection;
import io.muun.common.model.OperationStatus;
import io.muun.common.utils.BitcoinUtils;

import android.support.v4.util.Pair;
import br.com.six2six.fixturefactory.Fixture;
import org.bitcoinj.core.Transaction;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import rx.Observable;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import java.util.List;
import java.util.concurrent.Executors;

import javax.money.MonetaryAmount;

import static io.muun.apollo.TestUtils.fetchItemFromObservable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class OperationActionsTest extends BaseTest {

    private static final String[] ALL_LABELS = {"incoming internal", "outgoing internal"};

    @Mock
    private AsyncActionStore asyncActionStore;

    @Mock
    private OperationDao operationDao;

    @Mock
    private PublicProfileDao publicProfileDao;

    @Mock
    private KeysRepository keysRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BitcoinActions bitcoinActions;

    @Mock
    private SyncActions syncActions;

    @Mock
    private ContactActions contactActions;

    @Mock
    private HoustonClient houstonClient;

    @Mock
    private ExchangeRateWindowRepository exchangeRateWindowRepository;

    @Mock
    private TransactionSizeRepository transactionSizeRepository;

    @Mock
    private FeeWindowRepository feeWindowRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuthRepository authRepository;

    @Mock
    private ClipboardProvider clipboardProvider;

    private ExecutionTransformerFactory executionTransformerFactory =
            new ExecutionTransformerFactory(
                    Executors.newCachedThreadPool(),
                    Schedulers.computation()
            );

    private OperationActions operationActions;

    @Before
    public void setUp() {

        doReturn(null).when(asyncActionStore).get(anyString(), any(Func1.class));

        operationActions = spy(new OperationActions(
                contactActions,
                bitcoinActions,
                syncActions,
                operationDao,
                publicProfileDao,
                keysRepository,
                userRepository,
                feeWindowRepository,
                exchangeRateWindowRepository,
                transactionSizeRepository,
                houstonClient,
                notificationService,
                clipboardProvider,
                asyncActionStore
        ));
    }

    @Ignore
    @Test
    public void fetchReplaceOperations() {

        final List<Operation> remote = Fixture.from(Operation.class).gimme(2, ALL_LABELS);

        doReturn(Observable.just(remote))
                .when(houstonClient).fetchOperations();

        doReturn(Observable.just(0))
                .when(operationDao).deleteAll();

        doReturn(Observable.just(null))
                .when(operationActions).onRemoteOperationCreated(any(),
                transactionSizeRepository.getNextTransactionSize());

        fetchItemFromObservable(operationActions.fetchReplaceOperations());

        verify(operationActions, never())
                .onRemoteOperationUpdated(anyLong(), anyLong(), anyString(), any());

        verify(operationActions, times(remote.size()))
                .onRemoteOperationCreated(argThat(remote::contains),
                        transactionSizeRepository.getNextTransactionSize());
    }

    @Ignore
    @Test
    public void buildPaymentToContact() {

        final User user = Fixture.from(User.class).gimme("valid");
        final FeeWindow fees = Fixture.from(FeeWindow.class).gimme("valid");
        final ExchangeRateWindow rates = Fixture.from(ExchangeRateWindow.class).gimme("valid");

        final Contact contact = Fixture.from(Contact.class).gimme("valid");
        final PublicKey publicKey = contact.publicKey;
        final MuunAddress contactAddress = new MuunAddress(
                TemplateHelpers.address().generateValue(),
                publicKey.getAbsoluteDerivationPath() + "/" + publicKey.getLastLevelIndex()
        );

        final long someFee = 123456;

        final PaymentRequest payReq = PaymentRequest.toContact(
                contact,
                TemplateHelpers.money().generateValue(),
                "some description"
        );

        doReturn(contact.publicProfile)
                .when(publicProfileDao).fetchOneByHid(contact.hid);

        doReturn(contactAddress)
                .when(contactActions).getAddressForContact(contact);

        doReturn(user).when(userRepository).fetchOne();
        doReturn(rates).when(exchangeRateWindowRepository).fetchOne();
        doReturn(someFee).when(operationActions).getFeeForAmount(anyLong());

        final Operation operation =
                operationActions.buildOperationFromDraft(payReq);

        // check direction
        assertThat(operation.direction).isEqualTo(OperationDirection.OUTGOING);
        assertThat(operation.isExternal).isFalse();
        assertThat(operation.senderIsExternal).isFalse();
        assertThat(operation.receiverIsExternal).isFalse();

        // check receiver address
        assertThat(operation.receiverAddress).isEqualTo(contactAddress.getAddress());
        assertThat(operation.receiverAddressDerivationPath).isEqualTo(
                contactAddress.getDerivationPath()
        );

        // check amount
        final ExchangeRateProvider provider = new ExchangeRateProvider(rates.rates);
        final MonetaryAmount inBtc = payReq.amount.with(provider.getCurrencyConversion("BTC"));
        final long inSatoshis = BitcoinUtils.bitcoinsToSatoshis(inBtc);

        assertThat(operation.amount.inInputCurrency).isEqualTo(payReq.amount);
        assertThat(operation.amount.inSatoshis).isEqualTo(inSatoshis);

        // check fee
        assertThat(operation.fee.inSatoshis).isEqualTo(someFee);
    }

    @Ignore
    @Test
    public void buildPaymentToAddress() {

        final User user = Fixture.from(User.class).gimme("valid");
        final FeeWindow fees = Fixture.from(FeeWindow.class).gimme("valid");
        final ExchangeRateWindow rates = Fixture.from(ExchangeRateWindow.class).gimme("valid");

        final PaymentRequest payReq = PaymentRequest.toAddress(
                TemplateHelpers.address().generateValue(),
                TemplateHelpers.money().generateValue(),
                "some description"
        );

        final long someFee = 123456;

        doReturn(user).when(userRepository).fetchOne();
        doReturn(rates).when(exchangeRateWindowRepository).fetchOne();
        doReturn(someFee).when(operationActions).getFeeForAmount(anyLong());

        final Operation operation =
                operationActions.buildOperationFromDraft(payReq);

        // check direction
        assertThat(operation.direction).isEqualTo(OperationDirection.OUTGOING);
        assertThat(operation.isExternal).isTrue();
        assertThat(operation.senderIsExternal).isFalse();
        assertThat(operation.receiverIsExternal).isTrue();

        // check receiver address
        assertThat(operation.receiverAddress).isEqualTo(payReq.address);
        assertThat(operation.receiverAddressDerivationPath).isEqualTo(null);

        // check amount
        final ExchangeRateProvider provider = new ExchangeRateProvider(rates.rates);
        final MonetaryAmount inBtc = payReq.amount.with(provider.getCurrencyConversion("BTC"));
        final long inSatoshis = BitcoinUtils.bitcoinsToSatoshis(inBtc);

        assertThat(operation.amount.inInputCurrency).isEqualTo(payReq.amount);
        assertThat(operation.amount.inSatoshis).isEqualTo(inSatoshis);

        // check fee
        assertThat(operation.fee.inSatoshis).isEqualTo(someFee);
    }

    @Ignore
    @Test
    public void submitNewOperation() {

        final PrivateKey privateKey = TemplateHelpers.privateKey().generateValue();

        final Operation operation = Fixture.from(Operation.class).gimme("outgoing internal");
        final Operation createdOperation = shallowCopy(operation);

        // On Apollo's side, before submitting, operation HID is null:
        operation.hid = null;

        final PartiallySignedTransaction unsignedTx = mock(PartiallySignedTransaction.class);

        final Pair<Operation, PartiallySignedTransaction> newOperationRes = new Pair<>(
                createdOperation,
                unsignedTx
        );

        final Transaction signedTx = mock(Transaction.class);
        final String hash = TemplateHelpers.hash256().generateValue();

        doReturn(hash)
                .when(signedTx).getHashAsString();

        doReturn(Observable.just(privateKey))
                .when(keysRepository).getBasePrivateKey();

        doReturn(Observable.just(newOperationRes))
                .when(houstonClient).newOperation(operation);

//        doReturn(signedTx)
//                .when(bitcoinActions).signTransaction(privateKey, unsignedTx);

        doReturn(Observable.just(null))
                .when(houstonClient).pushTransaction(signedTx, createdOperation.hid);

        doReturn(Observable.just(null))
                .when(operationActions).onRemoteOperationCreated(operation,
                transactionSizeRepository.getNextTransactionSize());

        fetchItemFromObservable(operationActions.submitOperation(operation));

        assertThat(operation.hid).isEqualTo(createdOperation.hid);
        assertThat(operation.hash).isEqualTo(hash);
        assertThat(operation.status).isEqualTo(OperationStatus.SIGNED);
    }

    private Operation shallowCopy(Operation source) {
        return new Operation(
                source.id,
                source.hid,
                source.direction,
                source.isExternal,
                source.senderProfile,
                source.senderIsExternal,
                source.receiverProfile,
                source.receiverIsExternal,
                source.receiverAddress,
                source.receiverAddressDerivationPath,
                source.amount,
                source.fee,
                source.confirmations,
                source.hash,
                source.description,
                source.status,
                source.creationDate,
                source.exchangeRateWindowHid
        );
    }
}