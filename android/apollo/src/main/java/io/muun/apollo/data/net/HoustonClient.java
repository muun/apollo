package io.muun.apollo.data.net;

import io.muun.apollo.data.net.base.BaseClient;
import io.muun.apollo.data.net.okio.ContentUriRequestBody;
import io.muun.apollo.data.os.CpuInfoProvider;
import io.muun.apollo.data.os.GooglePlayHelper;
import io.muun.apollo.data.os.GooglePlayServicesHelper;
import io.muun.apollo.data.os.HardwareCapabilitiesProvider;
import io.muun.apollo.domain.errors.newop.CyclicalSwapError;
import io.muun.apollo.domain.errors.newop.InvalidInvoiceException;
import io.muun.apollo.domain.errors.newop.InvoiceAlreadyUsedException;
import io.muun.apollo.domain.errors.newop.InvoiceExpiredException;
import io.muun.apollo.domain.errors.newop.InvoiceExpiresTooSoonException;
import io.muun.apollo.domain.errors.newop.InvoiceMissingAmountException;
import io.muun.apollo.domain.errors.newop.NoPaymentRouteException;
import io.muun.apollo.domain.errors.newop.SwapFailedException;
import io.muun.apollo.domain.errors.newop.UnreachableNodeException;
import io.muun.apollo.domain.libwallet.Invoice;
import io.muun.apollo.domain.model.ChallengeKeyUpdateMigration;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.CreateFirstSessionOk;
import io.muun.apollo.domain.model.CreateSessionOk;
import io.muun.apollo.domain.model.CreateSessionRcOk;
import io.muun.apollo.domain.model.EmergencyKitExport;
import io.muun.apollo.domain.model.IncomingSwapFulfillmentData;
import io.muun.apollo.domain.model.NextTransactionSize;
import io.muun.apollo.domain.model.NotificationReport;
import io.muun.apollo.domain.model.OperationCreated;
import io.muun.apollo.domain.model.OperationWithMetadata;
import io.muun.apollo.domain.model.PendingChallengeUpdate;
import io.muun.apollo.domain.model.PlayIntegrityToken;
import io.muun.apollo.domain.model.PublicKeySet;
import io.muun.apollo.domain.model.RealTimeData;
import io.muun.apollo.domain.model.Sha256Hash;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.apollo.domain.model.SubmarineSwapRequest;
import io.muun.apollo.domain.model.TransactionPushed;
import io.muun.apollo.domain.model.user.User;
import io.muun.apollo.domain.model.user.UserPhoneNumber;
import io.muun.apollo.domain.model.user.UserPreferences;
import io.muun.apollo.domain.model.user.UserProfile;
import io.muun.common.Optional;
import io.muun.common.api.ChallengeSetupVerifyJson;
import io.muun.common.api.CreateFirstSessionJson;
import io.muun.common.api.CreateLoginSessionJson;
import io.muun.common.api.CreateRcLoginSessionJson;
import io.muun.common.api.DiffJson;
import io.muun.common.api.ExternalAddressesRecord;
import io.muun.common.api.IntegrityCheck;
import io.muun.common.api.IntegrityStatus;
import io.muun.common.api.KeySet;
import io.muun.common.api.LinkActionJson;
import io.muun.common.api.PasswordSetupJson;
import io.muun.common.api.PhoneConfirmation;
import io.muun.common.api.PlayIntegrityTokenJson;
import io.muun.common.api.PreimageJson;
import io.muun.common.api.PublicKeySetJson;
import io.muun.common.api.RawTransaction;
import io.muun.common.api.SetupChallengeResponse;
import io.muun.common.api.UpdateOperationMetadataJson;
import io.muun.common.api.UserJson;
import io.muun.common.api.UserProfileJson;
import io.muun.common.api.error.ErrorCode;
import io.muun.common.api.houston.HoustonService;
import io.muun.common.crypto.ChallengePublicKey;
import io.muun.common.crypto.ChallengeType;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.model.Diff;
import io.muun.common.model.PhoneNumber;
import io.muun.common.model.VerificationType;
import io.muun.common.model.challenge.Challenge;
import io.muun.common.model.challenge.ChallengeSetup;
import io.muun.common.model.challenge.ChallengeSignature;
import io.muun.common.rx.ObservableFn;
import io.muun.common.rx.RxHelper;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Pair;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import android.net.Uri;
import libwallet.MusigNonces;
import okhttp3.MediaType;
import rx.Completable;
import rx.Observable;
import rx.Single;

import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.money.CurrencyUnit;
import javax.validation.constraints.NotNull;


public class HoustonClient extends BaseClient<HoustonService> {

    private final ModelObjectsMapper modelMapper;
    private final ApiObjectsMapper apiMapper;
    private final Context context;
    private final HardwareCapabilitiesProvider hardwareCapabilitiesProvider;
    private final GooglePlayServicesHelper googlePlayServicesHelper;
    private final GooglePlayHelper googlePlayHelper;

    /**
     * Constructor.
     */
    @Inject
    public HoustonClient(
            ModelObjectsMapper modelMapper,
            ApiObjectsMapper apiMapper,
            Context context,
            HardwareCapabilitiesProvider hardwareCapabilitiesProvider,
            GooglePlayServicesHelper googlePlayServicesHelper,
            GooglePlayHelper googlePlayHelper
    ) {

        super(HoustonService.class);

        this.modelMapper = modelMapper;
        this.apiMapper = apiMapper;
        this.context = context;
        this.hardwareCapabilitiesProvider = hardwareCapabilitiesProvider;
        this.googlePlayServicesHelper = googlePlayServicesHelper;
        this.googlePlayHelper = googlePlayHelper;
    }

    /**
     * Creates a session for a first-time unrecoverable user.
     */
    public Observable<CreateFirstSessionOk> createFirstSession(
            String gcmRegistrationToken,
            PublicKey basePublicKey,
            CurrencyUnit primaryCurrency,
            String bigQueryPseudoId,
            boolean isRootHint
    ) {

        final CreateFirstSessionJson params = apiMapper.mapCreateFirstSession(
                gcmRegistrationToken,
                basePublicKey,
                primaryCurrency,
                bigQueryPseudoId,
                isRootHint,
                hardwareCapabilitiesProvider.getAndroidId(),
                hardwareCapabilitiesProvider.getSystemUsersInfo(),
                hardwareCapabilitiesProvider.getDrmClientIds(),
                HoustonClient_ExtensionsKt.getInstallSourceInfo(context),
                hardwareCapabilitiesProvider.getBootCount(),
                hardwareCapabilitiesProvider.getGlEsVersion(),
                CpuInfoProvider.INSTANCE.getCpuInfo(),
                googlePlayServicesHelper.getPlayServicesInfo(),
                googlePlayHelper.getPlayInfo()
        );

        return getService().createFirstSession(params)
                .map(modelMapper::mapCreateFirstSessionOk);
    }

    /**
     * Creates a session to log into an existing user.
     */
    public Observable<CreateSessionOk> createLoginSession(
            String gcmRegistrationToken,
            String email,
            String bigQueryPseudoId,
            boolean isRootHint
    ) {

        final CreateLoginSessionJson params = apiMapper.mapCreateLoginSession(
                gcmRegistrationToken,
                email,
                bigQueryPseudoId,
                isRootHint,
                hardwareCapabilitiesProvider.getAndroidId(),
                hardwareCapabilitiesProvider.getSystemUsersInfo(),
                hardwareCapabilitiesProvider.getDrmClientIds(),
                HoustonClient_ExtensionsKt.getInstallSourceInfo(context),
                hardwareCapabilitiesProvider.getBootCount(),
                hardwareCapabilitiesProvider.getGlEsVersion(),
                CpuInfoProvider.INSTANCE.getCpuInfo(),
                googlePlayServicesHelper.getPlayServicesInfo(),
                googlePlayHelper.getPlayInfo()
        );

        return getService().createLoginSession(params)
                .map(modelMapper::mapCreateSessionOk);
    }

    /**
     * Creates a session to log into an existing user, using "RC only" flow.
     */
    public Observable<Challenge> createRcLoginSession(
            String gcmToken,
            String rcChallengePublicKeyHex,
            String bigQueryPseudoId,
            boolean isRootHint
    ) {

        final CreateRcLoginSessionJson session = apiMapper.mapCreateRcLoginSession(
                gcmToken,
                rcChallengePublicKeyHex,
                bigQueryPseudoId,
                isRootHint,
                hardwareCapabilitiesProvider.getAndroidId(),
                hardwareCapabilitiesProvider.getSystemUsersInfo(),
                hardwareCapabilitiesProvider.getDrmClientIds(),
                HoustonClient_ExtensionsKt.getInstallSourceInfo(context),
                hardwareCapabilitiesProvider.getBootCount(),
                hardwareCapabilitiesProvider.getGlEsVersion(),
                CpuInfoProvider.INSTANCE.getCpuInfo(),
                googlePlayServicesHelper.getPlayServicesInfo(),
                googlePlayHelper.getPlayInfo()
        );

        return getService().createRecoveryCodeLoginSession(session)
                .map(modelMapper::mapChallenge);
    }

    /**
     * Submit Google Play Integrity token (or error) to Houston for decryption and verification.
     */
    public Completable submitPlayIntegrityToken(final PlayIntegrityToken playIntegrityToken) {
        return getService().submitPlayIntegrityToken(new PlayIntegrityTokenJson(
                playIntegrityToken.getToken(),
                playIntegrityToken.getError(),
                playIntegrityToken.getCause()
        ));
    }

    /**
     * Login by sending a challenge signature, signed with the RC.
     * Note: If user has email setup, email auth will be required to finish login.
     */
    public Observable<CreateSessionRcOk> loginWithRecoveryCode(ChallengeSignature challengeSig) {

        return getService().loginWithRecoveryCode(apiMapper.mapChallengeSignature(challengeSig))
                .map(modelMapper::mapCreateSessionRcOk);
    }

    /**
     * Use an external authorize link (received by email) to finish RC login.
     */
    public Observable<Void> authorizeLoginWithRecoveryCode(String uuid) {
        return getService().authorizeLoginWithRecoveryCode(new LinkActionJson(uuid));
    }

    /**
     * Finish Login with Recovery Code (after email auth) by fetching KeySet.
     */
    public Observable<KeySet> fetchKeyset() {
        return getService().getKeySet();
    }

    /**
     * Start the email setup process.
     */
    public Observable<Void> startEmailSetup(String email, ChallengeSignature anonChallengeSig) {
        return getService().startEmailSetup(apiMapper.mapStartEmailSetup(email, anonChallengeSig));
    }

    /**
     * Use an external verify link (received by email).
     */
    public Observable<Void> useVerifyLink(String uuid) {
        return getService().useVerifyLink(new LinkActionJson(uuid));
    }

    /**
     * Run the password setup.
     */
    public Observable<Void> setUpPassword(ChallengeSignature chSig, ChallengeSetup chSetup) {

        final PasswordSetupJson params =
                apiMapper.mapPasswordSetup(chSig, chSetup);

        return getService().setUpPassword(params);
    }

    /**
     * Creates a new PhoneNumber for the user.
     */
    public Observable<UserPhoneNumber> createPhone(PhoneNumber phoneNumber) {
        return getService().createPhone(apiMapper.mapPhoneNumber(phoneNumber))
                .map(modelMapper::mapUserPhoneNumber);
    }

    /**
     * Asks Houston for a new verification code, to be sent via sms or phone call.
     *
     * @param verificationType the way to deliver the verification code, either sms or phone call
     */
    public Observable<Void> resendVerificationCode(@NotNull VerificationType verificationType) {
        return getService().resendVerificationCode(verificationType);
    }

    /**
     * Confirms a phone.
     *
     * @return whether the user was already signed up.
     */
    public Observable<UserPhoneNumber> confirmPhone(@NotNull String verificationCode) {
        final PhoneConfirmation phoneConfirmation = new PhoneConfirmation(verificationCode);

        return getService().confirmPhone(phoneConfirmation)
                .map(modelMapper::mapUserPhoneNumber);
    }

    /**
     * Creates a new UserProfile for the user.
     */
    public Observable<User> createProfile(UserProfile userProfile) {
        return getService().createProfile(apiMapper.mapUserProfile(userProfile))
                .map(modelMapper::mapUser);
    }

    /**
     * Use an external authorize link (received by email).
     */
    public Observable<Void> useAuthorizeLink(String uuid) {
        return getService().useAuthorizeLink(new LinkActionJson(uuid));
    }

    /**
     * Login by sending a challenge signature and the associated challenge public key.
     */
    public Observable<KeySet> login(
            final ChallengeSignature challengeSignature,
            final ChallengePublicKey challengePublicKey
    ) {
        return getService()
                .login(apiMapper.mapLogin(challengeSignature, challengePublicKey));
    }

    /**
     * Login with compatibility, no-challenge method.
     */
    public Observable<KeySet> loginCompatWithoutChallenge() {
        return getService().loginCompatWithoutChallenge();
    }

    /**
     * Notify houston of a client logout. Not a critical request, in fact its just so Houston
     * can now IN ADVANCE of a session expiration (otherwise will have to wait until a new create
     * session to invalidate old ones). So, its a fire and forget call.
     */
    public Observable<Void> notifyLogout(String authHeader) {
        return getService().notifyLogout(authHeader);
    }

    /**
     * Updates the GCM token for the current user.
     */
    public Observable<Void> updateFcmToken(@NotNull String fcmToken) {
        return getService().updateFcmToken(fcmToken);
    }

    /**
     * Fetch a notification report after a given id. This endpoint supports pagination and might
     * not return all the existing notifications, it's up to the caller to make subsequent calls.
     */
    public Observable<NotificationReport> fetchNotificationReportAfter(
            @Nullable Long notificationId) {

        return getService().fetchNotificationReportAfter(notificationId)
                .map(modelMapper::mapNotificationReport);
    }

    /**
     * Confirm the delivery of all the notifications up to a given id.
     */
    public Observable<Void> confirmNotificationsDeliveryUntil(
            final long notificationId,
            final String deviceModel,
            final String osVersion,
            final String appStatus) {

        return getService().confirmNotificationsDeliveryUntil(
                notificationId, deviceModel, osVersion, appStatus
        );
    }

    /**
     * Fetches the current user.
     */
    public Observable<Pair<User, UserPreferences>> fetchUser() {
        return getService()
                .fetchUserInfo()
                .map(modelMapper::mapUserAndPreferences);
    }

    /**
     * Updates the public key set.
     */
    public Observable<PublicKeySet> updatePublicKeySet(PublicKey basePublicKey) {
        final PublicKeySetJson publicKeySet = new PublicKeySetJson(
                apiMapper.mapPublicKey(basePublicKey)
        );

        return getService()
                .updatePublicKeySet(publicKeySet)
                .map(modelMapper::mapPublicKeySet);
    }

    /**
     * Fetches the user's external addresses record.
     */
    public Observable<ExternalAddressesRecord> fetchExternalAddressesRecord() {
        return getService().fetchExternalAddressesRecord();
    }

    /**
     * Updates the user's external addresses record in houston, returning the new one.
     */
    public Observable<ExternalAddressesRecord> updateExternalAddressesRecord(int maxUsedIndex) {
        final ExternalAddressesRecord externalAddressesRecord = apiMapper
                .mapExternalAddressesRecord(maxUsedIndex);

        return getService().updateExternalAddressesRecord(externalAddressesRecord);
    }

    /**
     * Uploads a new profile picture.
     */
    public Observable<UserProfile> uploadProfilePicture(Uri fileUri) {
        final ContentUriRequestBody requestBody = new ContentUriRequestBody(
                context,
                MediaType.parse("image/*"),
                fileUri
        );

        return getService().uploadProfilePicture(requestBody)
                .map(modelMapper::mapUserProfile);
    }

    /**
     * Update username.
     */
    public Observable<User> updateUsername(String firstName, String lastName) {
        final UserProfileJson user = new UserProfileJson(
                firstName,
                lastName
        );

        return updateUser(user);
    }

    /**
     * Update primary currency.
     */
    public Observable<User> updatePrimaryCurrency(CurrencyUnit currencyUnit) {
        final UserJson user = new UserJson();
        user.primaryCurrency = currencyUnit;

        return getService()
                .changeCurrency(user)
                .map(modelMapper::mapUser);
    }

    private Observable<User> updateUser(UserProfileJson user) {
        return getService()
                .updateUser(user)
                .map(modelMapper::mapUser);
    }

    /**
     * Report a successful emergency kit export.
     */
    public Observable<Void> reportEmergencyKitExported(EmergencyKitExport export) {
        return getService()
                .reportEmergencyKitExported(apiMapper.mapEmergencyKitExport(export));
    }

    /**
     * Send a feedback message.
     */
    public Observable<Void> submitFeedback(String feedbackContent) {
        return getService()
                .submitFeedback(apiMapper.mapFeedback(feedbackContent));
    }

    /**
     * Fetches the contact list for a given user id.
     */
    public Observable<List<Contact>> fetchContacts() {
        return getService().fetchContacts()
                .flatMap(Observable::from)
                .map(modelMapper::mapContact)
                .toList();
    }

    /**
     * Report changes to local PhoneNumbers to Houston.
     */
    public Observable<List<Contact>> patchPhoneNumbers(Diff<String> phoneNumberHashDiff) {
        return getService().patchPhoneNumbers(DiffJson.fromDiff(phoneNumberHashDiff))
                .flatMap(Observable::from)
                .map(modelMapper::mapContact)
                .toList();
    }

    /**
     * Make an integrity check with Houston.
     */
    public Observable<IntegrityStatus> checkIntegrity(IntegrityCheck integrityCheck) {
        return getService().checkIntegrity(integrityCheck);
    }

    /**
     * Sends a new operation to houston, that will return it with more data, including a transaction
     * draft.
     */
    public Observable<OperationCreated> newOperation(
            final OperationWithMetadata operation,
            final List<String> outpoints,
            final MusigNonces musigNonces
    ) {
        return getService()
                .newOperation(apiMapper.mapOperation(operation, outpoints, musigNonces))
                .map(modelMapper::mapOperationCreated);
    }

    /**
     * Updates the receiver metadata for an incoming operation.
     */
    public Completable updateOperationMetadata(OperationWithMetadata operation) {

        final UpdateOperationMetadataJson json =
                new UpdateOperationMetadataJson(operation.getReceiverMetadata());

        return getService().updateOperationMetadata(operation.getId(), json);
    }

    /**
     * Pushes a raw transaction to Houston.
     *
     * @param txHex        The bitcoinj's transaction.
     * @param operationHid The houston operation id.
     */
    public Observable<TransactionPushed> pushTransaction(
            @Nullable String txHex,
            long operationHid
    ) {
        if (txHex != null) {
            return getService()
                    .pushTransaction(new RawTransaction(txHex), operationHid)
                    // This can happen if we determine the payment can't actually be made. If so, we
                    // fail fast to avoid broadcasting a transaction saving the user on miner fees.
                    .compose(ObservableFn.replaceHttpException(
                            ErrorCode.SWAP_FAILED,
                            SwapFailedException::new
                    ))
                    .map(modelMapper::mapTransactionPushed);
        } else {
            return getService()
                    .pushTransaction(operationHid) // empty body when txHex is not given
                    .map(modelMapper::mapTransactionPushed);
        }
    }

    /**
     * Fetches the operation history for this user from Houston.
     */
    public Observable<List<OperationWithMetadata>> fetchOperations() {
        return getService()
                .fetchOperations()
                .flatMap(Observable::from)
                .map(modelMapper::mapOperation)
                .toList();
    }

    /**
     * Fetches the transaction size estimation progression for the next transaction.
     */
    public Observable<NextTransactionSize> fetchNextTransactionSize() {
        return getService().fetchNextTransactionSize().map(modelMapper::mapNextTransactionSize);
    }

    /**
     * Request a new Submarine Swap.
     */
    public Observable<SubmarineSwap> createSubmarineSwap(SubmarineSwapRequest request) {

        return getService()
                .createSubmarineSwap(apiMapper.mapSubmarineSwapRequest(request))
                .compose(ObservableFn.replaceHttpException(
                        ErrorCode.INVALID_INVOICE,
                        e -> new InvalidInvoiceException(request.invoice, e)
                ))
                .compose(ObservableFn.replaceHttpException(
                        ErrorCode.INVALID_INVOICE_AMOUNT,
                        e -> new InvoiceMissingAmountException(request.invoice, e)
                ))
                .compose(ObservableFn.replaceHttpException(
                        ErrorCode.INVOICE_ALREADY_USED,
                        e -> new InvoiceAlreadyUsedException(request.invoice, e)
                ))
                .compose(ObservableFn.replaceHttpException(
                        ErrorCode.UNREACHABLE_NODE,
                        e -> new UnreachableNodeException(request.invoice, e)
                ))
                .compose(ObservableFn.replaceHttpException(
                        ErrorCode.NO_PAYMENT_ROUTE,
                        e -> new NoPaymentRouteException(request.invoice, e)
                ))
                .compose(ObservableFn.replaceHttpException(
                        ErrorCode.INVOICE_EXPIRED,
                        e -> new InvoiceExpiredException(request.invoice, e)
                ))
                .compose(ObservableFn.replaceHttpException(
                        ErrorCode.INVOICE_EXPIRES_TOO_SOON,
                        e -> new InvoiceExpiresTooSoonException(request.invoice, e)
                ))
                .compose(ObservableFn.replaceHttpException(
                        ErrorCode.CYCLICAL_SWAP,
                        e -> new CyclicalSwapError(request.invoice, e)
                ))
                .compose(ObservableFn.replaceHttpException(
                        ErrorCode.AMOUNTLESS_INVOICES_NOT_SUPPORTED,
                        e -> new InvoiceMissingAmountException(request.invoice, e)
                ))
                .map(modelMapper::mapSubmarineSwap);
    }

    /**
     * Fetch latest expected fee.
     */
    public Observable<RealTimeData> fetchRealTimeData() {
        return getService().fetchRealTimeData().map(modelMapper::mapRealTimeData);
    }

    /**
     * Request a challenge from Houston.
     */
    public Observable<Optional<Challenge>> requestChallenge(ChallengeType type) {
        return getService()
                .requestChallenge(type.name())
                .map(modelMapper::mapChallenge)
                .map(Optional::ofNullable);
    }

    /**
     * Setup a challenge in Houston.
     * Note: this should no longer be used to setup Recovery Code's challenge. We've a new 2-step
     * flow for that.
     */
    public Observable<SetupChallengeResponse> setupChallenge(ChallengeSetup setup) {
        Preconditions.checkArgument(setup.type != ChallengeType.RECOVERY_CODE);
        return getService()
                .setupChallenge(apiMapper.mapChallengeSetup(setup));
    }

    /**
     * Start a challenge setup in Houston.
     */
    public Observable<SetupChallengeResponse> startChallengeSetup(final ChallengeSetup setup) {
        Preconditions.checkArgument(setup.type == ChallengeType.RECOVERY_CODE);
        return getService()
                .startChallengeSetup(apiMapper.mapChallengeSetup(setup));
    }

    /**
     * Finish/Verify a challenge setup in Houston.
     */
    public Completable finishChallengeSetup(
            final ChallengeType challengeType,
            final ChallengePublicKey challengePublicKey
    ) {

        Preconditions.checkArgument(challengeType == ChallengeType.RECOVERY_CODE);
        return getService().finishChallengeSetup(new ChallengeSetupVerifyJson(
                challengeType,
                Encodings.bytesToHex(challengePublicKey.toBytes())
        ));
    }

    /**
     * Start a password change (which involves email authorization and a new challenge setup).
     */
    public Observable<PendingChallengeUpdate> beginPasswordChange(ChallengeSignature challengeSig) {
        return getService()
                .beginPasswordChange(apiMapper.mapChallengeSignature(challengeSig))
                .map(modelMapper::mapPendingChallengeUpdate);
    }

    /**
     * Use an external confirm (change password) link (received by email).
     */
    public Observable<Void> useConfirmLink(String uuid) {
        return getService().useConfirmLink(new LinkActionJson(uuid));
    }

    /**
     * Start a password change (which involves email authorization and a new challenge setup).
     * Note: Ignoring SetupChallengeResponse (and by extension MuunKey attr) as we no longer
     * encrypt Muun Key with password, so the encrypted MuunKey does not change on password change.
     */
    public Observable<Void> finishPasswordChange(String uuid, ChallengeSetup challengeSetup) {
        return getService()
                .finishPasswordChange(apiMapper.mapChallengeUpdate(uuid, challengeSetup))
                .map(RxHelper::toVoid);
    }

    /**
     * Fetch data required for the challenge key crypto migration.
     */
    public Observable<ChallengeKeyUpdateMigration> fetchChallengeKeyMigrationData() {
        return getService()
                .fetchChallengeKeyUpdateMigration()
                .map(modelMapper::mapChalengeKeyUpdateMigration);
    }

    /**
     * Fetch the Muun key fingerprint required for the related migration.
     */
    public Observable<String> fetchMuunKeyFingerprint() {
        return getService()
                .fetchKeyFingerprintMigration()
                .map(it -> it.muunKeyFingerprint);
    }

    /**
     * Register invoices for incoming swaps.
     */
    public Completable registerInvoices(final List<Invoice.InvoiceSecret> invoices) {
        return Observable.from(invoices)
                .map(apiMapper::mapUserInvoice)
                .toList()
                .toSingle()
                .flatMapCompletable(i -> getService().registerInvoices(i));
    }

    /**
     * Fetch fulfillment data for an incoming swap.
     */
    public Single<IncomingSwapFulfillmentData> fetchFulfillmentData(final String incomingSwap) {

        return getService().fetchFulfillmentData(incomingSwap)
                .map(apiMapper::mapFulfillmentData);
    }

    /**
     * Push the fulfillment TX for an incoming swap.
     */
    public Completable pushFulfillmentTransaction(final String incomingSwap,
                                                  final RawTransaction rawTransaction) {

        return getService().pushFulfillmentTransaction(incomingSwap, rawTransaction);
    }

    /**
     * Expire an invoice by payment hash.
     */
    public Completable expireInvoice(final Sha256Hash paymentHash) {
        return getService().expireInvoice(paymentHash.toString());
    }

    /**
     * Fulfill a debt only incoming swap.
     */
    public Completable fulfillIncomingSwap(final String incomingSwap, final byte[] preimage) {
        return getService().fulfillIncomingSwap(
                incomingSwap, new PreimageJson(Encodings.bytesToHex(preimage))
        );
    }

    /**
     * Update user preferences.
     */
    public Completable updateUserPreferences(final UserPreferences prefs) {
        return getService().updateUserPreferences(prefs.toJson());
    }
}
