package io.muun.apollo.data.net;

import io.muun.apollo.data.afs.BuildInfo;
import io.muun.apollo.data.afs.PackageManagerAppInfo;
import io.muun.apollo.data.afs.PackageManagerDeviceFeatures;
import io.muun.apollo.data.external.Globals;
import io.muun.apollo.data.os.GooglePlayHelper;
import io.muun.apollo.data.os.GooglePlayServicesHelper;
import io.muun.apollo.data.serialization.dates.ApolloZonedDateTime;
import io.muun.apollo.domain.libwallet.FeeBumpRefreshPolicy;
import io.muun.apollo.domain.libwallet.Invoice;
import io.muun.apollo.domain.model.BackgroundEvent;
import io.muun.apollo.domain.model.BitcoinAmount;
import io.muun.apollo.domain.model.EmergencyKitExport;
import io.muun.apollo.domain.model.IncomingSwapFulfillmentData;
import io.muun.apollo.domain.model.InstallSourceInfo;
import io.muun.apollo.domain.model.OperationWithMetadata;
import io.muun.apollo.domain.model.PublicProfile;
import io.muun.apollo.domain.model.SensorEvent;
import io.muun.apollo.domain.model.SensorEventBatch;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.apollo.domain.model.SubmarineSwapRequest;
import io.muun.apollo.domain.model.user.UserProfile;
import io.muun.common.api.AndroidAppInfoJson;
import io.muun.common.api.AndroidBuildInfoJson;
import io.muun.common.api.AndroidDeviceFeaturesJson;
import io.muun.common.api.BackgroundEventJson;
import io.muun.common.api.BitcoinAmountJson;
import io.muun.common.api.ChallengeKeyJson;
import io.muun.common.api.ChallengeSetupJson;
import io.muun.common.api.ChallengeSignatureJson;
import io.muun.common.api.ChallengeUpdateJson;
import io.muun.common.api.ClientJson;
import io.muun.common.api.ClientTypeJson;
import io.muun.common.api.CreateFirstSessionJson;
import io.muun.common.api.CreateLoginSessionJson;
import io.muun.common.api.CreateRcLoginSessionJson;
import io.muun.common.api.ExportEmergencyKitJson;
import io.muun.common.api.ExternalAddressesRecord;
import io.muun.common.api.FeedbackJson;
import io.muun.common.api.IncomingSwapFulfillmentDataJson;
import io.muun.common.api.LoginJson;
import io.muun.common.api.OperationJson;
import io.muun.common.api.PasswordSetupJson;
import io.muun.common.api.PhoneNumberJson;
import io.muun.common.api.PublicKeyJson;
import io.muun.common.api.PublicProfileJson;
import io.muun.common.api.RealTimeFeesRequestJson;
import io.muun.common.api.SensorEventBatchJson;
import io.muun.common.api.SensorEventJson;
import io.muun.common.api.StartEmailSetupJson;
import io.muun.common.api.SubmarineSwapRequestJson;
import io.muun.common.api.UserInvoiceJson;
import io.muun.common.api.UserProfileJson;
import io.muun.common.crypto.ChallengePublicKey;
import io.muun.common.crypto.ChallengeType;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.model.DebtType;
import io.muun.common.model.PhoneNumber;
import io.muun.common.model.SizeForAmount;
import io.muun.common.model.UtxoStatus;
import io.muun.common.model.challenge.ChallengeSetup;
import io.muun.common.model.challenge.ChallengeSignature;
import io.muun.common.utils.BitcoinUtils;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Preconditions;

import androidx.annotation.NonNull;
import libwallet.MusigNonces;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.money.CurrencyUnit;
import javax.validation.constraints.NotNull;

public class ApiObjectsMapper {

    @Inject
    ApiObjectsMapper() {
    }

    /**
     * Create an API phone number.
     */
    @NotNull
    public PhoneNumberJson mapPhoneNumber(PhoneNumber phoneNumber) {
        // By default, when we create a PhoneNumberJson from a phone number
        // on the client isVerified=false , since Houston is the one who should
        // be telling apollo if the phone is verified or not.
        return new PhoneNumberJson(phoneNumber.toE164String(), false);
    }

    /**
     * Create an API public profile.
     */
    @NotNull
    private PublicProfileJson mapPublicProfile(@NotNull PublicProfile publicProfile) {

        return new PublicProfileJson(
                publicProfile.getHid(),
                publicProfile.firstName,
                publicProfile.lastName,
                publicProfile.profilePictureUrl
        );
    }

    /**
     * Create an API user profile.
     */
    @NotNull
    public UserProfileJson mapUserProfile(UserProfile userProfile) {
        return new UserProfileJson(
                userProfile.getFirstName(),
                userProfile.getLastName(),
                userProfile.getPictureUrl()
        );
    }

    /**
     * Create an API bitcoin amount.
     */
    @NotNull
    private BitcoinAmountJson mapBitcoinAmount(@NotNull BitcoinAmount bitcoinAmount) {

        return new BitcoinAmountJson(
                bitcoinAmount.inSatoshis,
                bitcoinAmount.inInputCurrency,
                bitcoinAmount.inPrimaryCurrency
        );
    }

    /**
     * Create an API operation.
     */
    @NotNull
    public OperationJson mapOperation(
            final @NotNull OperationWithMetadata operation,
            final List<String> outpoints,
            final MusigNonces musigNonces,
            final List<MusigNonces> alternativeTxNonces
    ) {

        final Long outputAmountInSatoshis = mapOutputAmountInSatoshis(operation);

        final List<String> userPublicNoncesHex = new LinkedList<>();
        final long noncesCount = musigNonces.length();
        for (int i = 0; i < noncesCount; i++) {
            userPublicNoncesHex.add(musigNonces.getPubnonceHex(i));
        }

        for (final MusigNonces nonces : alternativeTxNonces) {
            Preconditions.checkState(noncesCount == nonces.length());
            for (int i = 0; i < noncesCount; i++) {
                userPublicNoncesHex.add(nonces.getPubnonceHex(i));
            }
        }

        return new OperationJson(
                UUID.randomUUID().toString(),
                operation.getDirection(),
                operation.isExternal(),
                operation.getSenderProfile() != null
                        ? mapPublicProfile(operation.getSenderProfile()) : null,
                operation.getSenderIsExternal(),
                operation.getReceiverProfile() != null
                        ? mapPublicProfile(operation.getReceiverProfile()) : null,
                operation.getReceiverIsExternal(),
                operation.getReceiverAddress(),
                operation.getReceiverAddressDerivationPath(),
                mapBitcoinAmount(operation.getAmount()),
                mapBitcoinAmount(operation.getFee()),
                outputAmountInSatoshis,
                operation.getExchangeRateWindowHid(),
                operation.getDescription(),
                operation.getStatus(),
                operation.getSwap() != null ? operation.getSwap().houstonUuid : null,
                operation.getSenderMetadata(),
                operation.getReceiverMetadata(),
                outpoints,
                false, // TODO: Set it to proper value.
                userPublicNoncesHex
        );
    }

    private Long mapOutputAmountInSatoshis(OperationWithMetadata operation) {
        if (operation.getSwap() == null) {
            return operation.getAmount().inSatoshis;
        }

        // Workaround Houston validation that enforces all operations to have outputAmount > DUST
        if (operation.getSwap().getFundingOutput().getDebtType() == DebtType.LEND) {
            return fundingOutputAmount(operation);
        }

        return operation.getSwap().getFundingOutput().getOutputAmountInSatoshis();
    }

    /**
     * Return a valid fundintOutputAmount (e.g > DUST) for this operation. Used only as a workaround
     * for Houston validation that enforces all operation to have outputAmount > DUST.
     */
    private Long fundingOutputAmount(OperationWithMetadata operation) {
        final SubmarineSwap swap = operation.getSwap();
        Long inputAmount = operation.getAmount().inSatoshis + swap.getFees().getLightningInSats();
        if (swap.isCollect()) {
            inputAmount += swap.getFundingOutput().getDebtAmountInSatoshis();
        }
        return Math.max(inputAmount, BitcoinUtils.DUST_IN_SATOSHIS);
    }

    /**
     * Map client information.
     */
    public ClientJson mapClient(
            final String bigQueryPseudoId,
            final boolean isRootHint,
            @NonNull final String androidId,
            @NonNull final Map<String, String> drmProviderToClientId,
            @NonNull final InstallSourceInfo installSourceInfo,
            final int bootCount,
            @NonNull final String glEsVersion,
            @NonNull GooglePlayServicesHelper.PlayServicesInfo playServicesInfo,
            @NonNull GooglePlayHelper.PlayInfo playInfo,
            final BuildInfo buildInfo,
            final PackageManagerAppInfo appInfo,
            final PackageManagerDeviceFeatures deviceFeatures,
            final String signatureHash,
            final int quickEmProps,
            final int emArchitecture,
            final String securityEnhancedBuild,
            final String bridgeRootService,
            final long appSize,
            final String vbMeta,
            final Boolean isLowRamDevice,
            final Long firstInstallTimeInMs,
            final String deviceName,
            final Long timeZoneOffsetSeconds,
            final String language,
            final Long uptimeMillis,
            final Long elapsedRealtime,
            final String applicationId
    ) {
        return new ClientJson(
                ClientTypeJson.APOLLO,
                Globals.INSTANCE.getOldBuildType(),
                Globals.INSTANCE.getVersionCode(),
                Globals.INSTANCE.getVersionName(),
                deviceName,
                timeZoneOffsetSeconds,
                language,
                bigQueryPseudoId,
                isRootHint,
                androidId,
                0,
                drmProviderToClientId,
                uptimeMillis,
                elapsedRealtime,
                installSourceInfo.getInstallingPackageName(),
                installSourceInfo.getInitiatingPackageName(),
                buildInfo.getBootloader(),
                bootCount,
                glEsVersion,
                playServicesInfo.getVersionCode(),
                playServicesInfo.getVersionName(),
                playServicesInfo.getClientVersionCode(),
                playInfo.getVersionCode(),
                playInfo.getVersionName(),
                mapBuildInfo(buildInfo),
                mapAppInfo(appInfo),
                mapDeviceFeatures(deviceFeatures),
                signatureHash,
                mapQemuProps(quickEmProps),
                emArchitecture,
                mapSeLinux(securityEnhancedBuild),
                mapAdbRootService(bridgeRootService),
                appSize,
                vbMeta,
                isLowRamDevice,
                firstInstallTimeInMs,
                applicationId
        );
    }

    private AndroidBuildInfoJson mapBuildInfo(BuildInfo buildInfo) {
        return new AndroidBuildInfoJson(
                buildInfo.getAbis(),
                null,
                buildInfo.getBootloader(),
                buildInfo.getManufacturer(),
                buildInfo.getBrand(),
                buildInfo.getHost(),
                buildInfo.getType(),
                buildInfo.getRadioVersion(),
                buildInfo.getSecurityPatch(),
                buildInfo.getModel(),
                buildInfo.getProduct(),
                buildInfo.getRelease()
        );
    }

    private AndroidAppInfoJson mapAppInfo(PackageManagerAppInfo appInfo) {
        return new AndroidAppInfoJson(
                appInfo.getName(),
                appInfo.getLabel(),
                appInfo.getIcon(),
                appInfo.getDebuggable(),
                appInfo.getPersistent()
        );
    }


    private AndroidDeviceFeaturesJson mapDeviceFeatures(
            PackageManagerDeviceFeatures deviceFeatures
    ) {
        return new AndroidDeviceFeaturesJson(
                deviceFeatures.getProximity(),
                deviceFeatures.getAccelerometer(),
                deviceFeatures.getGyro(),
                deviceFeatures.getCompass(),
                deviceFeatures.getTelephony(),
                deviceFeatures.getPc(),
                deviceFeatures.getPip()
        );
    }

    @Nullable
    private Boolean mapAdbRootService(String signalValue) {
        if (signalValue == null) {
            return null;
        }
        return signalValue.equals("1");
    }

    @Nullable
    private Boolean mapSeLinux(String signalValue) {
        if (signalValue == null) {
            return null;
        }
        return signalValue.equals("1");
    }

    @Nullable
    private Boolean mapQemuProps(Integer signalValue) {
        if (signalValue == null) {
            return null;
        }
        return signalValue == 1;
    }


    /**
     * Map a CreateFirstSession object.
     */
    public CreateFirstSessionJson mapCreateFirstSession(
            String gcmToken,
            PublicKey basePublicKey,
            CurrencyUnit primaryCurrency,
            String bigQueryPseudoId,
            boolean isRootHint,
            @NonNull String androidId,
            @NonNull Map<String, String> drmProviderToClientId,
            @NonNull InstallSourceInfo installSourceInfo,
            int bootCount,
            @NonNull String glEsVersion,
            @NonNull GooglePlayServicesHelper.PlayServicesInfo playServicesInfo,
            @NonNull GooglePlayHelper.PlayInfo playInfo,
            final BuildInfo buildInfo,
            final PackageManagerAppInfo appInfo,
            final PackageManagerDeviceFeatures deviceFeatures,
            final String signatureHash,
            final Integer quickEmProps,
            final Integer emArchitecture,
            final String securityEnhancedBuild,
            final String bridgeRootService,
            final Long appSize,
            final String vbMeta,
            final Boolean isLowRamDevice,
            final Long firstInstallTimeInMs,
            final String deviceName,
            final Long timeZoneOffsetSeconds,
            final String language,
            final Long uptimeMillis,
            final Long elapsedRealtime,
            final String applicationId
    ) {

        return new CreateFirstSessionJson(
                mapClient(
                        bigQueryPseudoId,
                        isRootHint,
                        androidId,
                        drmProviderToClientId,
                        installSourceInfo,
                        bootCount,
                        glEsVersion,
                        playServicesInfo,
                        playInfo,
                        buildInfo,
                        appInfo,
                        deviceFeatures,
                        signatureHash,
                        quickEmProps,
                        emArchitecture,
                        securityEnhancedBuild,
                        bridgeRootService,
                        appSize,
                        vbMeta,
                        isLowRamDevice,
                        firstInstallTimeInMs,
                        deviceName,
                        timeZoneOffsetSeconds,
                        language,
                        uptimeMillis,
                        elapsedRealtime,
                        applicationId
                ),
                gcmToken,
                primaryCurrency,
                mapPublicKey(basePublicKey),
                null // No longer needed
        );
    }

    /**
     * Map a CreateLoginSession object.
     */
    public CreateLoginSessionJson mapCreateLoginSession(
            String gcmToken,
            String email,
            String bigQueryPseudoId,
            boolean isRootHint,
            @NonNull String androidId,
            @NonNull Map<String, String> drmProviderToClientId,
            @NonNull InstallSourceInfo installSourceInfo,
            int bootCount,
            @NonNull String glEsVersion,
            @NonNull GooglePlayServicesHelper.PlayServicesInfo playServicesInfo,
            @NonNull GooglePlayHelper.PlayInfo playInfo,
            final BuildInfo buildInfo,
            final PackageManagerAppInfo appInfo,
            final PackageManagerDeviceFeatures deviceFeatures,
            final String signatureHash,
            final Integer quickEmProps,
            final Integer emArchitecture,
            final String securityEnhancedBuild,
            final String bridgeRootService,
            final Long appSize,
            final String vbMeta,
            final Boolean isLowRamDevice,
            final Long firstInstallTimeInMs,
            final String deviceName,
            final Long timeZoneOffsetSeconds,
            final String language,
            final Long uptimeMillis,
            final Long elapsedRealtime,
            final String applicationId
    ) {

        return new CreateLoginSessionJson(
                mapClient(
                        bigQueryPseudoId,
                        isRootHint,
                        androidId,
                        drmProviderToClientId,
                        installSourceInfo,
                        bootCount,
                        glEsVersion,
                        playServicesInfo,
                        playInfo,
                        buildInfo,
                        appInfo,
                        deviceFeatures,
                        signatureHash,
                        quickEmProps,
                        emArchitecture,
                        securityEnhancedBuild,
                        bridgeRootService,
                        appSize,
                        vbMeta,
                        isLowRamDevice,
                        firstInstallTimeInMs,
                        deviceName,
                        timeZoneOffsetSeconds,
                        language,
                        uptimeMillis,
                        elapsedRealtime,
                        applicationId
                ),
                gcmToken,
                email
        );
    }

    /**
     * Map a CreateRcLoginSession object.
     */
    public CreateRcLoginSessionJson mapCreateRcLoginSession(
            String gcmToken,
            String rcChallengePublicKeyHex,
            String bigQueryPseudoId,
            boolean isRootHint,
            @NonNull String androidId,
            @NonNull Map<String, String> drmProviderToClientId,
            @NonNull InstallSourceInfo installSourceInfo,
            int bootCount,
            @NonNull String glEsVersion,
            @NonNull GooglePlayServicesHelper.PlayServicesInfo playServicesInfo,
            @NonNull GooglePlayHelper.PlayInfo playInfo,
            final BuildInfo buildInfo,
            final PackageManagerAppInfo appInfo,
            final PackageManagerDeviceFeatures deviceFeatures,
            final String signatureHash,
            final Integer quickEmProps,
            final Integer emArchitecture,
            final String securityEnhancedBuild,
            final String bridgeRootService,
            final Long appSize,
            final String vbMeta,
            final Boolean isLowRamDevice,
            final Long firstInstallTimeInMs,
            final String deviceName,
            final Long timeZoneOffsetSeconds,
            final String language,
            final Long uptimeMillis,
            final Long elapsedRealtime,
            final String applicationId
    ) {

        return new CreateRcLoginSessionJson(
                mapClient(
                        bigQueryPseudoId,
                        isRootHint,
                        androidId,
                        drmProviderToClientId,
                        installSourceInfo,
                        bootCount,
                        glEsVersion,
                        playServicesInfo,
                        playInfo,
                        buildInfo,
                        appInfo,
                        deviceFeatures,
                        signatureHash,
                        quickEmProps,
                        emArchitecture,
                        securityEnhancedBuild,
                        bridgeRootService,
                        appSize,
                        vbMeta,
                        isLowRamDevice,
                        firstInstallTimeInMs,
                        deviceName,
                        timeZoneOffsetSeconds,
                        language,
                        uptimeMillis,
                        elapsedRealtime,
                        applicationId
                ),
                gcmToken,
                new ChallengeKeyJson(
                        ChallengeType.RECOVERY_CODE,
                        rcChallengePublicKeyHex,
                        2
                )
        );
    }

    /**
     * Create a StartEmailSetup API object.
     */
    public StartEmailSetupJson mapStartEmailSetup(String email, ChallengeSignature chSig) {

        return new StartEmailSetupJson(
                email,
                mapChallengeSignature(chSig)
        );
    }

    /**
     * Create a PasswordSetup.
     */
    public PasswordSetupJson mapPasswordSetup(ChallengeSignature chSig, ChallengeSetup chSetup) {

        return new PasswordSetupJson(
                mapChallengeSignature(chSig),
                mapChallengeSetup(chSetup)
        );
    }

    /**
     * Create an API external addresses record.
     */
    @NotNull
    public ExternalAddressesRecord mapExternalAddressesRecord(int maxUsedIndex) {

        return new ExternalAddressesRecord(maxUsedIndex);
    }

    /**
     * Create an API public key.
     */
    @NotNull
    public PublicKeyJson mapPublicKey(PublicKey publicKey) {

        return new PublicKeyJson(
                publicKey.serializeBase58(),
                publicKey.getAbsoluteDerivationPath()
        );
    }

    /**
     * Create a ChallengeSetup.
     */
    public ChallengeSetupJson mapChallengeSetup(ChallengeSetup setup) {
        return new ChallengeSetupJson(
                setup.type,
                Encodings.bytesToHex(setup.publicKey.toBytes()),
                Encodings.bytesToHex(setup.salt),
                setup.encryptedPrivateKey,
                setup.version
        );
    }

    /**
     * Map to LoginJson.
     */
    public LoginJson mapLogin(
            final ChallengeSignature chSignature,
            final ChallengePublicKey challengePublicKey
    ) {

        final ChallengeSignatureJson
                challengeSignatureJson =
                mapChallengeSignature(chSignature);
        final ChallengeKeyJson challengeKeyJson = new ChallengeKeyJson(
                challengeSignatureJson.type,
                Encodings.bytesToHex(challengePublicKey.toBytes()),
                Encodings.bytesToHex(Objects.requireNonNull(challengePublicKey.getSalt())),
                challengePublicKey.getVersion()
        );

        return new LoginJson(
                challengeKeyJson.type,
                challengeSignatureJson.hex,
                challengeKeyJson
        );
    }

    /**
     * Create a ChallengeSetup.
     */
    public ChallengeSignatureJson mapChallengeSignature(ChallengeSignature challengeSignature) {
        return new ChallengeSignatureJson(
                challengeSignature.type,
                Encodings.bytesToHex(challengeSignature.bytes)
        );
    }

    /**
     * Create a ChallengeUpdate.
     */
    public ChallengeUpdateJson mapChallengeUpdate(String uuid, ChallengeSetup challengeSetup) {
        return new ChallengeUpdateJson(uuid, mapChallengeSetup(challengeSetup));
    }

    /**
     * Create a Feedback.
     */
    public FeedbackJson mapFeedback(String content) {
        return new FeedbackJson(content, FeedbackJson.Type.SUPPORT);
    }

    /**
     * Create a Submarine Swap Request.
     */
    public SubmarineSwapRequestJson mapSubmarineSwapRequest(SubmarineSwapRequest request) {
        return new SubmarineSwapRequestJson(
                request.invoice,
                request.swapExpirationInBlocks,
                request.origin.name().toLowerCase(Locale.getDefault()), // match analytics event
                mapBackgroundTimes(request.bkgTimes)
        );
    }

    private List<BackgroundEventJson> mapBackgroundTimes(List<BackgroundEvent> bkgTimes) {

        final List<BackgroundEventJson> result = new ArrayList<>();

        for (BackgroundEvent bkgTime : bkgTimes) {
            result.add(new BackgroundEventJson(
                    bkgTime.getBeginTimestampInMillis(),
                    bkgTime.getDurationInMillis()
            ));
        }

        return result;
    }

    /**
     * Map an invoice.
     */
    public UserInvoiceJson mapUserInvoice(final Invoice.InvoiceSecret invoice) {
        return new UserInvoiceJson(
                Encodings.bytesToHex(invoice.getPaymentHash()),
                invoice.getShortChannelId(),
                mapPublicKey(invoice.getUserPublicKey()),
                mapPublicKey(invoice.getMuunPublicKey()),
                mapPublicKey(invoice.getIdentityKey())
        );
    }

    /**
     * Map fulfillment data.
     */
    public IncomingSwapFulfillmentData mapFulfillmentData(
            final IncomingSwapFulfillmentDataJson json
    ) {

        return new IncomingSwapFulfillmentData(
                Encodings.hexToBytes(json.fulfillmentTxHex),
                Encodings.hexToBytes(json.muunSignatureHex),
                json.outputPath,
                json.outputVersion
        );
    }

    /**
     * Map information about an exported emergency kit.
     */
    public ExportEmergencyKitJson mapEmergencyKitExport(EmergencyKitExport export) {
        return new ExportEmergencyKitJson(
                ApolloZonedDateTime.of(export.getExportedAt()),
                export.isVerified(),
                export.getGeneratedKit().getVerificationCode(),
                export.getGeneratedKit().getVersion(),
                mapExportMethod(export.getMethod())
        );
    }

    @Nullable
    private ExportEmergencyKitJson.Method mapExportMethod(
            @NotNull EmergencyKitExport.Method
                    meth
    ) {
        switch (meth) {
            case UNKNOWN:
                return null;
            case DRIVE:
                return ExportEmergencyKitJson.Method.DRIVE;
            case MANUAL:
                return ExportEmergencyKitJson.Method.MANUAL;
            case ICLOUD:
                return ExportEmergencyKitJson.Method.ICLOUD;
            default:
                throw new MissingCaseError(meth);
        }
    }

    /**
     *  Creates a RealTimeFeesRequestJson.
     */
    @NotNull
    public RealTimeFeesRequestJson mapRealTimeFeesRequestJson(
            @NotNull List<SizeForAmount> sizeProgression,
            @NotNull FeeBumpRefreshPolicy feeBumpRefreshPolicy
    ) {
        final List<String> unconfirmedUtxos = new ArrayList<>();

        for (final SizeForAmount sizeForAmount : sizeProgression) {
            if (sizeForAmount.status == UtxoStatus.UNCONFIRMED) {
                unconfirmedUtxos.add(sizeForAmount.outpoint);
            }
        }
        ;
        return new RealTimeFeesRequestJson(
                unconfirmedUtxos,
                mapFeeBumpRefreshPolicy(feeBumpRefreshPolicy)
        );
    }

    @NotNull
    private RealTimeFeesRequestJson.FeeBumpRefreshPolicy mapFeeBumpRefreshPolicy(
            @NotNull FeeBumpRefreshPolicy feeBumpRefreshPolicy
    ) {
        switch (feeBumpRefreshPolicy) {
            case PERIODIC:
                return RealTimeFeesRequestJson.FeeBumpRefreshPolicy.PERIODIC;
            case FOREGROUND:
                return RealTimeFeesRequestJson.FeeBumpRefreshPolicy.FOREGROUND;
            case NTS_CHANGED:
                return RealTimeFeesRequestJson.FeeBumpRefreshPolicy.CHANGED_NEXT_TRANSACTION_SIZE;
            case NEW_OP_BLOCKINGLY:
                return RealTimeFeesRequestJson.FeeBumpRefreshPolicy.NEW_OPERATION;
            default:
                throw new MissingCaseError(feeBumpRefreshPolicy);
        }
    }

    /**
     * Map a SensorEventBatch.
     */
    @NonNull
    public SensorEventBatchJson mapSensorEventBatch(
            @NonNull SensorEventBatch sensorEventBatch
    ) {
        return new SensorEventBatchJson(
                mapSensorEvents(sensorEventBatch.getEvents())
        );
    }

    /**
     * Map a list of SensorEvent objects to a list of SensorEventJson representations.
     *
     * @param sensorEvent The list of domain model sensor event.
     * @return A list of SensorEventJson for JSON serialization.
     */
    private List<SensorEventJson> mapSensorEvents(List<SensorEvent> sensorEvent) {
        final List<SensorEventJson> mappedList = new ArrayList<>();
        for (SensorEvent event : sensorEvent) {
            mappedList.add(mapSensorEvent(event));
        }

        return mappedList;
    }

    /**
     * Map a single SensorEvent to its SensorEventJson representation.
     *
     * @param event The SensorEvent to map.
     * @return The mapped SensorEventJson containing the event's details.
     */
    private SensorEventJson mapSensorEvent(SensorEvent event) {
        return new SensorEventJson(
                event.getEventId(),
                ApolloZonedDateTime.of(event.getEventTimestamp()),
                event.getEventType(),
                event.getEventData()
        );
    }
}
