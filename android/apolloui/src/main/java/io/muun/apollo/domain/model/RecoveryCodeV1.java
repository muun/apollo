package io.muun.apollo.domain.model;


import io.muun.common.utils.RandomGenerator;

import com.google.common.base.Preconditions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class RecoveryCodeV1 {

    private static final Character[] ALPHABET = {
            // Upper-case characters except for numbers/letters that look alike:
            'A', 'B', 'C', 'D', 'E', 'F', 'H', 'J', 'K', 'M', 'N', 'P', 'Q', 'R', 'S',
            'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            '2', '3', '4', '5', '7', '8', '9'
    };

    private static final Set<Character> ALPHABET_SET = new HashSet<>(Arrays.asList(ALPHABET));

    public static final Character SEPARATOR = '-';

    public static final int SEGMENT_COUNT = 8;
    public static final int SEGMENT_LENGTH = 4;

    /**
     * Create a random RecoveryCode.
     */
    public static RecoveryCodeV1 createRandom() {
        final String[] segments = new String[SEGMENT_COUNT];

        for (int i = 0; i < segments.length; i++) {
            segments[i] = RandomGenerator.getRandomString(SEGMENT_LENGTH, ALPHABET);
        }

        return new RecoveryCodeV1(segments);
    }

    /**
     * Create a RecoveryCode from a string representation.
     */
    public static RecoveryCodeV1 fromString(String code) {
        return new RecoveryCodeV1(split(code));
    }

    private final String[] segments;

    private RecoveryCodeV1(String[] segments) {
        validateSegmentArray(segments);
        this.segments = segments;
    }

    /**
     * Extract a segment out of this RecoveryCode.
     */
    public String getSegment(int segmentIndex) {
        Preconditions.checkArgument(segmentIndex >= 0 && segmentIndex < segments.length);
        return segments[segmentIndex];
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        for (String segment : segments) {
            sb.append(segment);
            sb.append(SEPARATOR);
        }

        sb.setLength(sb.length() - 1); // remove last SEPARATOR

        return sb.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        return Arrays.equals(segments, ((RecoveryCodeV1) other).segments);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(segments);
    }

    private static void validateSegmentArray(String[] segments) {
        // Detect alphabet errors first (so incomplete codes are already known to be wrong):
        for (String segment: segments) {
            validateAlphabet(segment);
        }

        // Detect length errors after (incomplete codes fail here, caller can handle differently):
        if (segments.length != SEGMENT_COUNT) {
            throw new RecoveryCode.RecoveryCodeLengthError();
        }

        for (String segment: segments) {
            if (segment.length() != SEGMENT_LENGTH) {
                throw new RecoveryCode.RecoveryCodeLengthError();
            }
        }
    }

    private static void validateAlphabet(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (! ALPHABET_SET.contains(text.charAt(i))) {
                throw new RecoveryCode.RecoveryCodeAlphabetError();
            }
        }
    }

    private static String[] split(String code) {
        return code.split(Pattern.quote("" + SEPARATOR));
    }
}
