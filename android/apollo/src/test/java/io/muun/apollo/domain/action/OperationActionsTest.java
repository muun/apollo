package io.muun.apollo.domain.action;

import io.muun.apollo.BaseTest;
import io.muun.apollo.data.db.operation.OperationDao;
import io.muun.apollo.data.db.public_profile.PublicProfileDao;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.domain.action.operation.CreateOperationAction;
import io.muun.apollo.domain.action.operation.OperationMetadataMapper;
import io.muun.apollo.domain.action.operation.SubmitPaymentAction;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.ExchangeRateWindow;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.PreparedPayment;
import io.muun.apollo.domain.model.tx.PartiallySignedTransaction;
import io.muun.apollo.template.TemplateHelpers;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.model.ExchangeRateProvider;
import io.muun.common.model.OperationDirection;
import io.muun.common.model.OperationStatus;
import io.muun.common.utils.BitcoinUtils;

import androidx.core.util.Pair;
import br.com.six2six.fixturefactory.Fixture;
import org.bitcoinj.core.Transaction;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import rx.Observable;

import java.util.List;
import javax.money.MonetaryAmount;

import static io.muun.apollo.TestUtils.fetchItemFromObservable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Ignore
public class OperationActionsTest extends BaseTest {

    private static final String[] ALL_LABELS = {"incoming internal", "outgoing internal"};

    @Mock
    private OperationDao operationDao;

    @Mock
    private PublicProfileDao publicProfileDao;

    @Mock
    private KeysRepository keysRepository;

    @Mock
    private ContactActions contactActions;

    @Mock
    private HoustonClient houstonClient;

    @Mock
    private CreateOperationAction createOperationAction;

    @Mock
    private SubmitPaymentAction submitPaymentAction;

    @Mock
    private OperationMetadataMapper operationMapper;

    private OperationActions operationActions;

    @Before
    public void setUp() {

        operationActions = spy(new OperationActions(
                createOperationAction,
                operationDao,
                houstonClient,
                operationMapper
        ));
    }

    @Test
    public void fetchReplaceOperations() {

        final List<Operation> remote = Fixture.from(Operation.class).gimme(2, ALL_LABELS);

        doReturn(Observable.just(remote))
                .when(houstonClient).fetchOperations();

        doReturn(Observable.just(0))
                .when(operationDao).deleteAll();

        fetchItemFromObservable(operationActions.fetchReplaceOperations());

        verify(createOperationAction, times(remote.size()))
                .saveOperation(argThat(remote::contains));
    }

    @Test
    public void buildPaymentToContact() {

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
                "some description",
                10.0
        );

        doReturn(contact.publicProfile)
                .when(publicProfileDao).fetchOneByHid(contact.getHid());

        doReturn(contactAddress)
                .when(contactActions).getAddressForContact(contact);

        final PreparedPayment preparedPayment = new PreparedPayment(null,
                null,
                null,
                null,
                null,
                payReq);
        final Operation operation =
                submitPaymentAction.buildOperation(preparedPayment);

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
        final ExchangeRateProvider provider = new ExchangeRateProvider(rates.toJson());
        final MonetaryAmount inBtc = payReq.getAmount().with(provider.getCurrencyConversion("BTC"));
        final long inSatoshis = BitcoinUtils.bitcoinsToSatoshis(inBtc);

        assertThat(operation.amount.inInputCurrency).isEqualTo(payReq.getAmount());
        assertThat(operation.amount.inSatoshis).isEqualTo(inSatoshis);

        // check fee
        assertThat(operation.fee.inSatoshis).isEqualTo(someFee);
    }

    @Test
    public void buildPaymentToAddress() {

        final ExchangeRateWindow rates = Fixture.from(ExchangeRateWindow.class).gimme("valid");

        final PaymentRequest payReq = PaymentRequest.toAddress(
                TemplateHelpers.address().generateValue(),
                TemplateHelpers.money().generateValue(),
                "some description",
                10.0
        );

        final long someFee = 123456;

        final PreparedPayment preparedPayment = new PreparedPayment(null,
                null,
                null,
                null,
                null,
                payReq);
        final Operation operation = submitPaymentAction.buildOperation(preparedPayment);

        // check direction
        assertThat(operation.direction).isEqualTo(OperationDirection.OUTGOING);
        assertThat(operation.isExternal).isTrue();
        assertThat(operation.senderIsExternal).isFalse();
        assertThat(operation.receiverIsExternal).isTrue();

        // check receiver address
        assertThat(operation.receiverAddress).isEqualTo(payReq.getAddress());
        assertThat(operation.receiverAddressDerivationPath).isEqualTo(null);

        // check amount
        final ExchangeRateProvider provider = new ExchangeRateProvider(rates.toJson());
        final MonetaryAmount inBtc = payReq.getAmount().with(provider.getCurrencyConversion("BTC"));
        final long inSatoshis = BitcoinUtils.bitcoinsToSatoshis(inBtc);

        assertThat(operation.amount.inInputCurrency).isEqualTo(payReq.getAmount());
        assertThat(operation.amount.inSatoshis).isEqualTo(inSatoshis);

        // check fee
        assertThat(operation.fee.inSatoshis).isEqualTo(someFee);
    }

    @Test
    public void submitNewOperation() {

        final PrivateKey privateKey = TemplateHelpers.privateKey().generateValue();

        final Operation operation = Fixture.from(Operation.class).gimme("outgoing internal");
        final Operation createdOperation = shallowCopy(operation);

        // On Apollo's side, before submitting, operation HID is NO_HID:
        // TODO we need Kotlin data classes and their copy method and optional params!!
        operation.mergeWithUpdate(new Operation(
                operation.getId(),
                Operation.NO_HID,
                operation.direction,
                operation.isExternal,
                operation.senderProfile,
                operation.senderIsExternal,
                operation.receiverProfile,
                operation.receiverIsExternal,
                operation.receiverAddress,
                operation.receiverAddressDerivationPath,
                operation.amount,
                operation.fee,
                operation.confirmations,
                operation.hash,
                operation.description,
                operation.metadata,
                operation.status,
                operation.creationDate,
                operation.exchangeRateWindowHid,
                operation.swap,
                operation.incomingSwap,
                operation.isRbf
        ));

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

//        doReturn(Observable.just(newOperationRes))
//                .when(houstonClient).newOperation(any(), any(), any());

//        doReturn(signedTx)
//                .when(bitcoinActions).signTransaction(privateKey, unsignedTx);

        doReturn(Observable.just(null))
                .when(houstonClient)
                .pushTransaction(signedTx.getHashAsString(), createdOperation.getHid());

        //doReturn(Observable.just(null))
        //        .when(operationActions).onRemoteOperationCreated(operation,
        //        transactionSizeRepository.getNextTransactionSize());

        //fetchItemFromObservable(operationActions.submitPayment(operation));

        assertThat(operation.getHid()).isEqualTo(createdOperation.getHid());
        assertThat(operation.hash).isEqualTo(hash);
        assertThat(operation.status).isEqualTo(OperationStatus.SIGNED);
    }

    private Operation shallowCopy(Operation source) {
        return new Operation(
                source.getId(),
                source.getHid(),
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
                source.metadata,
                source.status,
                source.creationDate,
                source.exchangeRateWindowHid,
                source.swap,
                source.incomingSwap,
                source.isRbf
        );
    }


}