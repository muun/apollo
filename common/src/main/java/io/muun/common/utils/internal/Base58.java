/*
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.muun.common.utils.internal;

import java.util.Arrays;

/**
 * Base58 is a way to encode Bitcoin addresses (or arbitrary data) as alphanumeric strings.
 *
 * <p>Note that the encoding/decoding runs in O(n^2) time, so it is not useful for large data.
 *
 * <p>The basic idea of the encoding is to treat the data bytes as a large number represented using
 * base-256 digits, convert the number to be represented using base-58 digits, preserve the exact
 * number of leading zeros (which are otherwise lost during the mathematical operations on the
 * numbers), and finally represent the resulting base-58 digits as alphanumeric ASCII characters.
 */
@SuppressWarnings("WeakerAccess")
public final class Base58 {

    private static final char[] ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final char ENCODED_ZERO = ALPHABET[0];
    private static final int[] INDEXES = new int[128];

    static {
        Arrays.fill(INDEXES, -1);
        for (int i = 0; i < ALPHABET.length; i++) {
            INDEXES[ALPHABET[i]] = i;
        }
    }

    private Base58() {
    }

    /**
     * Encodes the given bytes as a base58 string (no checksum is appended).
     *
     * @param bytes the bytes to encode
     * @return the base58-encoded string
     */
    public static String encode(byte[] bytes) {

        if (bytes.length == 0) {
            return "";
        }

        // count leading zeros
        int zeros = 0;
        while (zeros < bytes.length && bytes[zeros] == 0) {
            ++zeros;
        }

        // convert base-256 digits to base-58 digits (plus conversion to ASCII characters)
        final byte[] input = Arrays.copyOf(bytes, bytes.length); // since we modify it in-place
        final char[] encoded = new char[input.length * 2]; // upper bound
        int outputStart = encoded.length;
        for (int inputStart = zeros; inputStart < input.length; ) {
            encoded[--outputStart] = ALPHABET[divmod(input, inputStart, 256, 58)];
            if (input[inputStart] == 0) {
                ++inputStart; // optimization - skip leading zeros
            }
        }

        // preserve exactly as many leading encoded zeros in output as there were leading zeros in
        // input
        while (outputStart < encoded.length && encoded[outputStart] == ENCODED_ZERO) {
            ++outputStart;
        }

        while (--zeros >= 0) {
            encoded[--outputStart] = ENCODED_ZERO;
        }

        // return encoded string (including encoded leading zeros)
        return new String(encoded, outputStart, encoded.length - outputStart);
    }

    /**
     * Decodes the given base58 string into the original data bytes.
     *
     * @param input the base58-encoded string to decode
     * @return the decoded data bytes
     */
    public static byte[] decode(String input) {

        if (input.length() == 0) {
            return new byte[0];
        }

        // convert the base58-encoded ASCII chars to a base58 byte sequence (base58 digits)
        final byte[] input58 = new byte[input.length()];

        for (int i = 0; i < input.length(); ++i) {
            final char c = input.charAt(i);
            final int digit = c < 128 ? INDEXES[c] : -1;
            if (digit < 0) {
                throw new IllegalArgumentException("Illegal character " + c + " at position " + i);
            }
            input58[i] = (byte) digit;
        }

        // count leading zeros
        int zeros = 0;

        while (zeros < input58.length && input58[zeros] == 0) {
            ++zeros;
        }

        // convert base-58 digits to base-256 digits
        final byte[] decoded = new byte[input.length()];
        int outputStart = decoded.length;

        for (int inputStart = zeros; inputStart < input58.length; ) {
            decoded[--outputStart] = divmod(input58, inputStart, 58, 256);
            if (input58[inputStart] == 0) {
                ++inputStart; // optimization - skip leading zeros
            }
        }

        // ignore extra leading zeroes that were added during the calculation
        while (outputStart < decoded.length && decoded[outputStart] == 0) {
            ++outputStart;
        }

        // return decoded data (including original number of leading zeros)
        return Arrays.copyOfRange(decoded, outputStart - zeros, decoded.length);
    }

    /**
     * Divides a number, represented as an array of bytes each containing a single digit
     * in the specified base, by the given divisor. The given number is modified in-place
     * to contain the quotient, and the return value is the remainder.
     *
     * @param number the number to divide
     * @param firstDigit the index within the array of the first non-zero digit
     *        (this is used for optimization by skipping the leading zeros)
     * @param base the base in which the number's digits are represented (up to 256)
     * @param divisor the number to divide by (up to 256)
     * @return the remainder of the division operation
     */
    private static byte divmod(byte[] number, int firstDigit, int base, int divisor) {

        // this is just long division which accounts for the base of the input digits
        int remainder = 0;

        for (int i = firstDigit; i < number.length; i++) {
            final int digit = (int) number[i] & 0xFF;
            final int temp = remainder * base + digit;
            number[i] = (byte) (temp / divisor);
            remainder = temp % divisor;
        }

        return (byte) remainder;
    }
}