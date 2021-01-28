package io.muun.apollo.domain.model

import io.muun.apollo.BaseTest
import io.muun.apollo.data.external.Globals
import io.muun.common.bitcoinj.NetworkParametersHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.doReturn

class OperationUriTest: BaseTest() {

    @Before
    fun setUp() {
        doReturn(NetworkParametersHelper.getNetworkParametersFromName("mainnet"))
                .`when`(Globals.INSTANCE).network

    }

    @Test
    fun fromString() {
        var uri: OperationUri

        uri = OperationUri.fromString(
                "BC1QSQP0D3TY8AAA8N9J8R0D2PF3G40VN4AS9TPWY3J9R3GK5K64VX6QWPAXH2")
        assertTrue(uri.isBitcoin)
        assertEquals("BC1QSQP0D3TY8AAA8N9J8R0D2PF3G40VN4AS9TPWY3J9R3GK5K64VX6QWPAXH2",
                uri.bitcoinAddress.get())

        uri = OperationUri.fromString(
                "BITCOIN:BC1QSQP0D3TY8AAA8N9J8R0D2PF3G40VN4AS9TPWY3J9R3GK5K64VX6QWPAXH2")
        assertTrue(uri.isBitcoin)
        assertEquals("BC1QSQP0D3TY8AAA8N9J8R0D2PF3G40VN4AS9TPWY3J9R3GK5K64VX6QWPAXH2",
                uri.bitcoinAddress.get())

        uri = OperationUri.fromString("3AXHY3dJU1z9YkU5o1GKiLomCcdySZhBnj")
        assertTrue(uri.isBitcoin)
        assertEquals("3AXHY3dJU1z9YkU5o1GKiLomCcdySZhBnj", uri.bitcoinAddress.get())

        uri = OperationUri.fromString("BiTCoIN:3AXHY3dJU1z9YkU5o1GKiLomCcdySZhBnj?amount=1.2")
        assertTrue(uri.isBitcoin)
        assertEquals("3AXHY3dJU1z9YkU5o1GKiLomCcdySZhBnj", uri.bitcoinAddress.get())
        assertEquals("1.2", uri.getParam(OperationUri.MUUN_AMOUNT).get())


        val invoice = "lnbc340n1p0r3px4pp5xf6h65d75sjwfd0lg6x37238chfxa8j5e2txn2cu8rwhqll9s3sqdzq2" +
                "pshjmt9de6zqen0wgsrxdpqwp5hsetvwvsxzapqwdshgmmndp5hxtnsd3skxefwxqzjccqp2sp53ul" +
                "dkk4rzafm00pv9sm7u57caycgn0qs52fxvkce96w4349x9pyqrzjqwryaup9lh50kkranzgcdnn2fgvx" +
                "390wgj5jd07rwr3vxeje0glc7z2kxuqqngcqqyqqqqlgqqqqqeqqjq9qy9qsqwahd0wsnyxl5sr0q25" +
                "pd8atey34665wh780ftht9prm5dzdd30nhg5m3wwe2wpw04mk4ps8dz9y0px00sn7u4hrnkkwdhe7ymg" +
                "upz2qpjj9v5r"
        uri = OperationUri.fromString(invoice)
        assertTrue(uri.isLn)
        assertEquals(invoice, uri.lnInvoice.get())

    }

    @Test
    fun fromStringWithMuunUri() {

        val address = "3AXHY3dJU1z9YkU5o1GKiLomCcdySZhBnj"

        val invoice = "lnbc340n1p0r3px4pp5xf6h65d75sjwfd0lg6x37238chfxa8j5e2txn2cu8rwhqll9s3sqdzq" +
                "2pshjmt9de6zqen0wgsrxdpqwp5hsetvwvsxzapqwdshgmmndp5hxtnsd3skxefwxqzjccqp2sp53uld" +
                "kk4rzafm00pv9sm7u57caycgn0qs52fxvkce96w4349x9pyqrzjqwryaup9lh50kkranzgcdnn2fgvx3" +
                "90wgj5jd07rwr3vxeje0glc7z2kxuqqngcqqyqqqqlgqqqqqeqqjq9qy9qsqwahd0wsnyxl5sr0q25pd" +
                "8atey34665wh780ftht9prm5dzdd30nhg5m3wwe2wpw04mk4ps8dz9y0px00sn7u4hrnkkwdhe7ymgup" +
                "z2qpjj9v5r"

        val btcUriWithoutAmount = OperationUri.fromString("muun:$address")
        assertTrue(btcUriWithoutAmount.isBitcoin)
        assertEquals(address, btcUriWithoutAmount.bitcoinAddress.get())

        val btcUriWithAmount = OperationUri.fromString("muun:$address?amount=1.2")
        assertTrue(btcUriWithAmount.isBitcoin)
        assertEquals(address, btcUriWithAmount.bitcoinAddress.get())
        assertEquals("1.2", btcUriWithAmount.getParam(OperationUri.MUUN_AMOUNT).get())

        val btcUriWithAmountAndCurrency = OperationUri.fromString(
            "muun:$address?amount=1.2&currency=BTC"
        )
        assertTrue(btcUriWithAmountAndCurrency.isBitcoin)
        assertEquals(address, btcUriWithAmountAndCurrency.bitcoinAddress.get())
        assertEquals("1.2", btcUriWithAmountAndCurrency.getParam(OperationUri.MUUN_AMOUNT).get())
        assertEquals("BTC", btcUriWithAmountAndCurrency.getParam(OperationUri.MUUN_CURRENCY).get())

        val btcUriWithAmountAndCurrency2 = OperationUri.fromString(
            "muun:$address?amount=1.2&currency=btc"
        )
        assertTrue(btcUriWithAmountAndCurrency2.isBitcoin)
        assertEquals(address, btcUriWithAmountAndCurrency2.bitcoinAddress.get())
        assertEquals("1.2", btcUriWithAmountAndCurrency2.getParam(OperationUri.MUUN_AMOUNT).get())
        assertEquals("btc", btcUriWithAmountAndCurrency2.getParam(OperationUri.MUUN_CURRENCY).get())

        val lnUri = OperationUri.fromString("muun:$invoice")
        assertTrue(lnUri.isLn)
        assertEquals(invoice, lnUri.lnInvoice.get())
    }
}