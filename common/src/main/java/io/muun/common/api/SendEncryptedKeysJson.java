package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SendEncryptedKeysJson {

    @NotEmpty
    public String userKey;

    /**
     * Json constructor.
     */
    public SendEncryptedKeysJson() {
    }

    /**
     * Manual constructor.
     */
    public SendEncryptedKeysJson(String userKey) {
        this.userKey = userKey;
    }
}
