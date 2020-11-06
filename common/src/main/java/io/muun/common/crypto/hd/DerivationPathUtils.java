package io.muun.common.crypto.hd;

import io.muun.common.crypto.hd.exception.InvalidDerivationPathException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.validation.constraints.NotNull;

/**
 * This class provides functions to work with our augmented HD paths, which we simply call paths.
 *
 * <p>We extend the notation of BIP 32 derivation paths (which we call canonical), by allowing any
 * part to be (optionally) prepended with a comment and a colon. A comment is any sequence of
 * letters, numbers and hyphens.
 *
 * <p>The root of the path, usually just an m, is itself a comment, so you can use any allowed
 * character.
 *
 * <p>For example, client-key/schema:1'/recovery:1'/qr:1/123 is equivalent to the BIP 32 derivation
 * path m/1'/1'/1/123
 */
public class DerivationPathUtils {

    private static final String PATH_PATTERN = "^[a-zA-Z\\d\\-]+(/([a-zA-Z\\d\\-]+:)?\\d+'?)*$";

    private static final Pattern PATH_REGEX = Pattern.compile(PATH_PATTERN);

    /**
     * Translates an (augmented) path no a canonical one.
     */
    @NotNull
    public static String translateToCanonicalPath(@NotNull String path) {

        final List<ChildNumber> childNumbers = parsePath(path);

        final StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("m");

        for (final ChildNumber childNumber : childNumbers) {
            stringBuilder.append("/");
            stringBuilder.append(childNumber.getIndex());
            stringBuilder.append(childNumber.isHardened() ? "'" : "");
        }

        return stringBuilder.toString();
    }

    /**
     * Parses a path and returns a list of child numbers to derive it.
     *
     * @param absolutePath An absolute derivation path.
     */
    @NotNull
    public static List<ChildNumber> parsePath(@NotNull String absolutePath) {

        if (!PATH_REGEX.matcher(absolutePath).matches()) {
            throw new InvalidDerivationPathException(absolutePath);
        }

        final String[] pathParts = absolutePath.split("/");

        final ArrayList<ChildNumber> childNumbers = new ArrayList<>(pathParts.length);


        for (int i = 1; i < pathParts.length; i += 1) {

            final boolean isHardened = pathParts[i].endsWith("'");

            String childString = pathParts[i];

            if (isHardened) {
                childString = childString.substring(0, childString.length() - 1);
            }

            final String comment;
            final String indexString;

            final String[] childParts = childString.split(":");

            if (childParts.length == 1) {
                comment = null;
                indexString = childParts[0];
            } else {
                comment = childParts[0];
                indexString = childParts[1];
            }

            final Integer index;
            try {
                index = Integer.valueOf(indexString);
            } catch (NumberFormatException exception) {
                throw new InvalidDerivationPathException(absolutePath);
            }

            final ChildNumber childNumber = new ChildNumber(index, isHardened, comment);
            childNumbers.add(childNumber);
        }

        return childNumbers;
    }

}
