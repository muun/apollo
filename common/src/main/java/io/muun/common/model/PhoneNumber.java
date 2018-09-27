package io.muun.common.model;

import io.muun.common.Optional;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.io.Serializable;

import javax.annotation.Nullable;

public class PhoneNumber implements Serializable {

    private static final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    private final Phonenumber.PhoneNumber phoneNumber;

    /**
     * Returns an example phone number within a country code.
     */
    public static Optional<PhoneNumber> getExample(String countryCode) {
        final Phonenumber.PhoneNumber exampleNumber = phoneNumberUtil
                .getExampleNumberForType(countryCode, PhoneNumberUtil.PhoneNumberType.MOBILE);

        if (exampleNumber == null) {
            return Optional.empty();
        }

        final String formattedExample = phoneNumberUtil.format(
                exampleNumber,
                PhoneNumberUtil.PhoneNumberFormat.E164
        );

        return Optional.of(new PhoneNumber(formattedExample));
    }

    /**
     * Creates a PhoneNumber from its E.164 representation.
     */
    public PhoneNumber(String e164PhoneNumber) {
        this(e164PhoneNumber, null);
    }

    /**
     * Parses a string and creates an object representing a phone number.
     *
     * <p>Tests whether the phone number matches a valid pattern. Note this doesn't verify the
     * number is actually in use, which is impossible to tell by just looking at a number itself.
     *
     * @param phoneNumber number that we are attempting to parse. This can contain formatting such
     *                    as +, (), and -, as well as a phone number extension.
     * @param regionCode  the ISO 3166-1 two-letter region code that denotes the region that we want
     *                    to get the country calling code for. If country code is present in
     *                    {phoneNumber}, this parameter is ignored and null can be used.
     * @throws IllegalArgumentException if the string is not considered to be a possible valid phone
     *                                  number.
     */
    public PhoneNumber(String phoneNumber, @Nullable String regionCode)
            throws IllegalArgumentException {

        try {
            this.phoneNumber = phoneNumberUtil.parse(phoneNumber.trim(), regionCode);
        } catch (NumberParseException e) {
            throw new IllegalArgumentException("Invalid phone number");
        }

        if (!phoneNumberUtil.isValidNumber(this.phoneNumber)) {
            throw new IllegalArgumentException("Invalid phone number");
        }
    }

    /**
     * Returns the E.164 representation of the phone number.
     */
    public String toE164String() {
        final String formattedNumber = phoneNumberUtil.format(
                phoneNumber,
                PhoneNumberUtil.PhoneNumberFormat.E164
        );

        return normalizeArgentinePhoneNumber(formattedNumber);
    }

    /**
     * Returns the formatted national portion of this phone number.
     */
    public String toNationalPrettyString() {
        final String formattedNumber = phoneNumberUtil.format(
                phoneNumber,
                PhoneNumberUtil.PhoneNumberFormat.NATIONAL
        );

        if (getCountryCode().equals("AR")) {
            return formattedNumber.substring(1); // remove leading 0

        } else {
            return formattedNumber;
        }
    }

    /**
     * Returns a good-looking version of the normalized phone number.
     */
    public String toPrettyString() {

        return phoneNumberUtil.format(
                phoneNumber,
                PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL
        );
    }

    private String normalizeArgentinePhoneNumber(String formattedNumber) {
        if (formattedNumber.startsWith("+54") && !formattedNumber.startsWith("+549")) {
            // Ensure "9" is present if `formattedNumber` is prefixed with "+54":
            return "+549" + formattedNumber.substring(3);
        } else {
            return formattedNumber;
        }
    }

    /**
     * Get the country prefix number (e.g. "54" for Argentina), or "" if not included.
     */
    public String getCountryNumber() {
        final int number = phoneNumber.getCountryCode();

        if (number == 0) {
            return "";
        } else {
            return "" + number;
        }
    }

    /**
     * Get the country code (e.g. "AR") if possible, or "ZZ" if not known.
     */
    public String getCountryCode() {
        return phoneNumberUtil.getRegionCodeForCountryCode(phoneNumber.getCountryCode());
    }

    /**
     * Get the phone number digits AFTER the country number (what you would dial locally).
     */
    public String getNationalNumber() {
        return phoneNumberUtil.getNationalSignificantNumber(phoneNumber);
    }

    /**
     * Get the national area number if possible.
     */
    public Optional<String> getAreaNumber() {
        String localPhoneNumber = getNationalNumber();

        Phonenumber.PhoneNumber realPhoneNumber = this.phoneNumber;

        // Take out the leading 9 of Argentina's mobile phone numbers
        if (getCountryNumber().equals("54") && localPhoneNumber.startsWith("9")) {
            localPhoneNumber = localPhoneNumber.substring(1);

            try {
                realPhoneNumber = phoneNumberUtil.parse(localPhoneNumber, "AR");
            } catch (NumberParseException ignored) {
                return Optional.empty();
            }
        }

        final int areaLength = phoneNumberUtil.getLengthOfGeographicalAreaCode(realPhoneNumber);

        if (areaLength == 0) {
            return Optional.empty();
        }

        return Optional.of(localPhoneNumber.substring(0, areaLength));
    }
}
