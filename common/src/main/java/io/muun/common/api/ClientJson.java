package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientJson {

    @NotNull
    public ClientTypeJson type;

    @NotEmpty
    public String buildType;

    @Nonnegative
    public int version;

    @Nullable // Set by new apollo 46.4 and 2.3.2
    public String deviceModel;

    @Nullable // Set by new apollo 46.4 and 2.3.2
    public Long timezoneOffsetInSeconds;

    @Nullable // Set by new apollo 46.4 and 2.3.2
    public String language;

    @Nullable // Set by new apollo 46.4 and 2.3.2
    public String bigQueryPseudoId;

    /**
     * Json constructor.
     */
    public ClientJson() {
    }

    /**
     * Code constructor.
     */
    public ClientJson(final ClientTypeJson type,
                      final String buildType,
                      int version,
                      final String deviceModel,
                      final Long timezoneOffsetInSeconds,
                      final String language,
                      final String bigQueryPseudoId) {
        this.type = type;
        this.buildType = buildType;
        this.version = version;
        this.deviceModel = deviceModel;
        this.timezoneOffsetInSeconds = timezoneOffsetInSeconds;
        this.language = language;
        this.bigQueryPseudoId = bigQueryPseudoId;
    }
}
