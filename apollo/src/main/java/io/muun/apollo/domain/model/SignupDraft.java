package io.muun.apollo.domain.model;


import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.external.Globals;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignupDraft {

    public static SignupDraft deserialize(String json) {
        return SerializationUtils.deserializeJson(SignupDraft.class, json);
    }

    @NotNull
    public Integer versionCode = Globals.INSTANCE.getVersionCode();

    @Nullable
    public Boolean isExistingUser;

    @NotNull
    public SignupStep step = SignupStep.START;

    @NotNull
    public Boolean canUseRecoveryCode;

    @Nullable
    @JsonIgnore
    public transient String password;

    @Nullable
    public String email;

    public String serialize() {
        return SerializationUtils.serializeJson(SignupDraft.class, this);
    }
}
