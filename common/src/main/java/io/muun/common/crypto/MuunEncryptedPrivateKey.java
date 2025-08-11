package io.muun.common.crypto;

import io.muun.common.utils.Preconditions;
import io.muun.common.utils.internal.Base58;

public interface MuunEncryptedPrivateKey {
    int PUBLIC_KEY_SIZE = 33;

    enum Version {
        V2,
        V3;

        static Version fromEncryptedPrivateKey(String serializedPrivateKey) {
            final byte[] decodedBytes = Base58.decode(serializedPrivateKey);
            final int firstByte = decodedBytes[0];

            Preconditions.checkArgument(firstByte == 3 || firstByte == 2);

            if (firstByte == 3) {
                return V3;
            }

            return V2;
        }
    }

    String toBase58();

    byte[] getRecoveryCodeSalt();

    byte[] getEphemeralPublicKey();

    byte[] getCypherText();

    byte getVersion();

    /**
     * Factory MuunEncryptedPrivateKey abstracting the caller from the encryptedKey version.
     */
    @SuppressWarnings("checkstyle:MissingSwitchDefault") // We want to have a compiling error here
    // if a new value is added.
    static MuunEncryptedPrivateKey create(
            Version version,
            long birthday,
            byte[] ephemeralPublicKey,
            byte[] cypherText,
            byte[] recoveryCodeSalt
    ) {
        switch (version) {
            case V2:
                return new MuunEncryptedPrivateKeyV2(
                        birthday,
                        ephemeralPublicKey,
                        cypherText,
                        recoveryCodeSalt
                );
            case V3:
                return new MuunEncryptedPrivateKeyV3(
                        ephemeralPublicKey,
                        cypherText,
                        recoveryCodeSalt
                );
        }

        throw new IllegalStateException();
    }

    /**
     * Factory MuunEncryptedPrivateKey abstracting the caller from the encryptedKey version.
     */
    @SuppressWarnings("checkstyle:MissingSwitchDefault") // We want to have a compiling error here
    // if a new value is added.
    static MuunEncryptedPrivateKey fromBase58(
            String serialization
    ) {
        final Version version = Version.fromEncryptedPrivateKey(serialization);
        switch (version) {
            case V2:
                return MuunEncryptedPrivateKeyV2.fromBase58(serialization);
            case V3:
                return MuunEncryptedPrivateKeyV3.fromBase58(serialization);
        }

        throw new IllegalStateException();
    }
}
