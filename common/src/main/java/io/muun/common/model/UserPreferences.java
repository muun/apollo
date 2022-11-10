package io.muun.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * How to add new preferences:
 * 1. Decide the type of the field and it's default value.
 * 2. Add the preference here
 *  * Add a nullable field with a default value
 *  * Add it to the constructor too, non-null
 *  * Add it in the merge method
 * 3. In falcon:
 *  * Add the field to UserPreferences as non-optional without a default, adding it to copy
 *  * In StoredUserPreferences add the field with a default value
 *  * In StoredUserPreferences map the field in the constructor and toModel method
 * 4. In apollo:
 *  * Add the field to UserPreferences as non-optional without a default
 *  * In StoredUserPreferences add the field with a default value
 *  * In StoredUserPreferences map the field in the constructor and toModel method
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserPreferences {

    public Boolean receiveStrictMode = false;

    public Boolean seenNewHome = false;

    public Boolean seenLnurlFirstTime = false;

    public Boolean lightningDefaultForReceiving = false;

    public String defaultAddressType = "segwit";

    /**
     * JSON constructor.
     */
    public UserPreferences() {
    }

    /**
     * Apollo constructor.
     */
    public UserPreferences(final boolean receiveStrictMode,
                           final boolean seenNewHome,
                           final boolean seenLnurlFirstTime,
                           final String defaultAddressType,
                           final boolean lightningDefaultForReceiving) {
        this.receiveStrictMode = receiveStrictMode;
        this.seenNewHome = seenNewHome;
        this.seenLnurlFirstTime = seenLnurlFirstTime;
        this.defaultAddressType = defaultAddressType;
        this.lightningDefaultForReceiving = lightningDefaultForReceiving;
    }

    /**
     * Merge the other preferences into these.
     *
     * <p>Replaces current values with non-null values from other.
     */
    public void merge(final UserPreferences other) {

        /*
        Dear future person that adds things here, read carefully:
        Remember to limit the size of fields such as strings to avoid inserting crap into the db.
         */

        if (other.receiveStrictMode != null) {
            this.receiveStrictMode = other.receiveStrictMode;
        }

        if (other.seenNewHome != null) {
            this.seenNewHome = other.seenNewHome;
        }

        if (other.seenLnurlFirstTime != null) {
            this.seenLnurlFirstTime = other.seenLnurlFirstTime;
        }

        if (other.defaultAddressType != null) {
            this.defaultAddressType = other.defaultAddressType.substring(
                    0, Math.min(10, other.defaultAddressType.length())
            );
        }

        if (other.lightningDefaultForReceiving != null) {
            this.lightningDefaultForReceiving = other.lightningDefaultForReceiving;
        }
    }

}
