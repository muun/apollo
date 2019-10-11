package io.muun.common.crypto.hd;

import io.muun.common.Optional;
import io.muun.common.utils.Encodings;

import com.google.protobuf.ByteString;
import org.bitcoinj.crypto.EncryptedData;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.wallet.Protos;
import org.bouncycastle.crypto.params.KeyParameter;

public class KeyCrypter {

    private static final int SCRYPT_ITERATIONS = 512;

    private static final int SCRYPT_R = 8;

    private static final int SCRYPT_P = 1;

    public KeyCrypter() {
    }

    /**
     * Encrypts the key with a passphrase.
     *
     * @return The ASCII serialization of the encrypted key.
     */
    public String encrypt(PrivateKey key, String passphrase) {

        final KeyCrypterScrypt keyCrypterScrypt = getKeyCrypter(KeyCrypterScrypt.randomSalt());

        final KeyParameter keyParameter = keyCrypterScrypt.deriveKey(passphrase);

        final String base58Serialization = key.serializeBase58();
        final byte[] dataToEncrypt = Encodings.stringToBytes(base58Serialization);

        final EncryptedData encryptedData = keyCrypterScrypt.encrypt(dataToEncrypt, keyParameter);

        return serialize(
                encryptedData,
                keyCrypterScrypt.getScryptParameters(),
                key.getAbsoluteDerivationPath()
        );
    }

    /**
     * Decrypts a key.
     *
     * @param encryptedKey The ASCII serialization of the encrypted key.
     */
    public Optional<PrivateKey> decrypt(String encryptedKey, String passphrase) {

        final DeserializationResult deserializationResult = deserialize(encryptedKey);

        final KeyCrypterScrypt keyCrypterScrypt = new KeyCrypterScrypt(
                deserializationResult.parametersForKeyDerivation
        );

        final KeyParameter keyParameter = keyCrypterScrypt.deriveKey(passphrase);

        final byte[] decryptedData;

        try {
            decryptedData = keyCrypterScrypt.decrypt(
                    deserializationResult.encryptedData,
                    keyParameter
            );
        } catch (KeyCrypterException exception) {
            return Optional.empty();
        }

        final String base58Serialization = Encodings.bytesToString(decryptedData);

        final PrivateKey privateKey = PrivateKey.deserializeFromBase58(
                deserializationResult.absoluteDerivationPath,
                base58Serialization
        );

        return Optional.of(privateKey);
    }

    private KeyCrypterScrypt getKeyCrypter(byte[] salt) {

        final Protos.ScryptParameters scryptParameters = Protos.ScryptParameters.newBuilder()
                .setSalt(ByteString.copyFrom(salt))
                .setN(SCRYPT_ITERATIONS)
                .setP(SCRYPT_P)
                .setR(SCRYPT_R)
                .build();

        return new KeyCrypterScrypt(scryptParameters);
    }

    private String serialize(EncryptedData encryptedData,
                             Protos.ScryptParameters scryptParameters,
                             String absoluteDerivationPath) {

        final byte[] pathBytes = Encodings.stringToBytes(absoluteDerivationPath);

        return "v1"
                + ":" + scryptParameters.getN()
                + ":" + scryptParameters.getP()
                + ":" + scryptParameters.getR()
                + ":" + Encodings.bytesToHex(scryptParameters.getSalt().toByteArray())
                + ":" + Encodings.bytesToHex(encryptedData.initialisationVector)
                + ":" + Encodings.bytesToHex(encryptedData.encryptedBytes)
                + ":" + Encodings.bytesToHex(pathBytes);

    }

    private DeserializationResult deserialize(String serialization) {

        final String[] parts = serialization.split(":");

        if (parts.length != 8 || !parts[0].equals("v1")) {
            throw new IllegalArgumentException("Invalid serialization");
        }

        final byte[] saltBytes = Encodings.hexToBytes(parts[4]);
        final ByteString salt = ByteString.copyFrom(saltBytes);

        final long nParameter = Long.parseLong(parts[1]);
        final int pParameter = Integer.parseInt(parts[2]);
        final int rParameter = Integer.parseInt(parts[3]);

        final Protos.ScryptParameters scryptParameters = Protos.ScryptParameters.newBuilder()
                .setN(nParameter)
                .setP(pParameter)
                .setR(rParameter)
                .setSalt(salt)
                .build();

        final EncryptedData encryptedData = new EncryptedData(
                Encodings.hexToBytes(parts[5]),
                Encodings.hexToBytes(parts[6])
        );

        final byte[] pathBytes = Encodings.hexToBytes(parts[7]);
        final String path = Encodings.bytesToString(pathBytes);

        return new DeserializationResult(scryptParameters, encryptedData, path);
    }

    private static class DeserializationResult {

        public final Protos.ScryptParameters parametersForKeyDerivation;

        public final EncryptedData encryptedData;

        public final String absoluteDerivationPath;

        private DeserializationResult(Protos.ScryptParameters parametersForKeyDerivation,
                                      EncryptedData encryptedData,
                                      String absoluteDerivationPath) {
            this.encryptedData = encryptedData;
            this.parametersForKeyDerivation = parametersForKeyDerivation;
            this.absoluteDerivationPath = absoluteDerivationPath;
        }
    }

}
