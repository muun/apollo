package io.muun.apollo.domain.libwallet;

import io.muun.apollo.data.external.Globals;
import io.muun.apollo.domain.errors.InvalidPaymentRequestError;
import io.muun.apollo.domain.libwallet.errors.AddressDerivationError;
import io.muun.apollo.domain.libwallet.errors.LibwalletEmergencyKitError;
import io.muun.apollo.domain.libwallet.errors.LibwalletSigningError;
import io.muun.apollo.domain.libwallet.errors.LibwalletVerificationError;
import io.muun.apollo.domain.libwallet.errors.PayloadDecryptError;
import io.muun.apollo.domain.libwallet.errors.PayloadEncryptError;
import io.muun.apollo.domain.libwallet.model.Input;
import io.muun.apollo.domain.model.BitcoinUriContent;
import io.muun.apollo.domain.model.GeneratedEmergencyKit;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.tx.PartiallySignedTransaction;
import io.muun.common.Optional;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.MuunInput;
import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.utils.BitcoinUtils;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Preconditions;

import libwallet.Config;
import libwallet.EKInput;
import libwallet.EKOutput;
import libwallet.HDPrivateKey;
import libwallet.HDPublicKey;
import libwallet.Libwallet;
import libwallet.MusigNonces;
import libwallet.MuunPaymentURI;
import libwallet.Network;
import libwallet.SigningExpectations;
import libwallet.Transaction;
import org.bitcoinj.core.NetworkParameters;
import org.javamoney.moneta.Money;
import timber.log.Timber;

import java.util.Arrays;
import java.util.Locale;

// TODO: slowly start chopping down this class. See Extensions file and other models in this
//  (libwallet) package. Also, migrate it to Kotlin too.
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
                                                             String userFingerprint,
                                                             String muunKey,
                                                             String muunFingerprint,
                                                             Locale locale) {

        final EKInput ekInput = new EKInput();

        ekInput.setFirstEncryptedKey(userKey);
        ekInput.setFirstFingerprint(userFingerprint);

        ekInput.setSecondEncryptedKey(muunKey);
        ekInput.setSecondFingerprint(muunFingerprint);

        try {
            final EKOutput ekOutput = Libwallet
                    .generateEmergencyKitHTML(ekInput, locale.getLanguage());

            return new GeneratedEmergencyKit(
                    ekOutput.getHTML(),
                    ekOutput.getVerificationCode(),
                    ekOutput.getMetadata(),
                    (int) ekOutput.getVersion()
            );

        } catch (Exception e) {
            throw new LibwalletEmergencyKitError(e);
        }
    }

    /**
     * Sign a message. Use for challenge signing.
     */
    public static byte[] sign(byte[] message, PrivateKey userKey, NetworkParameters params) {
        try {
            return toLibwalletModel(userKey, params).sign(message);
        } catch (Exception e) {
            throw new LibwalletSigningError(Arrays.toString(message), e);
        }
    }

    /**
     * Sign a partially signed transaction.
     */
    public static Transaction sign(
            final Operation userCraftedOp,
            final PrivateKey userPrivateKey,
            final PublicKey muunPublicKey,
            final PartiallySignedTransaction pst,
            final NetworkParameters network,
            final MusigNonces musigNonces
    ) {

        final byte[] unsignedTx = pst.getTransaction().bitcoinSerialize();

        final HDPrivateKey userKey = toLibwalletModel(userPrivateKey, network);
        final HDPublicKey muunKey = toLibwalletModel(muunPublicKey, network);

        final libwallet.InputList inputList = new libwallet.InputList();
        for (final MuunInput input : pst.getInputs()) {
            inputList.add(new Input(input));
        }

        final libwallet.PartiallySignedTransaction libwalletPst =
                new libwallet.PartiallySignedTransaction(inputList, unsignedTx, musigNonces);

        // Attempt client-side verification (log-only for now):
        // We have some cases that aren't considered in libwallet yet, so keep this advisory
        tryLibwalletVerify(userCraftedOp, userKey.publicKey(), muunKey, libwalletPst);

        try {
            return libwalletPst.sign(userKey, muunKey);
        } catch (Exception e) {
            final String hexTx = Encodings.bytesToHex(unsignedTx);
            throw new LibwalletSigningError(hexTx, e);
        }
    }

    /**
     * Create a V1 (Legacy single sig aka P2PKH)  MuunAddress.
     */
    public static MuunAddress createAddressV1(PublicKey pubKey, NetworkParameters params) {

        final HDPublicKey userKey = toLibwalletModel(pubKey, params);
        try {
            return fromLibwalletModel(Libwallet.createAddressV1(userKey));
        } catch (Exception e) {
            throw new AddressDerivationError(
                    (int) Libwallet.AddressVersionV1,
                    pubKey.getAbsoluteDerivationPath(),
                    e
            );
        }
    }

    /**
     * Create a V2 (Legacy Multisig aka P2SH) MuunAddress.
     */
    public static MuunAddress createAddressV2(PublicKeyPair pubKeyPair, NetworkParameters params) {

        final HDPublicKey userKey = toLibwalletModel(pubKeyPair.getUserPublicKey(), params);
        final HDPublicKey muunKey = toLibwalletModel(pubKeyPair.getMuunPublicKey(), params);

        try {
            return fromLibwalletModel(Libwallet.createAddressV2(userKey, muunKey));
        } catch (Exception e) {
            throw new AddressDerivationError(
                    (int) Libwallet.AddressVersionV2,
                    pubKeyPair.getAbsoluteDerivationPath(),
                    e
            );
        }
    }

    /**
     * Create a V3 (Retro-compat Segwit Multisig aka P2SH-P2WSH) MuunAddress.
     */
    public static MuunAddress createAddressV3(PublicKeyPair pubKeyPair, NetworkParameters params) {

        final HDPublicKey userKey = toLibwalletModel(pubKeyPair.getUserPublicKey(), params);
        final HDPublicKey muunKey = toLibwalletModel(pubKeyPair.getMuunPublicKey(), params);

        try {
            return fromLibwalletModel(Libwallet.createAddressV3(userKey, muunKey));
        } catch (Exception e) {
            throw new AddressDerivationError(
                    (int) Libwallet.AddressVersionV3,
                    pubKeyPair.getAbsoluteDerivationPath(),
                    e
            );
        }
    }

    /**
     * Create a V4 (Native Segwit Multisig aka P2WSH) MuunAddress.
     */
    public static MuunAddress createAddressV4(PublicKeyPair pubKeyPair, NetworkParameters params) {

        final HDPublicKey userKey = toLibwalletModel(pubKeyPair.getUserPublicKey(), params);
        final HDPublicKey muunKey = toLibwalletModel(pubKeyPair.getMuunPublicKey(), params);

        try {
            return fromLibwalletModel(Libwallet.createAddressV4(userKey, muunKey));
        } catch (Exception e) {
            throw new AddressDerivationError(
                    (int) Libwallet.AddressVersionV4,
                    pubKeyPair.getAbsoluteDerivationPath(),
                    e
            );
        }
    }

    /**
     * Create a V5 (Taproot Multisig aka P2TR) MuunAddress.
     */
    public static MuunAddress createAddressV5(PublicKeyPair pubKeyPair, NetworkParameters params) {

        final HDPublicKey userKey = toLibwalletModel(pubKeyPair.getUserPublicKey(), params);
        final HDPublicKey muunKey = toLibwalletModel(pubKeyPair.getMuunPublicKey(), params);

        try {
            return fromLibwalletModel(Libwallet.createAddressV5(userKey, muunKey));
        } catch (Exception e) {
            throw new AddressDerivationError(
                    (int) Libwallet.AddressVersionV5,
                    pubKeyPair.getAbsoluteDerivationPath(),
                    e
            );
        }
    }

    /**
     * Parse (and get) the contents of a bitcoin URI.
     */
    public static BitcoinUriContent getBitcoinUriContent(OperationUri uri)
            throws InvalidPaymentRequestError {
        final Network network = toLibwalletModel(Globals.INSTANCE.getNetwork());

        final MuunPaymentURI paymentUri = getPaymentUri(uri, network);

        if (paymentUri != null && !paymentUri.getBip70Url().isEmpty()) {

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
            return Libwallet.doPaymentRequestCall(uri.getBip70Url(), network);

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

    /**
     * Map Java model to Libwallet (Go) Model.
     */
    private static MuunAddress fromLibwalletModel(libwallet.MuunAddress address) {
        return new MuunAddress(
                (int) address.version(),
                address.derivationPath(),
                address.address()
        );
    }

    /**
     * Map Java model to Libwallet (Go) Model.
     */
    private static HDPublicKey toLibwalletModel(PublicKey pubKey, NetworkParameters params) {
        return new HDPublicKey(
                pubKey.serializeBase58(),
                pubKey.getAbsoluteDerivationPath(),
                toLibwalletModel(params)
        );
    }

    /**
     * Map Java model to Libwallet (Go) Model.
     */
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

    /**
     * Map Java model to Libwallet (Go) Model.
     */
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

            libwalletPst.verify(expectations, userPublicKey, muunPublicKey);

        } catch (Throwable error) {
            Timber.e(new LibwalletVerificationError(error));
        }
    }
}
