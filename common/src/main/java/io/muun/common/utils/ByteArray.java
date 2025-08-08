package io.muun.common.utils;

import java.util.Arrays;

public class ByteArray {

    /**
     * Return a copy of bytes from start to end.
     */
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
     * Concat multiple byte arrays into one.
     */
    public static byte[] concat(byte[]... parts) {

        final int totalLength = Arrays.stream(parts)
                .map(x -> x.length)
                .reduce(0, Integer::sum);

        final byte[] concatenatedArray = new byte[totalLength];
        int currentPosition = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, concatenatedArray, currentPosition, part.length);
            currentPosition += part.length;
        }

        return concatenatedArray;
    }
}
