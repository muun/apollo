package io.muun.apollo.domain.action;

import io.muun.apollo.BaseTest;
import io.muun.apollo.BuildConfig;
import io.muun.apollo.data.os.ClipboardProvider;
import io.muun.apollo.domain.model.BitcoinUriContent;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.protobuf.ByteString;
import org.bitcoin.protocols.payments.Protos;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Date;
import java.util.concurrent.Executors;

import static io.muun.apollo.TestUtils.assertObservableThrows;
import static io.muun.apollo.TestUtils.fetchItemFromObservable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class BitcoinActionsTest extends BaseTest {

    private static final String ADDRESS = "1Hz96kJKF2HLPGY15JWLB5m9qGNxvt8tHJ";

    private static final int WIRE_MOCK_PORT = 9192;

    @Mock
    private ClipboardProvider clipboardProvider;

    @Mock
    private AddressActions addressActions;

    private BitcoinActions bitcoinActions;


    @Rule
    public WireMockRule wireMockRule = new WireMockRule(WIRE_MOCK_PORT);

    @Before
    public void setUp() {
        bitcoinActions = new BitcoinActions(
                clipboardProvider,
                addressActions,
                MainNetParams.get(),
                Executors.newSingleThreadExecutor()
        );
    }

    @Test
    public void copyAddressToClipboard() {

        doReturn(ADDRESS).when(addressActions).getExternalAddress();
        bitcoinActions.copyAddressToClipboard();
        verify(clipboardProvider).copy(anyString(), eq(ADDRESS));
    }

    @Test
    public void getBitcoinUriContent_bip21_basic() {
        final String uri = "bitcoin:" + ADDRESS;

        final BitcoinUriContent uriContent = fetchItemFromObservable(
                bitcoinActions.getBitcoinUriContent(uri)
        );

        assertThat(uriContent.address).isEqualTo(ADDRESS);
        assertThat(uriContent.amountInStatoshis).isNull();
        assertThat(uriContent.memo).isNull();
        assertThat(uriContent.merchant).isNull();
    }

    @Test
    public void getBitcoinUriContent_bip21_full() {
        final long amount = 1;
        final String memo = "hello";
        final String merchant = "bob";

        final String uri = "bitcoin:" + ADDRESS
                + "?amount=" + amount
                + "&message=" + memo
                + "&label=" + merchant
        ;

        final BitcoinUriContent uriContent = fetchItemFromObservable(
                bitcoinActions.getBitcoinUriContent(uri)
        );

        assertThat(uriContent.address).isEqualTo(ADDRESS);
        assertThat(uriContent.amountInStatoshis).isEqualTo(amount * 100000000);
        assertThat(uriContent.memo).isEqualTo(memo);
        assertThat(uriContent.merchant).isEqualTo(merchant);
    }

    @Test
    public void getBitcoinUriContent_bip21_error() {
        final String[] invalidUris = new String[] {
                "noscheme",
                "otherScheme:1234",
                "bitcoin:notAnAddress"
        };

        for (String invalidUri : invalidUris) {
            assertObservableThrows(bitcoinActions.getBitcoinUriContent(invalidUri));
        }
    }

    @Test
    @Ignore
    public void getBitcoinUriContent_bip72_basic() {
        final Protos.PaymentRequest paymentRequest = buildPaymentRequest(ADDRESS);

        final String uri = getBip72Uri(paymentRequest);

        final BitcoinUriContent uriContent = fetchItemFromObservable(
                bitcoinActions.getBitcoinUriContent(uri)
        );

        assertThat(uriContent.address).isEqualTo(ADDRESS);
        assertThat(uriContent.amountInStatoshis).isNull();
        assertThat(uriContent.memo).isNull();
        assertThat(uriContent.merchant).isNull();
    }

    @Test
    @Ignore
    public void getBitcoinUriContent_bip72_full() {
        final long amount = 12345;
        final String memo = "hello";

        final Protos.PaymentRequest paymentRequest = buildPaymentRequest(ADDRESS, amount, memo);
        final String uri = getBip72Uri(paymentRequest);

        final BitcoinUriContent uriContent = fetchItemFromObservable(
                bitcoinActions.getBitcoinUriContent(uri)
        );

        assertThat(uriContent.address).isEqualTo(ADDRESS);
        assertThat(uriContent.amountInStatoshis).isEqualTo(amount);
        assertThat(uriContent.memo).isEqualTo(memo);
        assertThat(uriContent.merchant).isNull(); // TODO test PKIVerificationData?
    }

    @Test
    @Ignore
    public void getBitcoinUriContent_bip72_error() {
        final Protos.PaymentRequest paymentRequest = buildPaymentRequest(ADDRESS);
        final String uri = getBip72Uri(paymentRequest);

        final String[] invalidUris = new String[] {
                "bitcoin:?r=invalid",
                "bitcoin:?r=http://invalid",
                getBip72Uri("invalid payment request".getBytes())
        };

        for (String invalidUri : invalidUris) {
            assertObservableThrows(bitcoinActions.getBitcoinUriContent(invalidUri));
        }
    }

    private Protos.PaymentRequest buildPaymentRequest(String address) {
        return buildPaymentRequest(address, null, null);
    }

    private Protos.PaymentRequest buildPaymentRequest(String address,
                                                      Long satoshis,
                                                      String memo) {

        final Protos.Output.Builder outputBuilder = Protos.Output.newBuilder()
                .setScript(buildOutputScript(address));

        if (satoshis != null) {
            outputBuilder.setAmount(satoshis);
        }

        final Protos.PaymentDetails.Builder detailsBuilder = Protos.PaymentDetails.newBuilder()
                .setTime(new Date().getTime())
                .addOutputs(outputBuilder);

        if (memo != null) {
            detailsBuilder.setMemo(memo);
        }

        return Protos.PaymentRequest.newBuilder()
                .setSerializedPaymentDetails(detailsBuilder.build().toByteString())
                .build();
    }

    private ByteString buildOutputScript(String addressString) {
        final Address address;

        try {
            address = new Address(null, addressString);
        } catch (AddressFormatException e) {
            throw new RuntimeException(e);
        }

        final Transaction tx = new Transaction(address.getParameters());
        tx.addOutput(Coin.COIN, address);

        return ByteString.copyFrom(tx.getOutput(0).getScriptBytes());
    }

    private String getBip72Uri(Protos.PaymentRequest paymentRequest) {
        return getBip72Uri(paymentRequest.toByteArray());
    }

    private String getBip72Uri(byte[] response) {
        final String path = "/pay/" + response.hashCode();
        final String mimeType = "application/bitcoin-paymentrequest";

        wireMockRule.stubFor(
                WireMock.get(WireMock.urlEqualTo(path))
                        .withHeader("Accept", WireMock.equalTo(mimeType))
                        .willReturn(
                                WireMock.aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", mimeType)
                                        .withBody(response)
                        )
        );

        return "bitcoin:?r=http://" + BuildConfig.TESTING_LOCAL_IP + ":" + WIRE_MOCK_PORT + path;
    }
}