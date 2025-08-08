package io.muun.apollo.domain.model;

import io.muun.apollo.domain.model.base.PersistentModel;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Hashes;

import androidx.annotation.Nullable;

import javax.validation.constraints.NotNull;

public class PhoneContact extends PersistentModel {

    @NotNull
    public final String internalId;

    @NotNull
    public final String name;

    @NotNull
    public final String phoneNumber;

    @Nullable
    public String phoneNumberHash;

    @NotNull
    public final long firstSeen;

    @NotNull
    public final long lastSeen;

    @NotNull
    public final long lastUpdated;

    /**
     * SQL constructor.
     */
    public PhoneContact(@Nullable Long id,
                        @NotNull String internalId,
                        @NotNull String name,
                        @NotNull String phoneNumber,
                        @Nullable String phoneNumberHash,
                        long firstSeen,
                        long lastSeen,
                        long lastUpdated) {
        super(id);
        this.internalId = internalId;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.phoneNumberHash = phoneNumberHash;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.lastUpdated = lastUpdated;
    }

    /**
     * Manual constructor.
     */
    public PhoneContact(@NotNull String internalId,
                        @NotNull String name,
                        @NotNull String phoneNumber,
                        long firstSeen,
                        long lastSeen,
                        long lastUpdated) {

        super(null);
        this.internalId = internalId;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.lastUpdated = lastUpdated;
    }

    /**
     * Generate the phoneNumberHash field.
     */
    public void generateHash() {
        if (phoneNumberHash == null) {
            phoneNumberHash = PhoneContact.computeHash(phoneNumber);
        }
    }

    @Override
    public boolean equals(Object other) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof PhoneContact)) {
            return false;
        }

        final PhoneContact phone = (PhoneContact) other;

        return internalId.equals(phone.internalId) && phoneNumber.equals(phone.phoneNumber);
    }

    @Override
    public int hashCode() {
        return 31 * internalId.hashCode() + phoneNumber.hashCode();
    }

    /**
     * Compute a PhoneNumber hash.
     */
    public static String computeHash(String phoneNumber) {
        final byte[] inputBytes = Encodings.stringToBytes(phoneNumber);
        final byte[] outputBytes = Hashes.sha256(inputBytes);

        return Encodings.bytesToHex(outputBytes);
    }
}
