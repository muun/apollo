package io.muun.common.utils;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HexUtilsTest {

    @Test
    public void testEmptyEncode() throws Exception {
        final byte[] emptyArray = {};
        assertThat(Encodings.bytesToHex(emptyArray)).isEmpty();
    }

    @Test
    public void testValidEncode() throws Exception {
        final byte[] bytes = {0x01, 0x02, (byte) 0xFF};
        assertThat(Encodings.bytesToHex(bytes)).isEqualToIgnoringCase("0102ff");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecodeOddLengthStringFails() throws Exception {
        Encodings.hexToBytes("a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidCharDecode() throws Exception {
        Encodings.hexToBytes("g");
    }

    @Test
    public void testDecodeEmptyString() throws Exception {
        assertThat(Encodings.hexToBytes("")).isEmpty();
    }

    @Test
    public void testValidUpperCaseDecode() throws Exception {
        final byte[] bytes = {0x01, 0x02, (byte) 0xFF};
        assertThat(Encodings.hexToBytes("0102FF")).isEqualTo(bytes);
    }

    @Test
    public void testValidLowerCaseDecode() throws Exception {
        final byte[] bytes = {(byte) 0xca, (byte) 0xb2, (byte) 0xFF};
        assertThat(Encodings.hexToBytes("cab2ff")).isEqualTo(bytes);
    }

    @Test
    public void testValidMixedCaseDecode() throws Exception {
        final byte[] bytes = {(byte) 0xaa, (byte) 0xbb, (byte) 0xdd};
        assertThat(Encodings.hexToBytes("AaBbdD")).isEqualTo(bytes);
    }

}