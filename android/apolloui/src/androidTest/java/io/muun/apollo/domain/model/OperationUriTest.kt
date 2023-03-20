package io.muun.apollo.domain.model

import io.muun.apollo.data.external.Globals
import io.muun.apollo.domain.BaseUnitTest
import io.muun.common.bitcoinj.NetworkParametersHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.doReturn

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 *
 * NOTE: this is an instrumented test as tested code makes use of Libwallet and we currently can't
 * run unit tests for code that uses Libwallet (a go mobile bindings limitation).
 * Also, for some reason, some tests cannot be run on its own, the second one throws an
 * UnsatisfiedLinkError when run alone. We still can run the whole suite without problems
 * (go figure).
 */
class OperationUriTest : BaseUnitTest() {

    private val legacyAddress = "3AXHY3dJU1z9YkU5o1GKiLomCcdySZhBnj"
    private val bech32Address = "BC1QSQP0D3TY8AAA8N9J8R0D2PF3G40VN4AS9TPWY3J9R3GK5K64VX6QWPAXH2"
    private val taprootAddress = "bc1ps3y85gxp3wxyezkcvsww0cylfng209t896fsxxw2c8r2tju0fajseqrglx"
    private val invoice = "lnbc340n1p0r3px4pp5xf6h65d75sjwfd0lg6x37238chfxa8j5e2txn2cu8rwhqll9s3s" +
        "qdzq2pshjmt9de6zqen0wgsrxdpqwp5hsetvwvsxzapqwdshgmmndp5hxtnsd3skxefwxqzjccqp2sp53uldkk4r" +
        "zafm00pv9sm7u57caycgn0qs52fxvkce96w4349x9pyqrzjqwryaup9lh50kkranzgcdnn2fgvx390wgj5jd07rw" +
        "r3vxeje0glc7z2kxuqqngcqqyqqqqlgqqqqqeqqjq9qy9qsqwahd0wsnyxl5sr0q25pd8atey34665wh780ftht9" +
        "prm5dzdd30nhg5m3wwe2wpw04mk4ps8dz9y0px00sn7u4hrnkkwdhe7ymgupz2qpjj9v5r"
    private val invoice100Sat = "lnbcrt1u1p3ljvqgpp58v7szdf8xzmcyfue60tqmzzhesf4t4uwa3h43k9gq4wte" +
        "46kdlxsdqqcqzpgxqyz5vqsp5pnacd7q7agjv9h4y6lmn5atula28a8k4rmv2zxd3exnswpqsu8xq9qyyssqzmr5" +
        "t40nl76sqdng5axwyvk9mft734xvy8v6c0qrk32s528tmd9rzkfcwytkujnmr75gq99wwnfrnny970xljq832hme" +
        "g58n2mcnp7qp0h7gsc"
    private val lnUrl = "LNURL1DP68GURN8GHJ7MTPD9HZUUM0W46XS7RRDPSKUEM99E3K7MF0V9CXJATC9A3XZMRPDE" +
        "3K2TMVDE6HYMP0V3JHGCTFD3EN7ERPW3SN67EZG9KK7ATWWS3R5VPWXQCRQVPSX5KZYJTYYGAR2DFSXYCRYTPZGD" +
        "HKGEFZ8G3XXEZCVFJ4XSMGD9VKW63Z05Z3HXU3"

    @Before
    fun setUp() {
        doReturn(NetworkParametersHelper.getNetworkParametersFromName("mainnet"))
            .`when`(Globals.INSTANCE).network
    }

    @Test
    fun fromString() {

        checkBitcoinUrisFor(legacyAddress)
        checkBitcoinUrisFor(bech32Address)
        checkBitcoinUrisFor("bc1q8j675rkzq3nn6vpg7quwhdh9q7drl8dzhh5rv9") // particular incident
        checkBitcoinUrisFor(taprootAddress)

        var uri = OperationUri.fromString(invoice)
        assertTrue(uri.isLn)
        assertEquals(invoice, uri.lnInvoice.get())

        uri = OperationUri.fromString("lightning:$invoice")
        assertTrue(uri.isLn)
        assertEquals(invoice, uri.lnInvoice.get())

        uri = OperationUri.fromString(lnUrl)
        assertFalse(uri.isLn)
        assertEquals(lnUrl, uri.lnUrl.get())

        uri = OperationUri.fromString("lightning:$lnUrl")
        assertFalse(uri.isLn)
        assertEquals(lnUrl, uri.lnUrl.get())
    }

    private fun checkBitcoinUrisFor(address: String) {
        var uri = OperationUri.fromString(address)
        assertTrue(uri.isBitcoin)
        assertEquals(address, uri.bitcoinAddress.get())

        uri = OperationUri.fromString("BITCOIN:$address")
        assertTrue(uri.isBitcoin)
        assertEquals(address, uri.bitcoinAddress.get())

        uri = OperationUri.fromString("BITCOIN:$address?")
        assertTrue(uri.isBitcoin)
        assertEquals(address, uri.bitcoinAddress.get())

        uri = OperationUri.fromString("BiTCoIN:$address?amount=1.2")
        assertTrue(uri.isBitcoin)
        assertEquals(address, uri.bitcoinAddress.get())
        assertEquals("1.2", uri.getParam(OperationUri.MUUN_AMOUNT).get())

        // test case with data from recent incident
        val fee = 8
        val rbf = false
        val uriStringWithCustomParams = "bitcoin:$address?fee=$fee&rbf=$rbf"

        uri = OperationUri.fromString(uriStringWithCustomParams)
        assertTrue(uri.isBitcoin)
        assertEquals(address, uri.bitcoinAddress.get())
        assertEquals(fee.toString(), uri.getParam("fee").get())
        assertEquals(rbf.toString(), uri.getParam("rbf").get())

        // btcUriWithAmountAndCurrencyAndBolt11InvoiceParam
        val unifiedQrUri = OperationUri.fromString(
            "muun:$address?amount=0.000001&currency=btc&lightning=$invoice100Sat"
        )
        assertEquals(address, unifiedQrUri.bitcoinAddress.get())
        assertEquals("0.000001", unifiedQrUri.getParam(OperationUri.MUUN_AMOUNT).get())
        assertEquals("btc", unifiedQrUri.getParam(OperationUri.MUUN_CURRENCY).get())
        assertEquals(invoice100Sat, unifiedQrUri.getParam(OperationUri.MUUN_LN_INVOICE).get())
    }

    @Test
    fun fromStringWithMuunUri() {

        checkMuunUrisFor(legacyAddress)
        checkMuunUrisFor(bech32Address)
        checkMuunUrisFor(taprootAddress)

        val lnUri = OperationUri.fromString("muun:$invoice")
        assertTrue(lnUri.isLn)
        assertEquals(invoice, lnUri.lnInvoice.get())

        val uri = OperationUri.fromString("muun:$lnUrl")
        assertFalse(uri.isLn)
        assertEquals(lnUrl, uri.lnUrl.get())
    }

    private fun checkMuunUrisFor(address: String) {
        val btcUriWithoutAmount = OperationUri.fromString("muun:$address")
        assertTrue(btcUriWithoutAmount.isBitcoin)
        assertEquals(address, btcUriWithoutAmount.bitcoinAddress.get())

        val btcUriEdgeCase = OperationUri.fromString("muun:$address?")
        assertTrue(btcUriEdgeCase.isBitcoin)
        assertEquals(address, btcUriEdgeCase.bitcoinAddress.get())

        val btcUriWithAmount = OperationUri.fromString("muun:$address?amount=1.2")
        assertTrue(btcUriWithAmount.isBitcoin)
        assertEquals(address, btcUriWithAmount.bitcoinAddress.get())
        assertEquals("1.2", btcUriWithAmount.getParam(OperationUri.MUUN_AMOUNT).get())

        var btcUriWithAmountAndCurrency = OperationUri.fromString(
            "muun:$address?amount=1.2&currency=BTC"
        )
        assertTrue(btcUriWithAmountAndCurrency.isBitcoin)
        assertEquals(address, btcUriWithAmountAndCurrency.bitcoinAddress.get())
        assertEquals("1.2", btcUriWithAmountAndCurrency.getParam(OperationUri.MUUN_AMOUNT).get())
        assertEquals("BTC", btcUriWithAmountAndCurrency.getParam(OperationUri.MUUN_CURRENCY).get())

        btcUriWithAmountAndCurrency = OperationUri.fromString(
            "muun:$address?amount=1.2&currency=btc"
        )
        assertTrue(btcUriWithAmountAndCurrency.isBitcoin)
        assertEquals(address, btcUriWithAmountAndCurrency.bitcoinAddress.get())
        assertEquals("1.2", btcUriWithAmountAndCurrency.getParam(OperationUri.MUUN_AMOUNT).get())
        assertEquals("btc", btcUriWithAmountAndCurrency.getParam(OperationUri.MUUN_CURRENCY).get())

        // btcUriWithAmountAndCurrencyAndBolt11InvoiceParam
        val unifiedQrUri = OperationUri.fromString(
            "muun:$address?amount=0.000001&currency=btc&lightning=$invoice100Sat"
        )
        assertEquals(address, unifiedQrUri.bitcoinAddress.get())
        assertEquals("0.000001", unifiedQrUri.getParam(OperationUri.MUUN_AMOUNT).get())
        assertEquals("btc", unifiedQrUri.getParam(OperationUri.MUUN_CURRENCY).get())
        assertEquals(invoice100Sat, unifiedQrUri.getParam(OperationUri.MUUN_LN_INVOICE).get())
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromMalformed1() {
        OperationUri.fromString("lighting:lightning:$invoice")
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromMalformed2() {
        OperationUri.fromString("lighting:muun:$invoice")
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromMalformed3() {
        OperationUri.fromString("lighting:lightning:$lnUrl")
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromMalformed4() {
        OperationUri.fromString("lighting:muun:$lnUrl")
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromMalformed5() {
        val invalidAddress = "bc1q8j675rkzq3nn6vpg7quwhdh9g7drl8dzhh5rv9" // 9g7 should be 9q7
        val fee = 8
        val rbf = false
        val uriString = "bitcoin:$invalidAddress?fee=$fee&rbf=$rbf"

        val uri = OperationUri.fromString(uriString)
        assertTrue(uri.isBitcoin)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromMalformed6() {
        val invalidAddress =
            "bc1ps0uwgdh35v9rh285nzlp9166gt20uynfut45ysupmrxcdfme5vqstsdcsm" // tsdcsm should be nakt5g
        val fee = 8
        val rbf = false
        val uriString = "bitcoin:$invalidAddress?fee=$fee&rbf=$rbf"

        val uri = OperationUri.fromString(uriString)
        assertTrue(uri.isBitcoin)
    }

    // TODO: What about bitcoin:<LNURL> or muun:<LNURL>??
    // TODO: What about muun:<bitcoinUri>? E.g muun:bitcoin:<address>
}