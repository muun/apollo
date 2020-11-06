package io.muun.common.bitcoinj;

import io.muun.common.api.ChallengeSetupJson;
import io.muun.common.api.ChallengeSignatureJson;
import io.muun.common.crypto.ChallengePublicKey;
import io.muun.common.crypto.ChallengeType;
import io.muun.common.utils.Bech32SegwitAddress;
import io.muun.common.utils.Encodings;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.SignatureDecodeException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;

import java.util.UUID;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

public class ValidationHelpers {

    private static final Pattern derivationPathPattern = Pattern.compile("m(?:/\\d+['hHpP]?)*");

    // NOTE: emails are complicated beasts (https://stackoverflow.com/a/201378/469300).
    // We run a very simple validation, aimed at preventing common user mistakes. This expression
    // does NOT attempt to filter invalid emails in general.
    private static final Pattern emailPattern = Pattern
            .compile("[^@ ]+@[^@ ]+[.][^@ ]*[a-zA-Z0-9]$");

    /**
     * Check if an e-mail address is valid.
     */
    public static boolean isValidEmail(@Nullable String email) {
        return email != null && email.length() <= 128 && emailPattern.matcher(email).matches();
    }

    /**
     * Check if a Bitcoin address is valid.
     */
    public static boolean isValidAddress(NetworkParameters params, String address) {

        return isValidBase58Address(params, address) || isValidBech32Address(params, address);
    }

    /**
     * Check if a Bitcoin base58-encoded address is a valid.
     */
    public static boolean isValidBase58Address(NetworkParameters params, String address) {

        try {
            LegacyAddress.fromBase58(params, address);
            return true;

        } catch (AddressFormatException e) {
            return false;
        }
    }

    /**
     * Check if a Bitcoin bech32-encoded address is a valid.
     */
    public static boolean isValidBech32Address(NetworkParameters params, String address) {

        try {
            Bech32SegwitAddress.decode(params, address);
            return true;

        } catch (io.muun.common.exception.AddressFormatException e) {
            return false;
        }
    }

    /**
     * Check if a bip32 derivation path is valid.
     */
    public static boolean isValidDerivationPath(String path) {

        return derivationPathPattern.matcher(path).matches();
    }

    /**
     * Check if a raw transaction is valid. Notice that only the prefix of the raw data needs to be
     * a valid transaction.
     */
    public static boolean isValidTransaction(NetworkParameters params, byte[] transaction) {

        // TODO: make this fail on trailing bytes:
        //      Transaction tx = new Transaction(params, transaction);
        //      return transaction.length == tx.unsafeBitcoinSerialize().length;

        try {
            new Transaction(params, transaction);
            return true;
        } catch (ProtocolException e) {
            return false;
        }
    }

    /**
     * Check if an extended public key is valid.
     */
    public static boolean isValidBase58HdPublicKey(NetworkParameters params, String key) {

        try {
            DeterministicKey.deserializeB58(key, params);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Check if the string is a valid hex.
     */
    public static boolean isValidHex(String hex) {

        try {
            Encodings.hexToBytes(hex);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if the salt and public key in this ChallengeSetup are valid, and the ChallengeType
     * matches the expected type.
     */
    public static boolean isValidChallengeSetup(ChallengeSetupJson challengeSetup,
                                                ChallengeType challengeType) {

        return challengeSetup.type.equals(challengeType) && isValidChallengeSetup(challengeSetup);
    }

    /**
     * Check if the salt and public key in this ChallengeSetup are valid.
     */
    public static boolean isValidChallengeSetup(ChallengeSetupJson challengeSetup) {

        if (challengeSetup == null) {
            return false;
        }

        if (challengeSetup.type.encryptsPrivateKey && challengeSetup.encryptedPrivateKey == null) {
            return false;
        }

        if (challengeSetup.salt.length() != 16 || !isValidHex(challengeSetup.salt)) {
            return false;
        }

        try {
            final byte[] publicKey = Encodings.hexToBytes(challengeSetup.publicKey);
            final byte[] salt = Encodings.hexToBytes(challengeSetup.salt);
            new ChallengePublicKey(publicKey, salt, challengeSetup.version);

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if the ECDSA signature in this ChallengeSignature is valid in format.
     */
    public static boolean isValidChallengeSignature(ChallengeSignatureJson challengeSignature) {
        return isValidEcdsaSignature(challengeSignature.hex);
    }

    /**
     * Check if the object represents a valid uuid.
     */
    public static boolean isValidUuid(String uuid) {

        try {
            UUID.fromString(uuid);

            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Check if the given string represents a valid ECDSA signature represented as DER and encoded
     * in hex.
     */
    public static boolean isValidEcdsaSignature(String signature) {

        if (signature == null || signature.length() < 136 || signature.length() > 142) {

            return false;
        }

        try {
            final byte[] hexSignature = Encodings.hexToBytes(signature);

            ECKey.ECDSASignature.decodeFromDER(hexSignature);

            return true;
        } catch (RuntimeException | SignatureDecodeException e) {
            return false;
        }
    }
}
