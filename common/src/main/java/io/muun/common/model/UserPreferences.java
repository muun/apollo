package io.muun.common.model;

import io.muun.common.utils.Deprecated;
import io.muun.common.utils.Since;

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
 *
 *  <p>How to modify or remove preferences:
 *  YOU DON'T.
 *  This Json is consumed by both Android and iOS clients. And iOS clients don't ignore unknown
 *  fields so altering or deleting preferences that are already in production is stricyly forbidden.
 *  </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true) // This is a foot-gun! Falcon does not honor this :(
public class UserPreferences {

    @Since(apolloVersion = 201)
    public Boolean receiveStrictMode = false;

    @Since(apolloVersion = 201)
    public Boolean seenNewHome = false;

    @Since(apolloVersion = 600)
    public Boolean seenLnurlFirstTime = false;

    @Deprecated(atApolloVersion = 1001, atFalconVersion = 1008) // Though ignored previously
    @Since(apolloVersion = 911)
    public Boolean lightningDefaultForReceiving = false;

    @Since(apolloVersion = 700)
    public String defaultAddressType = "segwit";

    @Since(apolloVersion = 1001, falconVersion = 1008)
    public Boolean skippedEmailSetup = false;

    @Since(apolloVersion = 1000)
    public ReceiveFormatPreference receiveFormatPreference = ReceiveFormatPreference.ONCHAIN;

    // TODO: set correct release versions
    @Since(apolloVersion = 1001, falconVersion = 1008)
    public Boolean allowMultiSession = false;

    /**
     * JSON constructor.
     */
    public UserPreferences() {
    }

    /**
     * Apollo constructor.
     */
    public UserPreferences(
            final boolean receiveStrictMode,
            final boolean seenNewHome,
            final boolean seenLnurlFirstTime,
            final String defaultAddressType,
            final boolean lightningDefaultForReceiving,
            final boolean skippedEmailSetup,
            final ReceiveFormatPreference receiveFormatPreference,
            final boolean allowMultiSession
    ) {
        this.receiveStrictMode = receiveStrictMode;
        this.seenNewHome = seenNewHome;
        this.seenLnurlFirstTime = seenLnurlFirstTime;
        this.defaultAddressType = defaultAddressType;
        this.lightningDefaultForReceiving = lightningDefaultForReceiving;
        this.skippedEmailSetup = skippedEmailSetup;
        this.receiveFormatPreference = receiveFormatPreference;
        this.allowMultiSession = allowMultiSession;
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

        if (other.skippedEmailSetup != null) {
            this.skippedEmailSetup = other.skippedEmailSetup;
        }

        if (other.receiveFormatPreference != null) {
            this.receiveFormatPreference = other.receiveFormatPreference;
        }

        if (other.allowMultiSession != null) {
            this.allowMultiSession = other.allowMultiSession;
        }
    }

}
