package io.muun.apollo.domain.libwallet;

import io.muun.apollo.domain.errors.InvalidPaymentRequestError;
import io.muun.apollo.domain.libwallet.errors.AddressDerivationError;
import io.muun.apollo.domain.libwallet.errors.InvoiceParsingError;
import io.muun.apollo.domain.libwallet.errors.LibwalletEmergencyKitError;
import io.muun.apollo.domain.libwallet.errors.LibwalletSigningError;
import io.muun.apollo.domain.libwallet.errors.LibwalletVerificationError;
import io.muun.apollo.domain.libwallet.errors.PayloadDecryptError;
import io.muun.apollo.domain.libwallet.errors.PayloadEncryptError;
import io.muun.apollo.domain.model.BitcoinUriContent;
import io.muun.apollo.domain.model.GeneratedEmergencyKit;
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
import io.muun.common.utils.Preconditions;

import libwallet.Config;
import libwallet.EKInput;
import libwallet.EKOutput;
import libwallet.HDPrivateKey;
import libwallet.HDPublicKey;
import libwallet.Invoice;
import libwallet.Libwallet;
import libwallet.MuunPaymentURI;
import libwallet.Network;
import libwallet.SigningExpectations;
import libwallet.Transaction;
import org.bitcoinj.core.NetworkParameters;
import org.javamoney.moneta.Money;
import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;
import timber.log.Timber;

import java.util.Locale;

public class LibwalletBridge {

    /**
     * Initialize libwallet.
     */
    public static void init(String dataDir) {
        final Config config = new Config();
        config.setDataDir(dataDir);

        Libwallet.init(config);
    }

    /**
     * Generate an Emergency Kit containing the provided information.
     */
    public static GeneratedEmergencyKit generateEmergencyKit(String userKey,
                                                             String muunKey,
                                                             Locale locale) {

        final EKInput ekInput = new EKInput();
        ekInput.setFirstEncryptedKey(userKey);
        ekInput.setSecondEncryptedKey(muunKey);

        try {
            final EKOutput ekOutput = Libwallet
                    .generateTranslatedEmergencyKitHTML(ekInput, locale.getLanguage());

            return new GeneratedEmergencyKit(
                    ekOutput.getHTML(),
                    ekOutput.getVerificationCode()
            );

        } catch (Exception e) {
            throw new LibwalletEmergencyKitError(e);
        }
    }

    /**
     * Sign a partially signed transaction.
     */
    public static Transaction sign(Operation userCraftedOp,
                                   PrivateKey userPrivateKey,
                                   PublicKey muunPublicKey,
                                   PartiallySignedTransaction pst,
                                   NetworkParameters network) {

        final byte[] unsignedTx = pst.getTransaction().bitcoinSerialize();

        final HDPrivateKey userKey = toLibwalletModel(userPrivateKey, network);
        final HDPublicKey muunKey = toLibwalletModel(muunPublicKey, network);

        final String hexTx = Encodings.bytesToHex(unsignedTx);
        final libwallet.PartiallySignedTransaction libwalletPst =
                new libwallet.PartiallySignedTransaction(hexTx);

        for (final MuunInput input: pst.getInputs()) {
            libwalletPst.addInput(new Input(input));
        }

        // Attempt client-side verification (log-only for now):
        // We have some cases that aren't considered in libwallet yet, so keep this advisory
        tryLibwalletVerify(userCraftedOp, userKey.publicKey(), muunKey, libwalletPst);

        try {
            return libwalletPst.sign(userKey, muunKey);
        } catch (Exception e) {
            throw new LibwalletSigningError(hexTx, e);
        }
    }

    /**
     * Create a V1 MuunAddress.
     */
    public static MuunAddress createAddressV1(PublicKey pubKey, NetworkParameters params) {

        final HDPublicKey userKey = toLibwalletModel(pubKey, params);
        try {
            return fromLibwalletModel(Libwallet.createAddressV1(userKey));
        } catch (Exception e) {
            throw new AddressDerivationError(
                    TransactionSchemeV1.ADDRESS_VERSION, pubKey.getAbsoluteDerivationPath(), e);
        }
    }
    
    /**
     * Create a V2 MuunAddress.
     */
    public static MuunAddress createAddressV2(PublicKeyPair pubKeyPair, NetworkParameters params) {

        final HDPublicKey userKey = toLibwalletModel(pubKeyPair.getUserPublicKey(), params);
        final HDPublicKey muunKey = toLibwalletModel(pubKeyPair.getMuunPublicKey(), params);

        try {
            return fromLibwalletModel(Libwallet.createAddressV2(userKey, muunKey));
        } catch (Exception e) {
            throw new AddressDerivationError(
                    TransactionSchemeV2.ADDRESS_VERSION, pubKeyPair.getAbsoluteDerivationPath(), e);
        }
    }

    /**
     * Create a V3 MuunAddress.
     */
    public static MuunAddress createAddressV3(PublicKeyPair pubKeyPair, NetworkParameters params) {

        final HDPublicKey userKey = toLibwalletModel(pubKeyPair.getUserPublicKey(), params);
        final HDPublicKey muunKey = toLibwalletModel(pubKeyPair.getMuunPublicKey(), params);

        try {
            return fromLibwalletModel(Libwallet.createAddressV3(userKey, muunKey));
        } catch (Exception e) {
            throw new AddressDerivationError(
                    TransactionSchemeV3.ADDRESS_VERSION, pubKeyPair.getAbsoluteDerivationPath(), e);
        }
    }

    /**
     * Create a V4 MuunAddress.
     */
    public static MuunAddress createAddressV4(PublicKeyPair pubKeyPair, NetworkParameters params) {

        final HDPublicKey userKey = toLibwalletModel(pubKeyPair.getUserPublicKey(), params);
        final HDPublicKey muunKey = toLibwalletModel(pubKeyPair.getMuunPublicKey(), params);

        try {
            return fromLibwalletModel(Libwallet.createAddressV4(userKey, muunKey));
        } catch (Exception e) {
            throw new AddressDerivationError(
                    TransactionSchemeV4.ADDRESS_VERSION, pubKeyPair.getAbsoluteDerivationPath(), e);
        }
    }

    /**
     * Decode a LN Invoice.
     */
    public static io.muun.apollo.domain.libwallet.Invoice
            decodeInvoice(NetworkParameters params, String bech32Invoice) {

        final Invoice invoice = parseInvoice(params, bech32Invoice);
        return new io.muun.apollo.domain.libwallet.Invoice(
                bech32Invoice,
                invoice.getSats(),
                invoice.getDescription(),
                ZonedDateTime.ofInstant(Instant.ofEpochSecond(invoice.getExpiry()), ZoneId.of("Z")),
                Encodings.bytesToHex(invoice.getDestination())
        );
    }

    private static Invoice parseInvoice(NetworkParameters params, String bech32Invoice) {
        try {
            return Libwallet.parseInvoice(bech32Invoice, toLibwalletModel(params));

        } catch (Exception e) {
            throw new InvoiceParsingError(bech32Invoice, e);
        }
    }

    /**
     * Parse (and get) the contents of a bitcoin URI.
     */
    public static BitcoinUriContent getBitcoinUriContent(OperationUri uri)
            throws InvalidPaymentRequestError {
        final Network network = toLibwalletModel(Globals.INSTANCE.getNetwork());

        final MuunPaymentURI paymentUri = getPaymentUri(uri, network);

        if (paymentUri != null && !paymentUri.getBIP70Url().isEmpty()) {

            final MuunPaymentURI bip70PaymentUri = doPaymentRequestCall(paymentUri, network);
            return fromLibwalletModel(bip70PaymentUri);
        }

        return fromLibwalletModel(paymentUri);
    }

    private static MuunPaymentURI getPaymentUri(OperationUri uri, Network network)
            throws InvalidPaymentRequestError {
        try {
            return Libwallet.getPaymentURI(uri.toString(), network);

        } catch (Exception e) {
            throw new InvalidPaymentRequestError("Failed to parse URI", e);
        }
    }

    private static MuunPaymentURI doPaymentRequestCall(MuunPaymentURI uri, Network network)
            throws InvalidPaymentRequestError {
        try {
            return Libwallet.doPaymentRequestCall(uri.getBIP70Url(), network);

        } catch (Exception e) {
            throw new InvalidPaymentRequestError("Failed to parse URI", e);
        }
    }

    /**
     * Encrypt a payload for ourselves.
     */
    public static String encryptPayload(final PrivateKey userKey,
                                        final byte[] payload,
                                        final NetworkParameters network) {
        try {
            return toLibwalletModel(userKey, network)
                    .encrypter()
                    .encrypt(payload);
        } catch (Exception e) {
            throw new PayloadEncryptError(e);
        }
    }

    /**
     * Encrypt a payload for a peer.
     */
    public static String encryptPayloadToPeer(final PrivateKey userKey,
                                              final PublicKey peerKey,
                                              final byte[] payload,
                                              final NetworkParameters network) {

        try {
            return toLibwalletModel(userKey, network)
                    .encrypterTo(toLibwalletModel(peerKey, network))
                    .encrypt(payload);
        } catch (Exception e) {
            throw new PayloadEncryptError(e);
        }
    }

    /**
     * Decrypt a payload encrypted by us.
     */
    public static byte[] decryptPayload(final PrivateKey userKey,
                                        final String payload,
                                        final NetworkParameters network) {

        try {
            return toLibwalletModel(userKey, network)
                    .decrypter()
                    .decrypt(payload);
        } catch (Exception e) {
            throw new PayloadDecryptError(e);
        }
    }

    /**
     * Decrypt a payload from a peer.
     */
    public static byte[] decryptPayloadFromPeer(final PrivateKey userKey,
                                                final String payload,
                                                final NetworkParameters network) {

        try {
            // TODO: We should actually pass a peer key here
            return toLibwalletModel(userKey, network)
                    .decrypterFrom(null)
                    .decrypt(payload);
        } catch (Exception e) {
            throw new PayloadDecryptError(e);
        }
    }


    private static BitcoinUriContent fromLibwalletModel(MuunPaymentURI muunPaymentUri) {
        if (muunPaymentUri == null) {
            return null;
        }

        final Optional<Long> maybeAmount;
        if (muunPaymentUri.getBIP70Url() != null && !muunPaymentUri.getBIP70Url().isEmpty()) {

            // We suck and BIP70 returns amouns in sats, instead of BTC like the rest
            maybeAmount = Optional.of(Long.valueOf(muunPaymentUri.getAmount()));

        } else {

            maybeAmount = Optional.ifNotEmpty(muunPaymentUri.getAmount())
                    .map(Double::parseDouble)
                    .map(it -> Money.of(it, "BTC"))
                    .map(BitcoinUtils::bitcoinsToSatoshis);
        }

        return new BitcoinUriContent(
                muunPaymentUri.getAddress(),
                maybeAmount.orElse(null),
                muunPaymentUri.getMessage().isEmpty() ? null : muunPaymentUri.getMessage(),
                muunPaymentUri.getLabel().isEmpty() ? null : muunPaymentUri.getLabel()
        );
    }

    private static MuunAddress fromLibwalletModel(libwallet.MuunAddress address) {
        return new MuunAddress(
                (int) address.version(),
                address.derivationPath(),
                address.address()
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

    private static void tryLibwalletVerify(Operation userCraftedOp,
                                           HDPublicKey userPublicKey,
                                           HDPublicKey muunPublicKey,
                                           libwallet.PartiallySignedTransaction libwalletPst) {

        Preconditions.checkArgument(!userCraftedOp.isLendingSwap()); // no tx for LEND swaps

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
