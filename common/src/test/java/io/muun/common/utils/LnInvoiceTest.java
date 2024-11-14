package io.muun.common.utils;


import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.model.BtcAmount;

import com.fasterxml.jackson.core.type.TypeReference;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.AbstractBitcoinNetParams;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Test cases taken from Alex Bosworth's Bolt-11 JS ref implementation and Bolt-11 rfc.
 * See:
 * https://github.com/lightningnetwork/lightning-rfc/blob/master/11-payment-encoding.md
 * https://github.com/alexbosworth/ln-service/blob/master/test/bolt11/test_parse_payment_request.js
 **/
public class LnInvoiceTest {

    private static final String BITCOIN_MAINNET = "bitcoin";
    private static final String BITCOIN_TESTNET = "testnet";
    private static final String BITCOIN_REGTEST = "regtest";


    private final List<LnInvoiceTestData> testData = TestUtils.loadJson(
            "ln-invoice-test-data.json",
            new TypeReference<List<LnInvoiceTestData>>() {}
    );

    @Test
    public void testLnInvoiceDecode() {

        for (LnInvoiceTestData invoiceTestData : testData) {

            final NetworkParameters params = getParams(invoiceTestData);
            final LnInvoice invoice = LnInvoice.decode(params, invoiceTestData.request);

            try {
                assertLnInvoice(invoice, invoiceTestData.expected);
            } catch (AssertionError e) {
                System.err.println("Failure at parsing invoice: " + invoiceTestData.request);
                throw e;
            }
        }
    }

    private AbstractBitcoinNetParams getParams(LnInvoiceTestData invoiceTestData) {
        final String network = invoiceTestData.expected.network;

        if (BITCOIN_MAINNET.equals(network)) {
            return MainNetParams.get();

        } else if (BITCOIN_TESTNET.equals(network)) {
            return TestNet3Params.get();

        } else if (BITCOIN_REGTEST.equals(network)) {
            return RegTestParams.get();

        } else {
            fail("Unknown network");
            return null;
        }
    }

    private void assertLnInvoice(LnInvoice invoice, LnInvoiceTestData.Values expected) {

        final List<String> chainAddresses = expected.chainAddresses;
        final String[] chainAddressesArray = chainAddresses != null
                ? chainAddresses.toArray(new String[chainAddresses.size()])
                : null;

        if (chainAddressesArray != null) {
            assertThat(invoice.addresses).containsExactlyInAnyOrder(chainAddressesArray);
        } else {
            assertThat(invoice.addresses).isNullOrEmpty();
        }

        assertThat(invoice.cltvDelta).isEqualTo(expected.cltvDelta);
        assertThat(invoice.createdAt).isEqualTo(expected.createdAt);

        if (expected.description != null) {
            assertThat(invoice.description).isEqualTo(expected.description);
        } else {
            assertThat(invoice.description).isNullOrEmpty();
        }

        assertThat(invoice.descriptionHash).isEqualTo(expected.descriptionHash);
        assertThat(invoice.destinationPubKey).isEqualTo(expected.destination);
        assertThat(invoice.expiresAt).isEqualTo(expected.expiresAt);
        assertThat(invoice.id).isEqualTo(expected.id);
        assertThat(invoice.isExpired).isEqualTo(expected.isExpired);

        if (expected.amountWithMillis != null) {
            assertThat(invoice.amount.amountWithMillis).isEqualTo(expected.amountWithMillis);
            assertThat(invoice.amount.amountInSatoshis).isEqualTo(expected.amountInSatoshis);
        }

        if (expected.paymentSecret != null) {
            assertThat(invoice.paymentSecret).isEqualTo(expected.paymentSecret);
        }

        // TODO assert routes
    }

    @Test
    public void testEncodeDecode() {
        final RegTestParams network = RegTestParams.get();
        Context.propagate(new Context(network));

        final PrivateKey identityKey = PrivateKey.getNewRootPrivateKey(network);
        final byte[] paymentHash = RandomGenerator.getBytes(32);
        final PublicKey publicNodeKey = PrivateKey.getNewRootPrivateKey(network).getPublicKey();

        final long shortChannelId = RandomGenerator.getLong();
        final String description = "Test!";
        final String rawInvoice = LnInvoice.encodeForTest(
                network,
                identityKey,
                paymentHash,
                BtcAmount.fromSats(1000),
                144,
                description,
                publicNodeKey.getPublicKeyBytes(),
                shortChannelId,
                1000,
                1000,
                (short) 9
        );

        final LnInvoice decoded = LnInvoice.decode(network, rawInvoice);

        assertEquals(1000, decoded.amount.amountInSatoshis);
        assertArrayEquals(paymentHash, Encodings.hexToBytes(decoded.id));
        assertEquals(144L, decoded.cltvDelta.longValue());
        assertEquals(description, decoded.description);
    }
}
