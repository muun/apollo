package io.muun.apollo.domain.libwallet;

import io.muun.apollo.domain.libwallet.errors.LibwalletMismatchAddressError;
import io.muun.apollo.domain.libwallet.errors.LibwalletMismatchBitcoinUriError;
import io.muun.apollo.domain.libwallet.errors.LibwalletMismatchInvoiceError;
import io.muun.apollo.domain.libwallet.errors.LibwalletMismatchSignatureError;
import io.muun.apollo.domain.libwallet.errors.LibwalletVerificationError;
import io.muun.apollo.domain.model.BitcoinUriContent;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.external.Globals;
import io.muun.common.Optional;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.MuunInput;
import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.crypto.schemes.TransactionSchemeV1;
import io.muun.common.crypto.schemes.TransactionSchemeV2;
import io.muun.common.crypto.schemes.TransactionSchemeV3;
import io.muun.common.crypto.schemes.TransactionSchemeV4;
import io.muun.common.crypto.tx.PartiallySignedTransaction;
import io.muun.common.utils.BitcoinUtils;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.LnInvoice;
import io.muun.common.utils.Preconditions;

import libwallet.HDPrivateKey;
import libwallet.HDPublicKey;
import libwallet.Invoice;
import libwallet.Libwallet;
import libwallet.MuunPaymentURI;
import libwallet.Network;
import libwallet.SigningExpectations;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.javamoney.moneta.Money;
import org.threeten.bp.Instant;
import org.threeten.bp.ZonedDateTime;
import timber.log.Timber;

import java.util.Arrays;
import java.util.Objects;

public class LibwalletBridge {

    /**
     * Sign a partially signed transaction.
     */
    public static TransactionInfo sign(Operation userCraftedOp,
                                       PrivateKey userPrivateKey,
                                       PublicKey muunPublicKey,
                                       PartiallySignedTransaction pst,
                                       NetworkParameters network) {

        final byte[] unsignedTx = pst.getTransaction().bitcoinSerialize();

        // Create and attach signatures:
        pst.addUserSignatures(userPrivateKey, muunPublicKey);

        // Obtain TransactionInfo:
        final Transaction signedBitcoinjTx = pst.getTransaction();

        final TransactionInfo txInfo = new TransactionInfo(
                signedBitcoinjTx.getHashAsString(),
                signedBitcoinjTx.unsafeBitcoinSerialize()
        );

        // Verify Libwallet approves and produces the same signed transaction as Bitcoinj:
        tryLibwalletSigning(
                userCraftedOp,
                unsignedTx,
                pst,
                txInfo,
                userPrivateKey,
                muunPublicKey,
                network);

        return txInfo;
    }

    /**
     * Create a V1 MuunAddress.
     */
    public static MuunAddress createAddressV1(PublicKey pubKey, NetworkParameters params) {

        final MuunAddress address = TransactionSchemeV1.createAddress(pubKey);

        final HDPublicKey userKey = toLibwalletModel(pubKey, params);

        final libwallet.MuunAddress addressV1 = createAddressV1(userKey);

        if (addressV1 != null && !address.getAddress().equals(addressV1.address())) {
            Timber.e(new LibwalletMismatchAddressError(address.getAddress(), addressV1.address()));
        }

        return address;
    }

    private static libwallet.MuunAddress createAddressV1(HDPublicKey userKey) {
        try {
            return Libwallet.createAddressV1(userKey);

        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }

    /**
     * Create a V2 MuunAddress.
     */
    public static MuunAddress createAddressV2(PublicKeyPair pubKeyPair, NetworkParameters params) {

        final MuunAddress address = TransactionSchemeV2.createAddress(pubKeyPair, params);

        final HDPublicKey userKey = toLibwalletModel(pubKeyPair.getUserPublicKey(), params);
        final HDPublicKey muunKey = toLibwalletModel(pubKeyPair.getMuunPublicKey(), params);

        final libwallet.MuunAddress addressV2 = createAddressV2(userKey, muunKey);

        if (addressV2 != null && !address.getAddress().equals(addressV2.address())) {
            Timber.e(new LibwalletMismatchAddressError(address.getAddress(), addressV2.address()));
        }

        return address;
    }

    private static libwallet.MuunAddress createAddressV2(HDPublicKey userKey, HDPublicKey muunKey) {
        try {
            return Libwallet.createAddressV2(userKey, muunKey);

        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }

    /**
     * Create a V3 MuunAddress.
     */
    public static MuunAddress createAddressV3(PublicKeyPair pubKeyPair, NetworkParameters params) {

        final MuunAddress address = TransactionSchemeV3.createAddress(pubKeyPair, params);

        final HDPublicKey userKey = toLibwalletModel(pubKeyPair.getUserPublicKey(), params);
        final HDPublicKey muunKey = toLibwalletModel(pubKeyPair.getMuunPublicKey(), params);

        final libwallet.MuunAddress addressV3 = createAddressV3(userKey, muunKey);

        if (addressV3 != null && !address.getAddress().equals(addressV3.address())) {
            Timber.e(new LibwalletMismatchAddressError(address.getAddress(), addressV3.address()));
        }

        return address;
    }

    private static libwallet.MuunAddress createAddressV3(HDPublicKey userKey, HDPublicKey muunKey) {
        try {
            return Libwallet.createAddressV3(userKey, muunKey);

        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }

    /**
     * Create a V4 MuunAddress.
     */
    public static MuunAddress createAddressV4(PublicKeyPair pubKeyPair, NetworkParameters params) {

        final MuunAddress address = TransactionSchemeV4.createAddress(pubKeyPair, params);

        final HDPublicKey userKey = toLibwalletModel(pubKeyPair.getUserPublicKey(), params);
        final HDPublicKey muunKey = toLibwalletModel(pubKeyPair.getMuunPublicKey(), params);

        final libwallet.MuunAddress addressV4 = createAddressV4(userKey, muunKey);

        if (addressV4 != null && !address.getAddress().equals(addressV4.address())) {
            Timber.e(new LibwalletMismatchAddressError(address.getAddress(), addressV4.address()));
        }

        return address;
    }

    private static libwallet.MuunAddress createAddressV4(HDPublicKey userKey, HDPublicKey muunKey) {
        try {
            return Libwallet.createAddressV4(userKey, muunKey);

        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }

    /**
     * Decode a LN Invoice.
     */
    public static LnInvoice decodeInvoice(NetworkParameters params, String bech32Invoice) {
        final LnInvoice invoice = LnInvoice.decode(params, bech32Invoice);

        final Invoice libwalletInvoice = parseInvoice(params, bech32Invoice);

        checkEquals(invoice, libwalletInvoice);

        return invoice;
    }

    private static Invoice parseInvoice(NetworkParameters params, String bech32Invoice) {
        try {
            return Libwallet.parseInvoice(bech32Invoice, toLibwalletModel(params));

        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }

    private static void checkEquals(LnInvoice invoice, Invoice libwalletInvoice) {

        if (!invoice.original.equals(libwalletInvoice.getRawInvoice())) {
            Timber.e(
                    new LibwalletMismatchInvoiceError(
                            "rawInvoice",
                            invoice.original,
                            libwalletInvoice.getRawInvoice()
                    )
            );
        }

        final String destinationHex = Encodings.bytesToHex(libwalletInvoice.getDestination());
        if (!invoice.destinationPubKey.equals(destinationHex)) {
            Timber.e(
                    new LibwalletMismatchInvoiceError(
                            "destination",
                            invoice.destinationPubKey,
                            destinationHex
                    )
            );
        }

        if (!invoice.description.equals(libwalletInvoice.getDescription())) {
            Timber.e(
                    new LibwalletMismatchInvoiceError(
                            "description",
                            invoice.description,
                            libwalletInvoice.getDescription()
                    )
            );
        }


        final ZonedDateTime expirationTime = invoice.getExpirationTime();
        final ZonedDateTime libwalletExpirationTime = ZonedDateTime.ofInstant(
                Instant.ofEpochSecond(libwalletInvoice.getExpiry()), expirationTime.getZone()
        );

        if (!expirationTime.equals(libwalletExpirationTime)) {
            Timber.e(
                    new LibwalletMismatchInvoiceError(
                            "expirationTime",
                            expirationTime,
                            libwalletExpirationTime
                    )
            );
        }

        if (!invoice.amount.amountWithMillis.equals(libwalletInvoice.getMilliSat())) {
            Timber.e(
                    new LibwalletMismatchInvoiceError(
                            "amountWithMillis",
                            invoice.amount.amountWithMillis,
                            libwalletInvoice.getMilliSat()
                    )
            );
        }

        final String libwalletPaymentHash = Encodings.bytesToHex(libwalletInvoice.getPaymentHash());
        if (!invoice.id.equals(libwalletPaymentHash)) {
            Timber.e(
                    new LibwalletMismatchInvoiceError(
                            "paymentHash",
                            invoice.id,
                            libwalletPaymentHash
                    )
            );
        }
    }

    /**
     * Parse a bitcoin uri, handling bip72 uris.
     */
    public static void checkBitcoinUriContent(OperationUri uri,
                                            BitcoinUriContent expected) {

        final BitcoinUriContent bitcoinUriContent = getBitcoinUriContent(uri);

        if (!expected.address.equals(bitcoinUriContent.address)) {
            Timber.e(new LibwalletMismatchBitcoinUriError(
                    "address",
                    expected.address,
                    bitcoinUriContent.address
            ));
        }

        if (!Objects.equals(expected.amountInStatoshis, bitcoinUriContent.amountInStatoshis)) {
            Timber.e(new LibwalletMismatchBitcoinUriError(
                    "amountInStatoshis",
                    expected.amountInStatoshis,
                    bitcoinUriContent.amountInStatoshis
            ));
        }

        if (!Objects.equals(expected.memo, bitcoinUriContent.memo)) {
            Timber.e(new LibwalletMismatchBitcoinUriError(
                    "memo",
                    expected.memo,
                    bitcoinUriContent.memo
            ));
        }

        if (!Objects.equals(expected.merchant, bitcoinUriContent.merchant)) {
            Timber.e(new LibwalletMismatchBitcoinUriError(
                    "merchant",
                    expected.merchant,
                    bitcoinUriContent.merchant
            ));
        }
    }

    private static BitcoinUriContent getBitcoinUriContent(OperationUri uri) {
        final Network network = toLibwalletModel(Globals.INSTANCE.getNetwork());

        final MuunPaymentURI paymentUri = getPaymentUri(uri, network);

        if (paymentUri != null && !paymentUri.getBIP70Url().isEmpty()) {

            final MuunPaymentURI bip70PaymentUri = doPaymentRequestCall(uri, network);
            return fromLibwalletModel(bip70PaymentUri);
        }

        return fromLibwalletModel(paymentUri);
    }

    private static MuunPaymentURI getPaymentUri(OperationUri uri, Network network) {
        try {
            return Libwallet.getPaymentURI(uri.toString(), network);

        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }

    private static MuunPaymentURI doPaymentRequestCall(OperationUri uri, Network network) {
        try {
            return Libwallet.doPaymentRequestCall(uri.toString(), network);

        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }

    private static BitcoinUriContent fromLibwalletModel(MuunPaymentURI muunPaymentUri) {
        if (muunPaymentUri == null) {
            return null;
        }

        final Optional<Long> maybeAmount = Optional.ifNotEmpty(muunPaymentUri.getAmount())
                .map(Double::parseDouble)
                .map(it -> Money.of(it, "BTC"))
                .map(BitcoinUtils::bitcoinsToSatoshis);

        return new BitcoinUriContent(
                muunPaymentUri.getAddress(),
                maybeAmount.orElse(null),
                muunPaymentUri.getMessage().isEmpty() ? null : muunPaymentUri.getMessage(),
                muunPaymentUri.getLabel().isEmpty() ? null : muunPaymentUri.getLabel()
        );
    }

    private static HDPublicKey toLibwalletModel(PublicKey pubKey, NetworkParameters params) {
        return new HDPublicKey(
                pubKey.serializeBase58(),
                pubKey.getAbsoluteDerivationPath(),
                toLibwalletModel(params)
        );
    }

    private static HDPrivateKey toLibwalletModel(PrivateKey privKey, NetworkParameters params) {
        return new HDPrivateKey(
                privKey.serializeBase58(),
                privKey.getAbsoluteDerivationPath(),
                toLibwalletModel(params)
        );
    }

    private static libwallet.MuunAddress toLibwalletModel(MuunAddress address) {
        return new libwallet.MuunAddress() {
            public String address() {
                return address.getAddress();
            }

            public String derivationPath() {
                return address.getDerivationPath();
            }

            public long version() {
                return address.getVersion();
            }
        };
    }

    private static Network toLibwalletModel(NetworkParameters networkParameters) {
        if (NetworkParameters.ID_MAINNET.equals(networkParameters.getId())) {
            return Libwallet.mainnet();

        } else if (NetworkParameters.ID_REGTEST.equals(networkParameters.getId())) {
            return Libwallet.regtest();

        } else {
            return Libwallet.testnet();
        }
    }

    private static void tryLibwalletSigning(Operation op,
                                            byte[] unsignedTx,
                                            PartiallySignedTransaction pst,
                                            TransactionInfo expectedSignedTx,
                                            PrivateKey baseUserPrivateKey,
                                            PublicKey baseMuunPublicKey,
                                            NetworkParameters network) {

        // Extract and convert model objects:
        final String expectedTxId = expectedSignedTx.getHash();
        final byte[] expectedTxBytes = expectedSignedTx.getBytes();

        final HDPrivateKey userKey = toLibwalletModel(baseUserPrivateKey, network);
        final HDPublicKey muunKey = toLibwalletModel(baseMuunPublicKey, network);

        final libwallet.PartiallySignedTransaction libwalletPst =
                new libwallet.PartiallySignedTransaction(Encodings.bytesToHex(unsignedTx));

        for (final MuunInput input: pst.getInputs()) {
            libwalletPst.addInput(new Input(input));
        }

        // Attempt client-side verification (log-only for now):
        tryLibwalletVerify(op, userKey.publicKey(), muunKey, libwalletPst);

        try {
            // Verify the signed transaction matches our previous serializations:
            Preconditions.checkState(Arrays.equals(
                    libwalletPst.sign(userKey, muunKey).getBytes(),
                    expectedTxBytes
            ));

        } catch (Throwable e) {
            Timber.e(new LibwalletMismatchSignatureError(expectedTxId, e));
        }
    }

    private static void tryLibwalletVerify(Operation userCraftedOp,
                                           HDPublicKey userPublicKey,
                                           HDPublicKey muunPublicKey,
                                           libwallet.PartiallySignedTransaction libwalletPst) {

        final MuunAddress changeAddress = userCraftedOp.changeAddress;

        final long outputAmount = userCraftedOp.swap != null
                ? userCraftedOp.swap.getFundingOutput().getOutputAmountInSatoshis()
                : userCraftedOp.amount.inSatoshis;

        try {
            final SigningExpectations expectations = new SigningExpectations(
                    userCraftedOp.receiverAddress,
                    outputAmount,
                    changeAddress == null ? null : toLibwalletModel(changeAddress),
                    userCraftedOp.fee.inSatoshis
            );

            libwalletPst.setExpectations(expectations);
            libwalletPst.verify(userPublicKey, muunPublicKey);

        } catch (Throwable error) {
            Timber.e(new LibwalletVerificationError(error));
        }
    }
}
