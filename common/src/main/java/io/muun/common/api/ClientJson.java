package io.muun.common.api;

import io.muun.common.Supports;
import io.muun.common.utils.Deprecated;
import io.muun.common.utils.Pair;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @Nullable
    public String versionName;

    @Nullable
    public String deviceModel;

    @Nullable
    public Long timezoneOffsetInSeconds;

    @Nullable
    public String language;

    @Nullable
    public String bigQueryPseudoId;

    @Nullable
    public Boolean isRootHint;

    @Nullable
    public Long totalInternalStorage;

    @Deprecated(atApolloVersion = Supports.RefreshTotalInternalStorageAndRam.APOLLO)
    @Nullable
    public List<Long> totalExternalStorage;

    @Deprecated(
            atApolloVersion = Supports.RefreshTotalInternalStorageAndRam.APOLLO,
            atFalconVersion = Supports.RefreshTotalInternalStorageAndRam.FALCON
    )
    @Nullable
    public Long totalRamStorage;

    @Nullable
    public String androidId;

    @Nullable
    public String deviceCheckToken;

    @Nullable
    public Long androidCreationTimestampInMilliseconds;

    @Nullable
    public List<String> drmClientIds;

    @Nullable
    public Map<String, String> drmProviderToClientId;

    @Nullable
    public Long androidElapsedRealtimeAtSessionCreationInMillis;

    @Nullable
    public Long androidUptimeAtSessionCreationInMillis;

    @Nullable
    public String installSource;

    @Nullable
    public String installInitiatingPackageName;

    @Nullable
    public String installInitiatingPackageSigningInfo;

    @Nullable
    @JsonProperty("fingerprint")
    public String osBuildFingerprint;

    @Nullable
    @JsonProperty("hardware")
    public String hardwareName;

    @Nullable
    @JsonProperty("bootloader")
    public String systemBootloaderVersion;

    @Nullable
    public Integer bootCount;

    @Nullable
    public String glEsVersion;

    @Nullable
    public Map<String, String> cpuInfoLegacy;

    @Nullable
    public List<Pair<String, String>> cpuCommonInfo;

    @Nullable
    public List<List<Pair<String, String>>> cpuPerProcessorInfo;

    @Nullable
    public Long googlePlayServicesVersionCode;

    @Nullable
    public String googlePlayServicesVersionName;

    @Nullable
    public Integer googlePlayServicesClientVersionCode;

    @Nullable
    public Long googlePlayVersionCode;

    @Nullable
    public String googlePlayVersionName;

    @Nullable
    public String fallbackDeviceToken;

    @Nullable
    public Long iosSystemUptimeInMilliseconds;

    @Nullable
    public String iCloudRecordId;

    @Nullable
    public AndroidBuildInfoJson androidBuildInfo;

    @Nullable
    public AndroidAppInfoJson androidAppInfo;

    @Nullable
    public AndroidDeviceFeaturesJson androidDeviceFeatures;

    @Nullable
    public String androidSignatureHash;

    @Nullable
    public Boolean androidQuickEmuProps;

    @Nullable
    public Integer androidEmArchitecture;

    @Nullable
    public Boolean androidSecurityEnhancedBuild;

    @Nullable
    public Boolean androidBridgeRootService;

    @Nullable
    public Long androidAppSize;

    @Nullable
    public List<String> androidHardwareAddresses;

    @Nullable
    public String androidVbMeta;

    @Nullable
    public String androidEfsCreationTimeInSeconds;

    @Nullable
    public Boolean androidIsLowRamDevice;

    @Nullable
    public Long androidFirstInstallTimeInMs;

    @Nullable
    public Map<String, String> androidDeviceRegion;

    @Nullable
    public String androidApplicationId;

    @Nullable
    public String iosAppDisplayName;

    @Nullable
    public String iosAppId;

    @Nullable
    public String iosAppName;

    @Nullable
    public String iosAppPrimaryIconHash;

    @Nullable
    public Boolean iosIsSoftDevice;

    @Nullable
    public String iosSoftDeviceName;

    @Nullable
    public Boolean iosHasGyro;

    @Nullable
    public Integer iosInstallSource;

    /**
     * Json constructor.
     */
    @SuppressWarnings("unused") // Jackson requires it
    public ClientJson() {
    }

    /**
     * Apollo constructor.
     */
    public ClientJson(
            final ClientTypeJson type,
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
            @SuppressWarnings("NullableProblems") final Map<String, String> drmProviderClientIds,
            final long androidElapsedRealtimeAtSessionCreationInMillis,
            final long androidUptimeAtSessionCreationInMillis,
            @Nullable final String installSource,
            @Nullable final String installInitiatingPackageName,
            @Nullable final String systemBootloaderVersion,
            final int bootCount,
            @Nullable final String glEsVersion,
            @Nullable final AndroidAppInfoJson androidAppInfo,
            @Nullable final AndroidDeviceFeaturesJson androidDeviceFeatures,
            @Nullable final String androidSignatureHash,
            @Nullable final Boolean androidQuickEmuProps,
            @Nullable final Integer androidEmachineArchitecture,
            @Nullable final Boolean androidSecurityEnhancedBuild,
            @Nullable final Boolean androidBridgeRootService,
            @Nullable final Long androidAppSize,
            @Nullable final String androidVbMeta,
            @Nullable final Boolean androidIsLowRamDevice,
            @Nullable final Long androidFirstInstallTimeInMs,
            @Nullable final String applicationId
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
        this.drmProviderToClientId = drmProviderClientIds;
        this.androidElapsedRealtimeAtSessionCreationInMillis =
                androidElapsedRealtimeAtSessionCreationInMillis;
        this.androidUptimeAtSessionCreationInMillis = androidUptimeAtSessionCreationInMillis;
        this.installSource = installSource;
        this.installInitiatingPackageName = installInitiatingPackageName;
        this.systemBootloaderVersion = systemBootloaderVersion;
        this.bootCount = bootCount;
        this.glEsVersion = glEsVersion;
        this.googlePlayServicesVersionCode = null;
        this.googlePlayServicesVersionName = null;
        this.googlePlayServicesClientVersionCode = null;
        this.googlePlayVersionCode = null;
        this.googlePlayVersionName = null;
        this.androidBuildInfo = null;
        this.androidAppInfo = androidAppInfo;
        this.androidDeviceFeatures = androidDeviceFeatures;
        this.androidSignatureHash = androidSignatureHash;
        this.androidQuickEmuProps = androidQuickEmuProps;
        this.androidEmArchitecture = androidEmachineArchitecture;
        this.androidSecurityEnhancedBuild = androidSecurityEnhancedBuild;
        this.androidBridgeRootService = androidBridgeRootService;
        this.androidAppSize = androidAppSize;
        this.androidVbMeta = androidVbMeta;
        this.androidIsLowRamDevice = androidIsLowRamDevice;
        this.androidFirstInstallTimeInMs = androidFirstInstallTimeInMs;
        this.androidApplicationId = applicationId;
    }
}