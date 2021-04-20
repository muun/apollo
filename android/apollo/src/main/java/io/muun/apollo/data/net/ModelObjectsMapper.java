package io.muun.apollo.data.net;

import io.muun.apollo.data.serialization.dates.ApolloZonedDateTime;
import io.muun.apollo.domain.errors.InvalidPhoneNumberError;
import io.muun.apollo.domain.model.BitcoinAmount;
import io.muun.apollo.domain.model.ChallengeKeyUpdateMigration;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.CreateFirstSessionOk;
import io.muun.apollo.domain.model.ExchangeRateWindow;
import io.muun.apollo.domain.model.FeeWindow;
import io.muun.apollo.domain.model.ForwardingPolicy;
import io.muun.apollo.domain.model.IncomingSwap;
import io.muun.apollo.domain.model.IncomingSwapHtlc;
import io.muun.apollo.domain.model.NextTransactionSize;
import io.muun.apollo.domain.model.NotificationReport;
import io.muun.apollo.domain.model.OperationCreated;
import io.muun.apollo.domain.model.OperationWithMetadata;
import io.muun.apollo.domain.model.PendingChallengeUpdate;
import io.muun.apollo.domain.model.PublicKeySet;
import io.muun.apollo.domain.model.PublicProfile;
import io.muun.apollo.domain.model.RealTimeData;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.apollo.domain.model.TransactionPushed;
import io.muun.apollo.domain.model.User;
import io.muun.apollo.domain.model.UserPhoneNumber;
import io.muun.apollo.domain.model.UserPreferences;
import io.muun.apollo.domain.model.UserProfile;
import io.muun.common.Optional;
import io.muun.common.api.BitcoinAmountJson;
import io.muun.common.api.ChallengeKeyUpdateMigrationJson;
import io.muun.common.api.CommonModelObjectsMapper;
import io.muun.common.api.CreateFirstSessionOkJson;
import io.muun.common.api.CreateSessionOkJson;
import io.muun.common.api.CreateSessionRcOkJson;
import io.muun.common.api.FeeWindowJson;
import io.muun.common.api.ForwardingPolicyJson;
import io.muun.common.api.IncomingSwapHtlcJson;
import io.muun.common.api.IncomingSwapJson;
import io.muun.common.api.NextTransactionSizeJson;
import io.muun.common.api.OperationCreatedJson;
import io.muun.common.api.OperationJson;
import io.muun.common.api.PendingChallengeUpdateJson;
import io.muun.common.api.PhoneNumberJson;
import io.muun.common.api.PublicKeySetJson;
import io.muun.common.api.PublicProfileJson;
import io.muun.common.api.SizeForAmountJson;
import io.muun.common.api.SubmarineSwapJson;
import io.muun.common.api.TransactionPushedJson;
import io.muun.common.api.UserJson;
import io.muun.common.api.beam.notification.NotificationReportJson;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKeyTriple;
import io.muun.common.crypto.tx.PartiallySignedTransaction;
import io.muun.common.dates.MuunZonedDateTime;
import io.muun.common.model.CreateSessionOk;
import io.muun.common.model.CreateSessionRcOk;
import io.muun.common.model.SizeForAmount;
import io.muun.common.model.UtxoStatus;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Pair;
import io.muun.common.utils.Preconditions;

import org.bitcoinj.core.NetworkParameters;
import org.threeten.bp.ZonedDateTime;

import java.util.ArrayList;
import java.util.List;
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
                mapPublicKey(json.cosigningPublicKey),
                mapPublicKey(json.swapServerPublicKey)
        );
    }

    private UserPreferences mapUserPreferences(final io.muun.common.model.UserPreferences prefs) {
        return UserPreferences.fromJson(prefs);
    }

    /**
     * Create a date time.
     */
    @Nullable
    private ZonedDateTime mapZonedDateTime(@Nullable MuunZonedDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

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

        return new User(
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
                Optional.ofNullable(apiUser.emergencyKitLastExportedAt).map(this::mapZonedDateTime),
                Optional.ofNullable(apiUser.createdAt).map(this::mapZonedDateTime)
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
     * Create an exchange rate window.
     */
    @NotNull
    private ExchangeRateWindow mapExchangeRateWindow(
            @NotNull io.muun.common.api.ExchangeRateWindow window) {

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
                mapZonedDateTime(operation.creationDate),
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
                MuunAddress.fromJson(operationCreated.changeAddress)
        );
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
                mapOperation(txPushed.updatedOperation)
        );
    }

    /**
     * Create an expected fee.
     */
    @NotNull
    private FeeWindow mapFeeWindow(@NotNull FeeWindowJson window) {

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
                realTimeData.minFeeRateInWeightUnits);
    }

    private List<ForwardingPolicy> mapForwadingPolicies(
            final List<ForwardingPolicyJson> forwardingPolicies) {

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
                sizeForAmount.deltaInWeightUnits
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

    public PendingChallengeUpdate mapPendingChallengeUpdate(PendingChallengeUpdateJson json) {
        return new PendingChallengeUpdate(json.uuid, json.type);
    }

    /**
     * Create a CreateSessionOk.
     */
    public CreateSessionOk mapCreateSessionOk(CreateSessionOkJson json) {
        return new CreateSessionOk(
            json.isExistingUser,
            json.canUseRecoveryCode
        );
    }

    public CreateSessionRcOk mapCreateSessionRcOk(CreateSessionRcOkJson json) {
        return new CreateSessionRcOk(json.keySet, json.hasEmailSetup, json.obfuscatedEmail);
    }

    /**
     * Create a ChallengeKeyUpdateMigration.
     */
    public ChallengeKeyUpdateMigration mapChalengeKeyUpdateMigration(
            final ChallengeKeyUpdateMigrationJson json) {

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
