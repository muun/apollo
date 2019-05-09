package io.muun.common.utils;

import java.util.Arrays;

public class ByteArray {

    public static byte[] slice(byte[] bytes, int start, int end) {
        return Arrays.copyOfRange(bytes, start, end);
    }

    /**
     * Slice a byte array. If index is negative, this will return a (copy) byte array with the last
     * 'index' elements of bytes. If index is positive, it will return a (copy) byte array with all
     * BUT first 'index' elements of bytes.
     */
    public static byte[] slice(byte[] bytes, int index) {
        if (index > bytes.length || -index > bytes.length) {
            throw new IllegalArgumentException("Out of bounds: slice index is bigger than array");
        }

        if (index < 0) {
            final int retSize = -index;
            return Arrays.copyOfRange(bytes, bytes.length - retSize, bytes.length);

        } else {

            return Arrays.copyOfRange(bytes, index, bytes.length);
        }
    }

    /**
     * Concat two byte arrays into ine.
     */
    public static byte[] concat(byte[] first, byte[] second) {

        final byte[] combined = new byte[first.length + second.length];

        System.arraycopy(first, 0, combined, 0, first.length);
        System.arraycopy(second, 0, combined, first.length, second.length);

        return combined;
    }
}
