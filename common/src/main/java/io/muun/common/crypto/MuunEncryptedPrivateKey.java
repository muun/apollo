package io.muun.common.crypto;

import io.muun.common.utils.Preconditions;
import io.muun.common.utils.internal.Base58;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MuunEncryptedPrivateKey {

    public static final byte CURRENT_VERSION = 0x2;
    private static final int VERSION_BYTE_SIZE = 1;
    private static final int BIRTHDAY_BYTE_SIZE = 2;
    public static final int PUBLIC_KEY_SIZE = 33;
    private static final int CYPHER_TEXT_SIZE = 64;
    private static final int MAX_TWO_BYTES = 0xFFFF;

    public final byte version;
    public final long birthday;
    public final byte[] ephemeralPublicKey;
    public final byte[] cypherText;
    public final byte[] recoveryCodeSalt;

    /**
     * Deserialize encrypted private key from base 58.
     */
    public static MuunEncryptedPrivateKey fromBase58(String serialization) {

        return fromBytes(Base58.decode(serialization));
    }

    /**
     * Deserialize encrypted private key from a byte array.
     */
    public static MuunEncryptedPrivateKey fromBytes(byte[] bytes) {

        try {

            final ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);

            final byte version;
            final long birthday;
            final byte[] publicKey = new byte[PUBLIC_KEY_SIZE];
            final byte[] cypherText = new byte[CYPHER_TEXT_SIZE];

            version = buffer.get();
            birthday = buffer.getShort() & MAX_TWO_BYTES;
            buffer.get(publicKey);
            buffer.get(cypherText);

            final byte[] salt = new byte[buffer.remaining()];

            buffer.get(salt);

            return new MuunEncryptedPrivateKey(
                    version,
                    birthday,
                    publicKey,
                    cypherText,
                    salt
            );

        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Constructor.
     */
    public MuunEncryptedPrivateKey(
            byte version,
            long birthday,
            byte[] ephemeralPublicKey,
            byte[] cypherText,
            byte[] recoveryCodeSalt) {

        Preconditions.checkArgument(birthday <= MAX_TWO_BYTES);
        Preconditions.checkArgument(ephemeralPublicKey.length == PUBLIC_KEY_SIZE);
        Preconditions.checkArgument(cypherText.length == CYPHER_TEXT_SIZE);

        this.version = version;
        this.birthday = birthday;
        this.ephemeralPublicKey = ephemeralPublicKey;
        this.cypherText = cypherText;
        this.recoveryCodeSalt = recoveryCodeSalt;
    }

    /**
     * Serialized encrypted private key to a byte array.
     */
    public byte[] toBytes() {

        final int length =
                  VERSION_BYTE_SIZE
                + BIRTHDAY_BYTE_SIZE
                + ephemeralPublicKey.length
                + cypherText.length
                + recoveryCodeSalt.length;

        final ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.BIG_ENDIAN);

        buffer.put(version);
        buffer.putShort((short) birthday);
        buffer.put(ephemeralPublicKey);
        buffer.put(cypherText);
        buffer.put(recoveryCodeSalt);

        Preconditions.checkState(!buffer.hasRemaining());

        return buffer.array();
    }

    /**
     * Serialized encrypted private key to base 58.
     */
    public String toBase58() {
        return Base58.encode(toBytes());
    }
}
