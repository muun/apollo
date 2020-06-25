package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateLoginSessionJson {

    @NotNull
    public ClientJson client;

    @NotEmpty
    public String gcmToken;

    @NotNull
    public String email;

    /**
     * Json constructor.
     */
    public CreateLoginSessionJson() {
    }

    /**
     * Code constructor.
     */
    public CreateLoginSessionJson(ClientJson client, String gcmToken, String email) {
        this.client = client;
        this.gcmToken = gcmToken;
        this.email = email;
    }
}
