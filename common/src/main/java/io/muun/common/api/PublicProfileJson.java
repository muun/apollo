package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PublicProfileJson {

    @NotNull
    public Long userId;

    @NotEmpty
    public String firstName;

    @NotEmpty
    public String lastName;

    @Nullable
    public String profilePictureUrl;

    /**
     * Json constructor.
     */
    public PublicProfileJson() {
    }

    /**
     * Constructor.
     */
    public PublicProfileJson(Long userId,
                             String firstName,
                             String lastName,
                             @Nullable String profilePictureUrl) {

        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.profilePictureUrl = profilePictureUrl;
    }
}
