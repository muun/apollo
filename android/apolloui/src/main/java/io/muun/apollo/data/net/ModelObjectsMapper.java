package io.muun.apollo.data.net;

import io.muun.apollo.data.serialization.dates.ApolloZonedDateTime;
import io.muun.apollo.domain.errors.p2p.InvalidPhoneNumberError;
import io.muun.apollo.domain.model.BitcoinAmount;
import io.muun.apollo.domain.model.ChallengeKeyUpdateMigration;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.CreateFirstSessionOk;
import io.muun.apollo.domain.model.CreateSessionOk;
import io.muun.apollo.domain.model.CreateSessionRcOk;
import io.muun.apollo.domain.model.EmergencyKitExport;
import io.muun.apollo.domain.model.ExchangeRateWindow;
import io.muun.apollo.domain.model.FeeBumpFunctions;
import io.muun.apollo.domain.model.FeeWindow;
import io.muun.apollo.domain.model.ForwardingPolicy;
import io.muun.apollo.domain.model.FulfillmentPushedResult;
import io.muun.apollo.domain.model.IncomingSwap;
import io.muun.apollo.domain.model.IncomingSwapHtlc;
import io.muun.apollo.domain.model.MuunFeature;
import io.muun.apollo.domain.model.NextTransactionSize;
import io.muun.apollo.domain.model.NotificationReport;
import io.muun.apollo.domain.model.OperationCreated;
import io.muun.apollo.domain.model.OperationWithMetadata;
import io.muun.apollo.domain.model.PendingChallengeUpdate;
import io.muun.apollo.domain.model.PublicKeySet;
import io.muun.apollo.domain.model.PublicProfile;
import io.muun.apollo.domain.model.RealTimeData;
import io.muun.apollo.domain.model.RealTimeFees;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.apollo.domain.model.TransactionPushed;
import io.muun.apollo.domain.model.tx.PartiallySignedTransaction;
import io.muun.apollo.domain.model.user.EmergencyKit;
import io.muun.apollo.domain.model.user.User;
import io.muun.apollo.domain.model.user.UserPhoneNumber;
import io.muun.apollo.domain.model.user.UserPreferences;
import io.muun.apollo.domain.model.user.UserProfile;
import io.muun.common.Optional;
import io.muun.common.Rules;
import io.muun.common.api.BitcoinAmountJson;
import io.muun.common.api.ChallengeKeyUpdateMigrationJson;
import io.muun.common.api.CommonModelObjectsMapper;
import io.muun.common.api.CreateFirstSessionOkJson;
import io.muun.common.api.CreateSessionOkJson;
import io.muun.common.api.CreateSessionRcOkJson;
import io.muun.common.api.ExportEmergencyKitJson;
import io.muun.common.api.FeeBumpFunctionsJson;
import io.muun.common.api.FeeWindowJson;
import io.muun.common.api.ForwardingPolicyJson;
import io.muun.common.api.FulfillmentPushedJson;
import io.muun.common.api.IncomingSwapHtlcJson;
import io.muun.common.api.IncomingSwapJson;
import io.muun.common.api.MuunFeatureJson;
import io.muun.common.api.NextTransactionSizeJson;
import io.muun.common.api.OperationCreatedJson;
import io.muun.common.api.OperationJson;
import io.muun.common.api.PartiallySignedTransactionJson;
import io.muun.common.api.PendingChallengeUpdateJson;
import io.muun.common.api.PhoneNumberJson;
import io.muun.common.api.PublicKeySetJson;
import io.muun.common.api.PublicProfileJson;
import io.muun.common.api.RealTimeFeesJson;
import io.muun.common.api.SizeForAmountJson;
import io.muun.common.api.SubmarineSwapJson;
import io.muun.common.api.TransactionPushedJson;
import io.muun.common.api.UserJson;
import io.muun.common.api.beam.notification.NotificationReportJson;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKeyTriple;
import io.muun.common.dates.MuunZonedDateTime;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.model.SizeForAmount;
import io.muun.common.model.UtxoStatus;
import io.muun.common.utils.CollectionUtils;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Pair;
import io.muun.common.utils.Preconditions;

import org.bitcoinj.core.NetworkParameters;
import org.threeten.bp.ZonedDateTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;


@Singleton
public class ModelObjectsMapper extends CommonModelObjectsMapper {

    /**
     * Constructor.
     */
    @Inject
    public ModelObjectsMapper(NetworkParameters networkParameters) {
        super(networkParameters);
    }

    /**
     * Create a CreateFirstSession.
     */
    public CreateFirstSessionOk mapCreateFirstSessionOk(CreateFirstSessionOkJson json) {
        return new CreateFirstSessionOk(
                mapUser(json.user),
                Objects.requireNonNull(mapPublicKey(json.cosigningPublicKey)),
                Objects.requireNonNull(mapPublicKey(json.swapServerPublicKey)),
                json.playIntegrityNonce
        );
    }

    private UserPreferences mapUserPreferences(final io.muun.common.model.UserPreferences prefs) {
        return UserPreferences.fromJson(prefs);
    }

    /**
     * Create a nullable date time.
     */
    @Nullable
    private ZonedDateTime mapZonedDateTime(@Nullable MuunZonedDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        return mapNonNullableZonedDateTime(dateTime);
    }

    /**
     * Create a date time.
     */
    @NotNull
    private ZonedDateTime mapNonNullableZonedDateTime(@NotNull MuunZonedDateTime dateTime) {
        return ((ApolloZonedDateTime) dateTime).dateTime;
    }

    /**
     * Create a user.
     */
    @NotNull
    public User mapUser(@NotNull UserJson apiUser) {

        final Optional<UserProfile> maybeProfile = Optional.ofNullable(apiUser.publicProfile)
                .map(this::mapUserProfile);

        final Optional<UserPhoneNumber> maybePhoneNumber = Optional.ofNullable(apiUser.phoneNumber)
                .map(this::mapUserPhoneNumber);

        EmergencyKit emergencyKit = null;
        if (apiUser.emergencyKitLastExportedAt != null) {
            final ExportEmergencyKitJson ek = apiUser.emergencyKit;
            Preconditions.checkNotNull(apiUser.emergencyKit);

            final Optional<EmergencyKitExport.Method> maybeMethod = Optional.ofNullable(ek.method)
                    .map(this::mapExportMethod);

            emergencyKit = new EmergencyKit(
                    Preconditions.checkNotNull(mapZonedDateTime(ek.lastExportedAt)),
                    Preconditions.checkNotNull(ek.version),
                    maybeMethod.isPresent() ? maybeMethod.get() : null
            );
        }

        return User.fromHouston(
                apiUser.id,
                Optional.ofNullable(apiUser.email),
                apiUser.isEmailVerified,
                maybePhoneNumber,
                maybeProfile,
                apiUser.primaryCurrency,
                apiUser.hasRecoveryCodeChallengeKey,
                apiUser.hasPasswordChallengeKey,
                apiUser.hasP2PEnabled,
                apiUser.hasExportedKeys,
                Optional.ofNullable(emergencyKit),
                Optional.ofNullable(apiUser.createdAt).map(this::mapZonedDateTime),
                new TreeSet<>(apiUser.exportedKitVersions)
        );
    }

    /**
     * Create a UserProfile.
     */
    public UserProfile mapUserProfile(@NotNull PublicProfileJson profile) {
        return new UserProfile(profile.firstName, profile.lastName, profile.profilePictureUrl);
    }

    /**
     * Create a UserPhoneNumber.
     */
    public UserPhoneNumber mapUserPhoneNumber(@NotNull PhoneNumberJson phoneNumber) {
        try {
            return new UserPhoneNumber(phoneNumber.number, phoneNumber.isVerified);
        } catch (IllegalArgumentException e) {
            throw new InvalidPhoneNumberError(e);
        }
    }

    /**
     * Create an EmergencyKit export method.
     */
    public EmergencyKitExport.Method mapExportMethod(@NotNull ExportEmergencyKitJson.Method meth) {
        switch (meth) {
            case MANUAL:
                return EmergencyKitExport.Method.MANUAL;

            case DRIVE:
                return EmergencyKitExport.Method.DRIVE;

            case ICLOUD:
                return EmergencyKitExport.Method.ICLOUD;

            default:
                throw new MissingCaseError(meth);
        }
    }

    /**
     * Create a contact.
     */
    @NotNull
    public Contact mapContact(@NotNull io.muun.common.api.Contact contact) {

        return new Contact(
                null,
                contact.publicProfile.userId,
                mapPublicProfile(contact.publicProfile),
                contact.maxAddressVersion,
                mapPublicKey(contact.publicKey),
                mapPublicKey(contact.cosigningPublicKey),
                contact.lastDerivationIndex
        );
    }

    /**
     * Create a public profile.
     */
    @NotNull
    private PublicProfile mapPublicProfile(@NotNull PublicProfileJson publicProfile) {

        return new PublicProfile(
                null,
                publicProfile.userId,
                publicProfile.firstName,
                publicProfile.lastName,
                publicProfile.profilePictureUrl
        );
    }

    /**
     * Create an exchange rate window.
     */
    @NotNull
    private ExchangeRateWindow mapExchangeRateWindow(
            @NotNull io.muun.common.api.ExchangeRateWindow window
    ) {

        return new ExchangeRateWindow(
                window.id,
                mapZonedDateTime(window.fetchDate),
                window.rates
        );
    }

    /**
     * Create a bitcoin amount.
     */
    @NotNull
    private BitcoinAmount mapBitcoinAmount(@NotNull BitcoinAmountJson bitcoinAmount) {

        return new BitcoinAmount(
                bitcoinAmount.inSatoshis,
                bitcoinAmount.inInputCurrency,
                bitcoinAmount.inPrimaryCurrency
        );
    }

    /**
     * Create an operation.
     */
    @NotNull
    public OperationWithMetadata mapOperation(@NotNull OperationJson operation) {

        Preconditions.checkNotNull(operation.id);

        return new OperationWithMetadata(
                operation.id,
                operation.direction,
                operation.isExternal,
                operation.senderProfile != null ? mapPublicProfile(operation.senderProfile) : null,
                operation.senderIsExternal,
                operation.receiverProfile != null
                        ? mapPublicProfile(operation.receiverProfile) : null,
                operation.receiverIsExternal,
                operation.receiverAddress,
                operation.receiverAddressDerivationPath,
                null,
                mapBitcoinAmount(operation.amount),
                mapBitcoinAmount(operation.fee),
                operation.transaction != null ? operation.transaction.confirmations : 0L,
                operation.transaction != null ? operation.transaction.hash : null,
                operation.description,
                operation.status,
                Objects.requireNonNull(mapZonedDateTime(operation.creationDate)),
                operation.exchangeRatesWindowId,
                operation.swap != null ? mapSubmarineSwap(operation.swap) : null,
                operation.receiverMetadata,
                operation.senderMetadata,
                operation.incomingSwap != null ? mapIncomingSwap(operation.incomingSwap) : null,
                // If transaction null, no biggie, isRbf will be defined & updated by Houston later
                operation.transaction != null ? operation.transaction.isReplaceableByFee : false

        );
    }

    /**
     * Create an operation swap.
     */
    @NotNull
    public SubmarineSwap mapSubmarineSwap(SubmarineSwapJson swap) {
        return SubmarineSwap.Companion.fromJson(swap);
    }

    /**
     * Create an IncomingSwap.
     */
    @NotNull
    public IncomingSwap mapIncomingSwap(@NotNull final IncomingSwapJson swap) {

        return new IncomingSwap(
                null,
                swap.uuid,
                Encodings.hexToBytes(swap.paymentHashHex),
                swap.htlc != null ? mapIncomingSwapHtlc(swap.htlc) : null,
                swap.sphinxPacketHex != null ? Encodings.hexToBytes(swap.sphinxPacketHex) : null,
                swap.collectInSats,
                swap.paymentAmountInSats,
                swap.preimageHex != null ? Encodings.hexToBytes(swap.preimageHex) : null
        );
    }

    private IncomingSwapHtlc mapIncomingSwapHtlc(final IncomingSwapHtlcJson htlc) {
        return new IncomingSwapHtlc(
                null,
                htlc.uuid,
                htlc.expirationHeight,
                htlc.fulfillmentFeeSubsidyInSats,
                htlc.lentInSats,
                Encodings.hexToBytes(htlc.swapServerPublicKeyHex),
                htlc.fulfillmentTxHex != null
                        ? Encodings.hexToBytes(htlc.fulfillmentTxHex) : null,
                htlc.address,
                htlc.outputAmountInSatoshis,
                Encodings.hexToBytes(htlc.htlcTxHex)
        );
    }

    /**
     * Create a partially signed transaction.
     */
    @NotNull
    public OperationCreated mapOperationCreated(@NotNull OperationCreatedJson operationCreated) {
        final OperationJson apiOperation = operationCreated.operation;

        Preconditions.checkNotNull(operationCreated.operation);
        Preconditions.checkNotNull(operationCreated.nextTransactionSize);

        final OperationWithMetadata operation = mapOperation(apiOperation);

        return new OperationCreated(
                operation,
                PartiallySignedTransaction.fromJson(
                        operationCreated.partiallySignedTransaction,
                        networkParameters
                ),
                mapNextTransactionSize(operationCreated.nextTransactionSize),
                MuunAddress.fromJson(operationCreated.changeAddress),
                mapAlternativeTransactions(operationCreated.alternativeTransactions)
        );
    }

    private List<PartiallySignedTransaction> mapAlternativeTransactions(
            @Nullable final List<PartiallySignedTransactionJson> txs
    ) {
        if (txs == null) {
            return List.of();
        }

        final var result = new ArrayList<PartiallySignedTransaction>();
        for (final var tx : txs) {
            result.add(PartiallySignedTransaction.fromJson(tx, networkParameters));
        }

        return result;
    }

    /**
     * Create a TransactionPushed object.
     */
    @NotNull
    public TransactionPushed mapTransactionPushed(@NotNull TransactionPushedJson txPushed) {
        Preconditions.checkNotNull(txPushed.nextTransactionSize);

        return new TransactionPushed(
                txPushed.hex,
                mapNextTransactionSize(txPushed.nextTransactionSize),
                mapOperation(txPushed.updatedOperation),
                mapFeeBumpFunctions(txPushed.feeBumpFunctions)
        );
    }

    /**
     * Create a FeeBumpFunctions object.
     */
    @NotNull
    public FeeBumpFunctions mapFeeBumpFunctions(
            @NotNull FeeBumpFunctionsJson feeBumpFunctionsJson
    ) {
        return new FeeBumpFunctions(
                feeBumpFunctionsJson.uuid,
                feeBumpFunctionsJson.functions
        );
    }

    /**
     * Map push fulfillment result data.
     */
    public FulfillmentPushedResult mapFulfillmentPushed(final FulfillmentPushedJson json) {

        return new FulfillmentPushedResult(
                mapNextTransactionSize(json.nextTransactionSize),
                mapFeeBumpFunctions(json.feeBumpFunctions)
        );
    }

    /**
     * Create an expected fee.
     */
    @NotNull
    private FeeWindow mapFeeWindow(@NotNull FeeWindowJson window) {

        // Sanitize targetedFees, just in case
        window.targetedFees.values().removeAll(Collections.singleton(null));

        return new FeeWindow(
                window.id,
                mapZonedDateTime(window.fetchDate),
                window.targetedFees,
                window.fastConfTarget,
                window.mediumConfTarget,
                window.slowConfTarget
        );
    }

    /**
     * Create a bag of real-time data provided by Houston.
     */
    @NotNull
    public RealTimeData mapRealTimeData(@NotNull io.muun.common.api.RealTimeData realTimeData) {
        return new RealTimeData(
                mapFeeWindow(realTimeData.feeWindow),
                mapExchangeRateWindow(realTimeData.exchangeRateWindow),
                realTimeData.currentBlockchainHeight,
                mapForwadingPolicies(realTimeData.forwardingPolicies),
                realTimeData.minFeeRateInWeightUnits,
                mapMuunFeatures(realTimeData.features)
        );
    }

    /**
     * Create a bag of real-time fees data provided by Houston.
     */
    @NotNull
    public RealTimeFees mapRealTimeFees(@NotNull RealTimeFeesJson realTimeFeesJson) {
        // Convert to domain model FeeWindow
        final FeeWindow feeWindow = new FeeWindow(
                1L, // It will be deleted later
                mapNonNullableZonedDateTime(realTimeFeesJson.computedAt),
                mapConfTargetToTargetFeeRateInSatPerVbyte(
                        realTimeFeesJson.targetFeeRates.confTargetToTargetFeeRateInSatPerVbyte
                ),
                realTimeFeesJson.targetFeeRates.fastConfTarget,
                realTimeFeesJson.targetFeeRates.mediumConfTarget,
                realTimeFeesJson.targetFeeRates.slowConfTarget
        );

        final FeeBumpFunctions feeBumpFunctions = new FeeBumpFunctions(
                realTimeFeesJson.feeBumpFunctions.uuid,
                realTimeFeesJson.feeBumpFunctions.functions
        );

        return new RealTimeFees(
                feeBumpFunctions,
                feeWindow,
                realTimeFeesJson.minMempoolFeeRateInSatPerVbyte,
                realTimeFeesJson.minFeeRateIncrementToReplaceByFeeInSatPerVbyte,
                mapNonNullableZonedDateTime(realTimeFeesJson.computedAt)
        );
    }

    private static SortedMap<Integer, Double> mapConfTargetToTargetFeeRateInSatPerVbyte(
            SortedMap<Integer, Double> confTargetToTargetFeeRateInSatPerVbyte
    ) {
        final SortedMap<Integer, Double> targetedFeeRates = new TreeMap<>();

        for (final var entry : confTargetToTargetFeeRateInSatPerVbyte.entrySet()) {

            final var target = entry.getKey();
            final var feeRateInSatPerVbyte = entry.getValue();
            targetedFeeRates.put(
                    target,
                    Rules.toSatsPerWeight(feeRateInSatPerVbyte)
            );
        }
        return targetedFeeRates;
    }

    private List<ForwardingPolicy> mapForwadingPolicies(
            final List<ForwardingPolicyJson> forwardingPolicies
    ) {

        final List<ForwardingPolicy> result = new ArrayList<>();
        for (final ForwardingPolicyJson json : forwardingPolicies) {
            result.add(new ForwardingPolicy(
                    Encodings.hexToBytes(json.identityKeyHex),
                    json.feeBaseMsat,
                    json.feeProportionalMillionths,
                    json.cltvExpiryDelta
            ));
        }

        return result;
    }

    private List<MuunFeature> mapMuunFeatures(List<MuunFeatureJson> features) {
        final List<MuunFeature> mappedFeatures =
                CollectionUtils.mapList(features, MuunFeature.Companion::fromJson);
        mappedFeatures.removeAll(Collections.singletonList(MuunFeature.UNSUPPORTED_FEATURE));
        return mappedFeatures;
    }

    /**
     * Create a NotificationReport.
     */
    @NotNull
    public NotificationReport mapNotificationReport(@NotNull NotificationReportJson reportJson) {
        return new NotificationReport(
                reportJson.previousId,
                reportJson.maximumId,
                reportJson.preview
        );
    }

    /**
     * Create a NextTransactionSize.
     */
    @NotNull
    public NextTransactionSize mapNextTransactionSize(@NotNull NextTransactionSizeJson json) {

        final ArrayList<SizeForAmount> progression = new ArrayList<>(json.sizeProgression.size());

        for (SizeForAmountJson sizeForAmount : json.sizeProgression) {
            progression.add(mapSizeForAmount(sizeForAmount));
        }

        return new NextTransactionSize(
                progression,
                json.validAtOperationHid,
                json.expectedDebtInSat
        );
    }

    /**
     * Create a SizeForAmount.
     */
    @NotNull
    private SizeForAmount mapSizeForAmount(@NotNull SizeForAmountJson sizeForAmount) {
        return new SizeForAmount(
                sizeForAmount.amountInSatoshis,
                sizeForAmount.sizeInBytes.intValue(),
                sizeForAmount.outpoint,
                UtxoStatus.fromJson(sizeForAmount.status),
                sizeForAmount.deltaInWeightUnits,
                sizeForAmount.derivationPath,
                sizeForAmount.addressVersion
        );
    }

    /**
     * Create a PublicKeySet.
     */
    @Nullable
    public PublicKeySet mapPublicKeySet(PublicKeySetJson publicKeySet) {
        return new PublicKeySet(
                new PublicKeyTriple(
                        mapPublicKey(publicKeySet.basePublicKey),
                        mapPublicKey(publicKeySet.baseCosigningPublicKey),
                        mapPublicKey(publicKeySet.baseSwapServerPublicKey)
                ),
                publicKeySet.externalPublicKeyIndices.maxUsedIndex,
                publicKeySet.externalPublicKeyIndices.maxWatchingIndex
        );
    }

    /**
     * Create a PendingChallengeUpdate.
     */
    public PendingChallengeUpdate mapPendingChallengeUpdate(PendingChallengeUpdateJson json) {
        return new PendingChallengeUpdate(json.uuid, json.type);
    }

    /**
     * Create a CreateSessionOk.
     */
    public CreateSessionOk mapCreateSessionOk(CreateSessionOkJson json) {
        return new CreateSessionOk(
                json.isExistingUser,
                json.canUseRecoveryCode,
                json.playIntegrityNonce
        );
    }

    /**
     * Create a CreateSessionRcOk.
     */
    public CreateSessionRcOk mapCreateSessionRcOk(CreateSessionRcOkJson json) {
        return new CreateSessionRcOk(
                json.keySet,
                json.hasEmailSetup,
                json.obfuscatedEmail,
                json.playIntegrityNonce
        );
    }

    /**
     * Create a ChallengeKeyUpdateMigration.
     */
    public ChallengeKeyUpdateMigration mapChalengeKeyUpdateMigration(
            final ChallengeKeyUpdateMigrationJson json
    ) {

        final byte[] passwordKeySalt = Encodings.hexToBytes(json.passwordKeySaltInHex);

        final byte[] recoveryCodeKeySalt = (json.recoveryCodeKeySaltInHex != null)
                ? Encodings.hexToBytes(json.recoveryCodeKeySaltInHex) : null;

        return new ChallengeKeyUpdateMigration(
                passwordKeySalt,
                recoveryCodeKeySalt,
                json.newEncrytpedMuunKey
        );
    }

    /**
     * Map a user and user prerences pair.
     */
    public Pair<User, UserPreferences> mapUserAndPreferences(final UserJson userJson) {
        return Pair.of(
                mapUser(userJson),
                mapUserPreferences(userJson.preferences)
        );
    }
}
