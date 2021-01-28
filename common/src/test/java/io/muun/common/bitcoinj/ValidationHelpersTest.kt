package io.muun.common.bitcoinj

import io.muun.common.api.ChallengeSetupJson
import io.muun.common.crypto.ChallengeType
import io.muun.common.utils.Encodings
import org.assertj.core.api.Assertions.assertThat
import org.bitcoinj.params.MainNetParams
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.net.URISyntaxException
import java.util.*

class ValidationHelpersTest {

    private val params = MainNetParams.get()

    private val BASE58_ADDR_1 = "1HB5XMLmzFVj8ALj6mfBsbifRoD4miY36v"
    private val BASE58_ADDR_2 = "39hFnGpcBR66YPps7ACGU8VQACzY4Drzwk"
    private val BECH32_ADDR_1 = "BC1QW508D6QEJXTDG4Y5R3ZARVARY0C5XW7KV8F3T4"
    private val BECH32_ADDR_2 = "bc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qccfmv3"

    private val INVALID_BASE58_ADDR_1 = "1HB5XMLmzFVj8ALj6mfBsbifRoD4miY3v"
    private val INVALID_BASE58_ADDR_2 = "39hFnGpcBR66YPps7ACU8VQACzY4Drzwkgggg"
    private val INVALID_BECH32_ADDR_1 = "BC1QW508D6QEJXTDG4Y5fR3RY0C5XW7KV8F3T4"
    private val INVALID_BECH32_ADDR_2 = "bc1qrp33g0q5c5txsp9arysrx4k6zdkfs4fdfnce4xj0gdcccefvpysxf3qccfmv3"

    @Test
    fun testIsValidExtendedPublicKey() {

        val key = "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFj" + "qJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet8"

        assertThat(ValidationHelpers.isValidBase58HdPublicKey(params, key))
            .isTrue()
    }

    @Test
    fun testIsValidDerivationPath() {

        assertThat(ValidationHelpers.isValidDerivationPath("m/0H/1/2H/2/1000000000")).isTrue()
        assertThat(ValidationHelpers.isValidDerivationPath("m/0'/1/2'/2/1000000000")).isTrue()
        assertThat(ValidationHelpers.isValidDerivationPath("m")).isTrue()
        assertThat(ValidationHelpers.isValidDerivationPath("m/")).isFalse()
    }

    @Test
    fun testIsValidAddress() {

        assertThat(ValidationHelpers.isValidAddress(params, BASE58_ADDR_1)).isTrue()
        assertThat(ValidationHelpers.isValidAddress(params, BASE58_ADDR_2)).isTrue()

        assertThat(ValidationHelpers.isValidAddress(params, BECH32_ADDR_1)).isTrue()
        assertThat(ValidationHelpers.isValidAddress(params, BECH32_ADDR_2)).isTrue()

        assertThat(ValidationHelpers.isValidAddress(params, INVALID_BASE58_ADDR_1)).isFalse()
        assertThat(ValidationHelpers.isValidAddress(params, INVALID_BASE58_ADDR_2)).isFalse()

        assertThat(ValidationHelpers.isValidAddress(params, INVALID_BECH32_ADDR_1)).isFalse()
        assertThat(ValidationHelpers.isValidAddress(params, INVALID_BECH32_ADDR_2)).isFalse()
    }

    @Test
    fun testIsValidBase58Address() {

        assertThat(ValidationHelpers.isValidBase58Address(params, BASE58_ADDR_1)).isTrue()
        assertThat(ValidationHelpers.isValidBase58Address(params, BASE58_ADDR_2)).isTrue()

        assertThat(ValidationHelpers.isValidBase58Address(params, INVALID_BASE58_ADDR_1)).isFalse()
        assertThat(ValidationHelpers.isValidBase58Address(params, INVALID_BASE58_ADDR_2)).isFalse()
    }

    @Test
    fun testIsValidBech32Address() {

        assertThat(ValidationHelpers.isValidBech32Address(params, BECH32_ADDR_1)).isTrue()
        assertThat(ValidationHelpers.isValidBech32Address(params, BECH32_ADDR_2)).isTrue()

        assertThat(ValidationHelpers.isValidBech32Address(params, INVALID_BECH32_ADDR_1)).isFalse()
        assertThat(ValidationHelpers.isValidBech32Address(params, INVALID_BECH32_ADDR_2)).isFalse()
    }

    @Test
    fun testIsValidHex() {
        assertThat(ValidationHelpers.isValidHex("0123456789abcdef")).isTrue()
        assertThat(ValidationHelpers.isValidHex("0x")).isFalse()
        assertThat(ValidationHelpers.isValidHex("012")).isFalse()
    }

    @Test
    fun testIsValidChallengeSetup() {
        assertThat(ValidationHelpers.isValidChallengeSetup(null)).isFalse()
        assertThat(ValidationHelpers.isValidChallengeSetup(ChallengeSetupJson())).isFalse()
        assertThat(
            ValidationHelpers.isValidChallengeSetup(
                ChallengeSetupJson(ChallengeType.PASSWORD, "", "", "", 1)
            )
        ).isFalse()
        assertThat(
            ValidationHelpers.isValidChallengeSetup(
                ChallengeSetupJson(ChallengeType.PASSWORD, "", "0x", "", 1)
            )
        ).isFalse()
        assertThat(
            ValidationHelpers.isValidChallengeSetup(
                ChallengeSetupJson(ChallengeType.PASSWORD, "", "0123456789012345", "", 1)
            )
        ).isFalse()
    }

    @Test
    fun testIsValidUuid() {
        assertThat(ValidationHelpers.isValidUuid("50c25fa4-b43f-44fc-a7d4-893e972e9ffc")).isTrue()
        assertThat(ValidationHelpers.isValidUuid("ABC")).isFalse()
    }

    @Test
    @Ignore("failing")
    @Throws(URISyntaxException::class, FileNotFoundException::class)
    fun testIsValidTransaction() {
        val file = resource("raw-transactions/tx-mainnet-segwit.txt")

        val `in` = Scanner(FileReader(file))
        val hex = `in`.nextLine().toLowerCase()

        val decode = Encodings.hexToBytes(hex)

        assertThat(ValidationHelpers.isValidTransaction(MainNetParams.get(), decode)).isTrue()
        assertThat(ValidationHelpers.isValidTransaction(MainNetParams.get(), ByteArray(32)))
            .isFalse()
    }

    @Test
    fun testIsValidEmail() {
        assertThat(ValidationHelpers.isValidEmail("a@b.c")).isTrue()
        assertThat(ValidationHelpers.isValidEmail("a123@b.c")).isTrue()
        assertThat(ValidationHelpers.isValidEmail("123@b.c")).isTrue()
        assertThat(ValidationHelpers.isValidEmail("a123@b.c.d")).isTrue()
        assertThat(ValidationHelpers.isValidEmail("123@b.c.d")).isTrue()
        assertThat(ValidationHelpers.isValidEmail("123+11@b.c.d")).isTrue()

        assertThat(ValidationHelpers.isValidEmail("a123@b.c.")).isFalse()
        assertThat(ValidationHelpers.isValidEmail("a@b.")).isFalse()
        assertThat(ValidationHelpers.isValidEmail("a@b@d.")).isFalse()
        assertThat(ValidationHelpers.isValidEmail("a@b@c.d")).isFalse()
        assertThat(ValidationHelpers.isValidEmail("a+12@b@c.d")).isFalse()
        assertThat(ValidationHelpers.isValidEmail("a+12@b@c+.d")).isFalse()
        assertThat(ValidationHelpers.isValidEmail("a@.b")).isFalse()
    }

    @Throws(URISyntaxException::class)
    private fun resource(path: String): File {

        val resource = javaClass.classLoader.getResource("io/muun/common/$path")

        return if (resource != null) {
            File(resource.toURI())
        } else File(
            javaClass.classLoader.getResource(".")!!.path
                .replace("/build/classes/test/", "/build/resources/test/io/muun/common/$path")
        )

        // Hack to load test resources when executing tests from Android Studio
    }
}
