package io.muun.common.crypto.agreement;

import io.muun.common.crypto.ChallengePrivateKey;
import io.muun.common.crypto.ChallengePublicKey;
import io.muun.common.crypto.Cryptography;
import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.internal.Base58;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

import java.security.Security;
import java.util.Random;

import javax.crypto.SecretKey;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MuunAsymmetricEncryptionTest {

    private static final byte[] SALT = Encodings.hexToBytes("ff2a201264a597b1");
    private static final byte[] PRIV_KEY_32 = byteArray(32);
    private static final byte[] CHAIN_CODE_32 = byteArray(32);

    private final ChallengePrivateKey
            passwordPrivateKey =
            ChallengePrivateKey.fromUserInput("This is a password !", SALT);

    private final ChallengePrivateKey
            recoveryCodePrivateKey =
            ChallengePrivateKey.fromUserInput("AAAABBBBCCCCDDDDEEEE", SALT);

    @Mock
    private PrivateKey muunKey;

    @Before
    public void setup() {
        Security.addProvider(new BouncyCastleProvider());
        Security.setProperty("crypto.policy", "unlimited");

        when(muunKey.getPrivKey32()).thenReturn(PRIV_KEY_32);
        when(muunKey.getChainCode()).thenReturn(CHAIN_CODE_32);
    }

    @Test
    public void tesCustomBinaryContainerCanBeSerializedAndDeserialized() {

        final byte[] message = byteArray(MuunSerializedMessage.CYPHER_TEXT_SIZE);
        final ChallengePublicKey publicKey = passwordPrivateKey.getChallengePublicKey();


        final MuunSerializedMessage container = MuunSerializedMessage.create(
                publicKey,
                message
        );

        final String encodedContainer = container.toBase58();

        final MuunSerializedMessage decodedContainer =
                MuunSerializedMessage.fromBase58(encodedContainer);

        final byte[] decodedPublicKey = decodedContainer.publicKey.toBytes();


        assertEquals(container.version, decodedContainer.version);
        assertArrayEquals(container.cypherText, decodedContainer.cypherText);
        assertArrayEquals(publicKey.toBytes(), decodedPublicKey);
    }

    @Test
    public void testSharedSecretCanBeGenerated() {
        final SecretKeyContainer secretKeyContainer =
                SharedSecretAgreement.generateSecretAesKeyFromPublicKeys(
                        passwordPrivateKey.getChallengePublicKey(),
                        recoveryCodePrivateKey.getChallengePublicKey()
                );

        final SecretKey secretAesDecryptKey = SharedSecretAgreement.extractSecretFromRemotePublic(
                secretKeyContainer.publicKey,
                passwordPrivateKey,
                recoveryCodePrivateKey
        );

        assertArrayEquals(
                secretKeyContainer.sharedSecretKey.getEncoded(),
                secretAesDecryptKey.getEncoded()
        );
    }

    @Test
    public void testMessageEncryptedWithASharedSecretCanBeDecrypted() throws Exception {

        // ENCRYPTION
        final SecretKeyContainer secretKeyContainer =
                SharedSecretAgreement.generateSecretAesKeyFromPublicKeys(
                        passwordPrivateKey.getChallengePublicKey(),
                        recoveryCodePrivateKey.getChallengePublicKey()
                );

        final byte[] cypherText = Cryptography.aesEncrypt(
                PRIV_KEY_32,
                secretKeyContainer.publicKey.generateIv(),
                secretKeyContainer.sharedSecretKey
        );

        final byte[] encodedRemotePublic = secretKeyContainer.publicKey.toBytes();

        // DECRYPTION

        final ChallengePublicKey decodedPublicKey = ChallengePublicKey
                .fromBytes(encodedRemotePublic);

        final SecretKey secretAesDecryptKey = SharedSecretAgreement.extractSecretFromRemotePublic(
                decodedPublicKey,
                passwordPrivateKey,
                recoveryCodePrivateKey
        );

        final byte[] decryptedText = Cryptography.aesDecrypt(
                cypherText,
                secretAesDecryptKey,
                decodedPublicKey.generateIv()
        );

        assertArrayEquals(decryptedText, PRIV_KEY_32);

    }

    @Test
    public void testEncryptedMessageShouldBe98BytesLong() {

        final String encryptedMessage = MuunAsymmetricEncryption.encryptWith(
                muunKey,
                passwordPrivateKey.getChallengePublicKey(),
                recoveryCodePrivateKey.getChallengePublicKey()
        );

        final int lengthInBytes = Base58.decode(encryptedMessage).length;

        assertEquals(98, lengthInBytes);
    }

    @Test
    public void testMessageEncryptedWithASharedSecretCanBeDecryptedFromCustomBinaryFormat() {

        final String encryptedMessage = MuunAsymmetricEncryption.encryptWith(
                muunKey,
                passwordPrivateKey.getChallengePublicKey(),
                recoveryCodePrivateKey.getChallengePublicKey()
        );

        final byte[] plainText = MuunAsymmetricEncryption.decryptFrom(
                encryptedMessage,
                passwordPrivateKey,
                recoveryCodePrivateKey
        );

        assertArrayEquals(ByteUtils.concatenate(PRIV_KEY_32, CHAIN_CODE_32), plainText);
    }

    private static byte[] byteArray(int length) {
        final StringBuilder builder = new StringBuilder();
        final Random random = new Random(123456L);

        for (int i = 0; i < length; i++) {
            builder.append(random.nextInt(9));
        }

        return ByteUtils.subArray( builder.toString().getBytes(), 0, length);
    }


}
