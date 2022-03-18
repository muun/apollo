package io.muun.apollo.presentation.model.text_decoration;

import io.muun.apollo.domain.errors.MoneyDecorationError;
import io.muun.common.utils.Preconditions;

import android.text.Spannable;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;

import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Welcome to the jungle! Though we've no fun and games here, sorry. This class serves as the
 * editor and/or input pre-processor for our MuunAmountInput. There's several tasks it accomplishes
 * but the main reason for its existence is to handle grouping separator display correctly (e.g
 * auto input/display grouping separators as a user types in, auto remove them as they delete),
 * taking into account the locale (grouping and decimal separator may differ according to the
 * locale).
 * Also, it handles AndroidSupremeLocalizationBug, ugly stuff. See below.
 */
public class MoneyDecoration implements DecorationTransformation {

    public static Character THIN_SPACE = '\u200A';

    private final char decimalSeparator;
    private final char groupingSeparator;

    private DecorationHandler target;

    private int skipCharacterAtPosition = -1;

    private int start = 0;
    private int after = 0;

    private int maxFractionalDigits = Integer.MAX_VALUE;

    /**
     * Constructor.
     */
    @VisibleForTesting
    public MoneyDecoration(Locale locale, DecorationHandler target) {
        this(locale);
        this.target = target;
    }

    /**
     * Constructor.
     */
    public MoneyDecoration(Locale locale) {
        final DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
        this.decimalSeparator = symbols.getDecimalSeparator();
        this.groupingSeparator = THIN_SPACE;
    }

    /**
     * Set target DecorationHandler.
     * This is designed to play "nice" with {@link TextDecorator}.
     */
    @Override
    public void setTarget(DecorationHandler target) {
        this.target = target;
    }

    /**
     * This method is called to notify you that, within <code>s</code>,
     * the <code>count</code> characters beginning at <code>start</code>
     * are about to be replaced by new text with length <code>after</code>.
     * It is an error to attempt to make changes to <code>s</code> from
     * this callback.
     * @see android.text.TextWatcher
     */
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        this.start = start;
        this.after = after;

        if (count == 1 && s.charAt(start) == groupingSeparator) {
            skipCharacterAtPosition = start - 1;

        } else {
            skipCharacterAtPosition = -1;
        }
    }

    /**
     * This method is called to notify you that, somewhere within
     * <code>s</code>, the text has been changed.
     * It is legitimate to make further changes to <code>s</code> from
     * this callback, but be careful not to get yourself into an infinite
     * loop, because any changes you make will cause this method to be
     * called again recursively.
     * (You are not told where the change took place because other
     * afterTextChanged() methods may already have made other changes
     * and invalidated the offsets.  But if you need to know here,
     * you can use {@link Spannable#setSpan} in {@link android.text.TextWatcher#onTextChanged}
     * to mark your place and then look up from here where the span
     * ended up.
     * @see android.text.TextWatcher
     */
    @Override
    public void afterTextChanged(StringBuilder s) {
        Preconditions.checkNotNull(target);

        if (maxFractionalDigits == 0) {
            final int indexOfDecimalComma = s.indexOf(String.valueOf(decimalSeparator));
            if (indexOfDecimalComma != -1) {
                final int decimalsCount = start + after - indexOfDecimalComma - 1;
                s.replace(indexOfDecimalComma, indexOfDecimalComma + decimalsCount + 1, "");
                target.setSelection(target.getSelectionStart() - 1 - decimalsCount);
            }
            target.setText(s);
            return;
        }

        handleAndroidSupremeLocalizationBug(s, target);

        removeExtraDigitIfDeletingGroupingSeparator(s, target);
        cleanLeadingZeros(s, target);
        cleanFractionalDigits(s, target);
        addZeroIfFirstCharIsDecimalSeparator(s, target);

        final String inputString = s.toString();

        final StringBuilder result = new StringBuilder();
        final int integerPartSize = countIntegerDigits(inputString);
        final int caretPosition = target.getSelectionStart();
        final int stringTotalSize = inputString.length();

        boolean isInFractionalPart = false;

        int insertedCount = 0;
        int digitCount = 0;
        int newCaretPosition = caretPosition;

        for (int i = 0; i < stringTotalSize; i++) {
            final char c = s.charAt(i);

            if (c == decimalSeparator) {
                isInFractionalPart = true;
            }

            if (c != groupingSeparator) {
                result.append(c);
                digitCount++;
                insertedCount++;

                final int groupingPosition = integerPartSize - digitCount;
                if (!isInFractionalPart && groupingPosition % 3 == 0 && groupingPosition != 0) {
                    result.append(groupingSeparator);
                    insertedCount++;
                }
            }

            if (i != 0 && i + 1 == caretPosition && newCaretPosition == caretPosition) {
                newCaretPosition = insertedCount;
            }

        }

        target.setText(result);

        // Avoid crashes but log error to understand what's going on
        if (newCaretPosition > result.length()) {
            Timber.e(new MoneyDecorationError(
                    "Trying to set selection caret beyond new text's length",
                    newCaretPosition,
                    target.length(),
                    result.toString(),
                    stringTotalSize,
                    inputString,
                    decimalSeparator,
                    groupingSeparator,
                    maxFractionalDigits,
                    integerPartSize,
                    target.getSelectionStart()

            ));
            newCaretPosition = target.length();

        } else if (newCaretPosition < 0) {
            Timber.e(new MoneyDecorationError(
                    "Trying to set selection caret below 0",
                    newCaretPosition,
                    target.length(),
                    result.toString(),
                    stringTotalSize,
                    inputString,
                    decimalSeparator,
                    groupingSeparator,
                    maxFractionalDigits,
                    integerPartSize,
                    target.getSelectionStart()
            ));
            newCaretPosition = 0;
        }

        target.setSelection(newCaretPosition);
    }

    public void setMaxFractionalDigits(int maxFractionalDigits) {
        this.maxFractionalDigits = maxFractionalDigits;
    }

    /**
     * WELCOME to another episode of OH GOD ANDROID Really?!?! For more info see:
     * https://issuetracker.google.com/issues/36907764. Apparently, Android has had a bug (for quite
     * a while now) that makes an EditText, setup with number|numberDecimal, allow only the dot
     * decimal separator, regardless of the locale used. This inputTypes, used with DecimalFormat,
     * lead to boggus behavior, since one uses a dot decimal separator and the other uses a comma.
     * In our particular case, the MoneyDecoration class, used on an device with an affected locale
     * (e.g es_AR) set, expects strings received in {@link #afterTextChanged(StringBuilder)} to use
     * the locale-specific decimal separator, but the dot is used instead (just like with an english
     * locale). Our workaround, only for affected locales (e.g. ones that use a different decimal
     * separator than the dot), consists of replacing the dot for the locale-specific decimal
     * separator and remove duplicates (as only one can exist in an amount, and each time a user
     * presses the dot we need to apply this transformation and check if a duplicated was added).
     *
     * <p>
     * TL;DR Android has had a bug (reported in 2009, fixed in 2017, and released in Android O) with
     * localization of non-english amounts (locales that use comma as decimal separator). This fixes
     * it.
     * </p>
     */
    private void handleAndroidSupremeLocalizationBug(StringBuilder s, DecorationHandler handler) {
        if (decimalSeparator != '.') {

            replace(s, start, start + after, '.', decimalSeparator);

            final int indexOfFirstOcurrence = s.indexOf(String.valueOf(decimalSeparator)) + 1;

            int removalsCount = 0;
            int indexOfNext = s.indexOf(String.valueOf(decimalSeparator), indexOfFirstOcurrence);
            while (indexOfNext != -1) {
                s.replace(indexOfNext, indexOfNext + 1, "");
                indexOfNext = s.indexOf(String.valueOf(decimalSeparator), indexOfFirstOcurrence);
                removalsCount++;
            }

            handler.setSelection(handler.getSelectionStart() - removalsCount);
        }
    }

    private void replace(StringBuilder sb, int from, int to, char oldChar, char newChar) {
        for (int index = from; index < to; index++) {
            if (sb.charAt(index) == oldChar) {
                sb.setCharAt(index, newChar);
            }
        }
    }

    private void removeExtraDigitIfDeletingGroupingSeparator(StringBuilder s,
                                                             DecorationHandler handler) {
        if (skipCharacterAtPosition >= 0) {
            s.delete(skipCharacterAtPosition, skipCharacterAtPosition + 1);
            handler.setSelection(skipCharacterAtPosition);
        }
    }

    /**
     * Deletes all leading zeros and updates the caret in order to keep consistency.
     */
    private void cleanLeadingZeros(StringBuilder s, DecorationHandler handler) {
        while (s.length() > 1 && s.charAt(0) == '0' && s.charAt(1) != decimalSeparator) {
            s.delete(0, 1);
            if (handler.getSelectionStart() > 0) {
                handler.setSelection(handler.getSelectionStart() - 1);
            }
        }
    }

    private void cleanFractionalDigits(StringBuilder sb, DecorationHandler handler) {
        final String s = sb.toString();
        final int indexOfSeparator = s.indexOf(decimalSeparator);

        if (indexOfSeparator == -1) {
            return; // No fractional digits at all
        }

        final int charsAfterSeparator = s.length() - indexOfSeparator - 1;

        if (charsAfterSeparator <= maxFractionalDigits) {
            return; // Fractional digits below allowed count, all good
        }

        // We have extra characters. Remove them:
        final int indexOfExtraDigits = indexOfSeparator + maxFractionalDigits + 1;
        sb.delete(indexOfExtraDigits, s.length());

        // If the selection ended up beyond the string, place it at the end:
        if (handler.getSelectionStart() > sb.length()) {
            handler.setSelection(indexOfExtraDigits);
        }
    }

    private void addZeroIfFirstCharIsDecimalSeparator(StringBuilder s, DecorationHandler handler) {
        final int selectionStart = handler.getSelectionStart();
        if (s.length() > 0 && s.charAt(0) == decimalSeparator) {
            s.insert(0, "0");
            handler.setText(s);
            handler.setSelection(selectionStart + 1);
        }
    }

    /**
     * Count the amount of digits on the integer part.
     *
     * @param s string with the number to analyze.
     * @return amount of digits on the integer part.
     */
    private int countIntegerDigits(String s) {
        final int size = s.length();
        int count = 0;

        for (int i = 0; i < size; i++) {
            if (s.charAt(i) == decimalSeparator) {
                break;

            } else if (s.charAt(i) != groupingSeparator) {
                count++;
            }
        }

        return count;
    }

    @VisibleForTesting
    public char getDecimalSeparator() {
        return decimalSeparator;
    }

    @VisibleForTesting
    public char getGroupingSeparator() {
        return groupingSeparator;
    }
}