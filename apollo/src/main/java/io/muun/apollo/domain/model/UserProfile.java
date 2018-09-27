package io.muun.apollo.domain.model;

import android.support.annotation.Nullable;

import javax.validation.constraints.NotNull;

public class UserProfile {

    @NotNull
    private final String firstName;

    @NotNull
    private final String lastName;

    @Nullable
    private final String pictureUrl;

    /**
     * Constructor.
     */
    public UserProfile(@NotNull String firstName,
                       @NotNull String lastName) {
        this(firstName, lastName, null);
    }

    /**
     * Constructor.
     */
    public UserProfile(@NotNull String firstName,
                       @NotNull String lastName,
                       @Nullable String pictureUrl) {

        this.firstName = firstName;
        this.lastName = lastName;
        this.pictureUrl = pictureUrl;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPictureUrl() {
        return pictureUrl;
    }
}
