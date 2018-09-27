package io.muun.common.utils;

import io.muun.common.utils.internal.LinuxSecureRandom;

import java.security.SecureRandom;
import java.security.Security;
import java.util.UUID;

public final class RandomGenerator {

    private static final SecureRandom random;

    static {
        if ("Android Runtime".equals(System.getProperty("java.runtime.name"))) {
            // Use Google's LinuxSecureRandom to provide SecureRandom:
            Security.insertProviderAt(LinuxSecureRandom.getProvider(), 1);
        }

        random = new SecureRandom();
    }

    /**
     * @param amount The length of the byte array to be generated.
     * @return Array of cryptographically secure random bytes.
     */
    public static byte[] getBytes(int amount) {
        final byte[] bytes = new byte[amount];
        random.nextBytes(bytes);
        return bytes;
    }

    public static String getRandomUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * Returns a pseudo-random, uniformly distributed int value.
     */
    public static int getInt() {
        return random.nextInt();
    }

    /**
     * Returns a pseudo-random, uniformly distributed int value between 0 (inclusive) and the
     * specified value (exclusive).
     */
    public static int getInt(int upperBound) {
        return random.nextInt(upperBound);
    }

    /**
     * Returns a pseudo-random, uniformly distributed, positive int value.
     */
    public static int getPositiveInt() {
        return random.nextInt(Integer.MAX_VALUE) + 1;
    }

    /**
     * Returns a pseudo-random, uniformly distributed long value.
     */
    public static long getLong() {
        return random.nextLong();
    }

    /**
     * Returns a pseudo-random, uniformly distributed, positive long value.
     */
    public static long getPositiveLong() {
        final long nonNegativeLong = (random.nextLong() << 1) >>> 1;
        return (nonNegativeLong % Long.MAX_VALUE) + 1;
    }

    /**
     * Return a cryptographically-secure random string, with letters taken from an alphabet.
     */
    public static String getRandomString(int length, Character[] alphabet) {

        final char[] result = new char[length];

        for (int i = 0; i < result.length; i++) {
            result[i] = alphabet[getInt(alphabet.length)];
        }

        return new String(result);
    }

    public static SecureRandom getSecureRandom() {
        return random;
    }
}
