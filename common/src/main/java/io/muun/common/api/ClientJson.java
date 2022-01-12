package io.muun.common.api;

import io.muun.common.utils.Since;

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

    @Since(apolloVersion = 704, falconVersion = 611) // Apollo 47.4 and Falcon 2.4.2
    @Nullable // Before that ;)
    public String versionName;

    @Since(apolloVersion = 604, falconVersion = 507) // Apollo 46.4 and Falcon 2.3.2
    @Nullable // Before that ;)
    public String deviceModel;

    @Since(apolloVersion = 604, falconVersion = 507) // Apollo 46.4 and Falcon 2.3.2
    @Nullable // Before that ;)
    public Long timezoneOffsetInSeconds;

    @Since(apolloVersion = 604, falconVersion = 507) // Apollo 46.4 and Falcon 2.3.2
    @Nullable // Before that ;)
    public String language;

    @Since(apolloVersion = 604, falconVersion = 507) // Apollo 46.4 and Falcon 2.3.2
    @Nullable // Before that ;)
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
                      final int version,
                      final String versionName,
                      final String deviceModel,
                      final Long timezoneOffsetInSeconds,
                      final String language,
                      final String bigQueryPseudoId) {
        this.type = type;
        this.buildType = buildType;
        this.version = version;
        this.versionName = versionName;
        this.deviceModel = deviceModel;
        this.timezoneOffsetInSeconds = timezoneOffsetInSeconds;
        this.language = language;
        this.bigQueryPseudoId = bigQueryPseudoId;
    }
}
