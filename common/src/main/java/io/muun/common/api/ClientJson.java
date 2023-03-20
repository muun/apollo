package io.muun.common.api;

import io.muun.common.utils.Deprecated;
import io.muun.common.utils.Since;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.List;
import java.util.Map;
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

    @Since(apolloVersion = 604) // Apollo 46.4 // Apollo only field
    @Nullable // Before that ;)
    public String bigQueryPseudoId;

    // TODO complete before releasing this
    @Since(apolloVersion = 900) // Apollo 49 // Apollo only field
    @Nullable // Before that ;)
    public Boolean isRootHint;

    @Since(apolloVersion = 1003) // Apollo only field
    @Deprecated(atApolloVersion = 1007)
    @Nullable // Before that ;) (and after deprecation)
    public Long totalInternalStorage;

    @Since(apolloVersion = 1003) // Apollo only field
    @Deprecated(atApolloVersion = 1007)
    @Nullable // Before that ;)
    public List<Long> totalExternalStorage;

    @Since(apolloVersion = 1003)
    @Deprecated(atApolloVersion = 1007, atFalconVersion = 1012)
    @Nullable // Before that ;) (and after deprecation)
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
    @Deprecated(atApolloVersion = 1007)
    @Nullable // Before that ;) (and after deprecation)
    public List<String> drmClientIds;

    @Since(apolloVersion = 1007) // Apollo only field
    @Nullable // Before that ;)
    public Map<String, String> drmProviderToClientId;

    @Since(apolloVersion = 1006) // Apollo only field
    @Nullable // Before that ;)
    public List<AndroidSystemUserInfoJson> androidSystemUsersInfo;

    @Since(apolloVersion = 1007) // Apollo only field // Apollo 50.7
    @Nullable // Before that ;)
    public Long androidElapsedRealtimeAtSessionCreationInMillis;

    @Since(apolloVersion = 1007) // Apollo only field // Apollo 50.7
    @Nullable // Before that ;)
    public Long androidUptimeAtSessionCreationInMillis;

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
                      @Nullable String androidId,
                      final long androidCreationTimestampInMilliseconds,
                      @Nullable List<AndroidSystemUserInfoJson> systemUsersInfo,
                      @SuppressWarnings("NullableProblems")
                      final Map<String, String> drmProviderClientIds,
                      final long androidElapsedRealtimeAtSessionCreationInMillis,
                      final long androidUptimeAtSessionCreationInMillis
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
        this.androidId = androidId;
        this.androidCreationTimestampInMilliseconds = androidCreationTimestampInMilliseconds;
        this.drmClientIds = null;
        this.androidSystemUsersInfo = systemUsersInfo;
        this.drmProviderToClientId = drmProviderClientIds;
        this.androidElapsedRealtimeAtSessionCreationInMillis =
                androidElapsedRealtimeAtSessionCreationInMillis;
        this.androidUptimeAtSessionCreationInMillis = androidUptimeAtSessionCreationInMillis;
    }
}
