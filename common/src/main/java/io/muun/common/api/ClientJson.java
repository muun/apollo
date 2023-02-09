package io.muun.common.api;

import io.muun.common.utils.Since;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.List;
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

    // TODO complete before releasing this
    @Since(apolloVersion = 999) // Apollo ?
    @Nullable // Before that ;)
    public Boolean isRootHint;

    @Since(apolloVersion = 1003) // Apollo only field
    @Nullable // Before that ;)
    public Long totalInternalStorage;

    @Since(apolloVersion = 1003) // Apollo only field
    @Nullable // Before that ;)
    public List<Long> totalExternalStorage;

    @Since(apolloVersion = 1003)
    @Nullable // Before that ;)
    public Long totalRamStorage;

    @Since(apolloVersion = 1003) // Apollo only field
    @Nullable // Before that ;)
    public String androidId;

    @Since(falconVersion = 1011) // Falcon only field
    @Nullable // Before that ;)
    public String deviceCheckToken;

    @Since(apolloVersion = 1005) // Apollo only field
    @Nullable // Before that ;)
    public Long androidCreationTimestampInMilliseconds;

    @Since(apolloVersion = 1005) // Apollo only field
    @Nullable // Before that ;)
    public List<String> drmClientIds;

    /**
     * Json constructor.
     */
    @SuppressWarnings("unused") // Jackson requires it
    public ClientJson() {
    }

    /**
     * Code constructor.
     */
    public ClientJson(final ClientTypeJson type,
                      final String buildType,
                      final int version,
                      @Nullable final String versionName,
                      @Nullable final String deviceModel,
                      @Nullable final Long timezoneOffsetInSeconds,
                      @Nullable final String language,
                      @Nullable final String bigQueryPseudoId,
                      @Nullable final Boolean isRootHint,
                      @Nullable Long totalInternalStorage,
                      @Nullable List<Long> totalExternalStorage,
                      @Nullable Long totalRamStorage,
                      @Nullable String androidId,
                      long androidCreationTimestampInMilliseconds,
                      List<String> drmClientIds
    ) {
        this.type = type;
        this.buildType = buildType;
        this.version = version;
        this.versionName = versionName;
        this.deviceModel = deviceModel;
        this.timezoneOffsetInSeconds = timezoneOffsetInSeconds;
        this.language = language;
        this.bigQueryPseudoId = bigQueryPseudoId;
        this.isRootHint = isRootHint;
        this.totalInternalStorage = totalInternalStorage;
        this.totalExternalStorage = totalExternalStorage;
        this.totalRamStorage = totalRamStorage;
        this.androidId = androidId;
        this.androidCreationTimestampInMilliseconds = androidCreationTimestampInMilliseconds;
        this.drmClientIds = drmClientIds;
    }
}
