package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfileJson {

    @NotEmpty
    public String firstName;

    @NotEmpty
    public String lastName;

    @Nullable
    public String profilePictureUrl;

    /**
     * Json constructor.
     */
    public UserProfileJson() {
    }

    /**
     * Constructor.
     */
    public UserProfileJson(String firstName,
                           String lastName,
                           @Nullable String profilePictureUrl) {

        this.firstName = firstName;
        this.lastName = lastName;
        this.profilePictureUrl = profilePictureUrl;
    }

    /**
     * Convenience constructor.
     */
    public UserProfileJson(String firstName,
                           String lastName) {

        this(firstName, lastName, null);
    }
}
