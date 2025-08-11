package io.muun.common.crypto;

import io.muun.common.utils.Preconditions;
import io.muun.common.utils.internal.Base58;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MuunEncryptedPrivateKeyV2 implements MuunEncryptedPrivateKey {

    private static final byte VERSION_BYTE = 0x2;
    private static final int VERSION_BYTE_SIZE = 1;
    private static final int BIRTHDAY_BYTE_SIZE = 2;
    private static final int CYPHER_TEXT_SIZE = 64;
    private static final int MAX_TWO_BYTES = 0xFFFF;

    private final byte version;
    private final long birthday;
    private final byte[] ephemeralPublicKey;
    private final byte[] cypherText;
    private final byte[] recoveryCodeSalt;

    /**
     * Deserialize encrypted private key from base 58.
     */
    public static MuunEncryptedPrivateKeyV2 fromBase58(String serialization) {

        return fromBytes(Base58.decode(serialization));
    }

    /**
     * Deserialize encrypted private key from a byte array.
     */
    private static MuunEncryptedPrivateKeyV2 fromBytes(byte[] bytes) {

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

            Preconditions.checkArgument(version == VERSION_BYTE);

            return new MuunEncryptedPrivateKeyV2(
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
    public MuunEncryptedPrivateKeyV2(
            long birthday,
            byte[] ephemeralPublicKey,
            byte[] cypherText,
            byte[] recoveryCodeSalt) {

        Preconditions.checkArgument(birthday <= MAX_TWO_BYTES);
        Preconditions.checkArgument(ephemeralPublicKey.length == PUBLIC_KEY_SIZE);
        Preconditions.checkArgument(cypherText.length == CYPHER_TEXT_SIZE);

        this.version = VERSION_BYTE;
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

    @Override
    public byte[] getRecoveryCodeSalt() {
        return recoveryCodeSalt;
    }

    @Override
    public byte[] getEphemeralPublicKey() {
        return ephemeralPublicKey;
    }

    @Override
    public byte[] getCypherText() {
        return cypherText;
    }

    @Override
    public byte getVersion() {
        return version;
    }

    public long getBirthday() {
        return birthday;
    }
}
