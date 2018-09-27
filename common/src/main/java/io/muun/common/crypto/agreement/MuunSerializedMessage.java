package io.muun.common.crypto.agreement;

import io.muun.common.crypto.ChallengePublicKey;
import io.muun.common.utils.internal.Base58;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MuunSerializedMessage {

    private static final byte MUUN_SERIALIZED_MESSAGE_VERSION = 0x1;

    // 32 private key + 32 chain code
    public static final int CYPHER_TEXT_SIZE = 64;
    private static final int PUBLIC_KEY_SIZE = 33;

    public final byte version;
    public final ChallengePublicKey publicKey;
    public final byte[] cypherText;

    private MuunSerializedMessage(byte version,
                                  ChallengePublicKey publicKey,
                                  byte[] encryptedPrivateKey) {
        this.version = version;
        this.publicKey = publicKey;
        this.cypherText = encryptedPrivateKey;
    }

    private byte[] getSerialized() {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            outputStream.write(version);
            outputStream.write(publicKey.toBytes());
            outputStream.write(cypherText);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return outputStream.toByteArray();

    }

    /**
     * @return Serialized message on Base58 encoding.
     */
    public String toBase58() {
        return Base58.encode(getSerialized());
    }

    /**
     * Creates a new `MuunSerializedMessage` instance.
     *
     * @param publicKey encoded.
     * @param cypherText i.e.: some private key + chain code.
     * @return a new `MuunSerializedMessage` instance.
     */
    public static MuunSerializedMessage create(ChallengePublicKey publicKey,
                                               byte[] cypherText) {
        return new MuunSerializedMessage(
                MUUN_SERIALIZED_MESSAGE_VERSION,
                publicKey,
                cypherText
        );
    }

    /**
     * Deserializes a muun message containing an encrypted private key.
     *
     * @param input bytes with the custom format defined on `MuunSerializedMessage`.
     * @return a `MuunSerializedMessage`.
     */
    public static MuunSerializedMessage from(byte[] input) {
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(input);

        final MuunSerializedMessage muunSerializedMessage = new MuunSerializedMessage(
                readNext(inputStream, 1)[0],
                ChallengePublicKey.fromBytes(readNext(inputStream, PUBLIC_KEY_SIZE)),
                readNext(inputStream, CYPHER_TEXT_SIZE)
        );

        try {
            inputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return muunSerializedMessage;
    }

    private static byte[] readNext(ByteArrayInputStream stream, int bytesToRead) {
        final byte[] result = new byte[bytesToRead];
        stream.read(result, 0, bytesToRead);
        return result;
    }

    /**
     * Decodes a byte array encoded on a Base58 string containing a custom binary format for
     * `MuunSerializedMessage`.
     *
     * @param encoded a serialized `MuunSerializedMessage`.
     * @return an decoded instance of `MuunSerializedMessage`.
     */
    public static MuunSerializedMessage fromBase58(String encoded) {
        final byte[] decoded = Base58.decode(encoded);
        return from(decoded);
    }
}
