package io.muun.apollo.domain.libwallet

import io.muun.common.crypto.hd.PrivateKey
import io.muun.common.crypto.hd.Schema
import libwallet.Libwallet
import org.bitcoinj.params.RegTestParams
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Instrumented test: the conversions call into Libwallet, which unit tests can't load.
 */
class ExtensionsTest {

    companion object {
        private const val PASSPHRASE = "correct horse battery staple"
    }

    private val network = RegTestParams.get()

    @Test
    fun shouldRestoreTheSameKeyAfterEncryptingAndDecryptingIt() {
        // GIVEN
        val baseKey = PrivateKey.getNewRootPrivateKey(network)
            .deriveFromAbsolutePath(Schema.getBasePath())

        // WHEN
        val encrypted = Libwallet.keyEncrypt(baseKey.toLibwallet(network), PASSPHRASE)
        val decrypted = Libwallet.keyDecrypt(encrypted, PASSPHRASE, network.toLibwallet())
        val restored = Extensions.fromLibwallet(decrypted.key)

        // THEN
        assertEquals(baseKey.serializeBase58(), restored.serializeBase58())
        assertEquals(baseKey.absoluteDerivationPath, restored.absoluteDerivationPath)
    }

    @Test
    fun shouldNotRevealTheKeyThroughToString() {
        // GIVEN
        val key = PrivateKey.getNewRootPrivateKey(network)
        val libwalletKey = key.toLibwallet(network)

        // WHEN
        val serialized = libwalletKey.base58()
        val printed = libwalletKey.toString()

        // THEN
        assertEquals(key.serializeBase58(), serialized)
        assertEquals("HDPrivateKey(<secret>)", printed)
    }
}
