package io.muun.common.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class HexUtilsTest {

    @Test
    @Throws(Exception::class)
    fun `test empty encode`() {
        val emptyArray = byteArrayOf()
        assertThat(Encodings.bytesToHex(emptyArray)).isEmpty()
    }

    @Test
    @Throws(Exception::class)
    fun `test valid encode`() {
        val bytes = byteArrayOf(0x01, 0x02, 0xFF.toByte())
        assertThat(Encodings.bytesToHex(bytes)).isEqualToIgnoringCase("0102ff")
    }

    @Test(expected = IllegalArgumentException::class)
    @Throws(Exception::class)
    fun `test decode odd length string fails`() {
        Encodings.hexToBytes("a")
    }

    @Test(expected = IllegalArgumentException::class)
    @Throws(Exception::class)
    fun `test invalid char decode`() {
        Encodings.hexToBytes("g")
    }

    @Test
    @Throws(Exception::class)
    fun `test decode empty string`() {
        assertThat(Encodings.hexToBytes("")).isEmpty()
    }

    @Test
    @Throws(Exception::class)
    fun `test valid upper case decode`() {
        val bytes = byteArrayOf(0x01, 0x02, 0xFF.toByte())
        assertThat(Encodings.hexToBytes("0102FF")).isEqualTo(bytes)
    }

    @Test
    @Throws(Exception::class)
    fun `test valid lower case decode`() {
        val bytes = byteArrayOf(0xca.toByte(), 0xb2.toByte(), 0xFF.toByte())
        assertThat(Encodings.hexToBytes("cab2ff")).isEqualTo(bytes)
    }

    @Test
    @Throws(Exception::class)
    fun `test valid mixed case decode 1`() {
        val bytes = byteArrayOf(0xaa.toByte(), 0xbb.toByte(), 0xdd.toByte())
        assertThat(Encodings.hexToBytes("AaBbdD")).isEqualTo(bytes)
    }

}