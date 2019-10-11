package io.muun.apollo.data.net;

import io.muun.apollo.data.net.base.BaseClient;
import io.muun.apollo.data.net.okio.ContentUriRequestBody;
import io.muun.apollo.domain.errors.InvalidInvoiceAmountException;
import io.muun.apollo.domain.errors.InvalidInvoiceException;
import io.muun.apollo.domain.errors.InvalidSwapException;
import io.muun.apollo.domain.errors.InvoiceAlreadyUsedException;
import io.muun.apollo.domain.errors.InvoiceExpiresTooSoonException;
import io.muun.apollo.domain.errors.NoPaymentRouteException;
import io.muun.apollo.domain.model.ChallengeKeyUpdateMigration;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.HardwareWallet;
import io.muun.apollo.domain.model.NextTransactionSize;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.OperationCreated;
import io.muun.apollo.domain.model.PendingChallengeUpdate;
import io.muun.apollo.domain.model.PublicKeySet;
import io.muun.apollo.domain.model.RealTimeData;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.apollo.domain.model.SubmarineSwapRequest;
import io.muun.apollo.domain.model.TransactionPushed;
import io.muun.apollo.domain.model.User;
import io.muun.apollo.domain.model.UserPhoneNumber;
import io.muun.apollo.domain.model.UserProfile;
import io.muun.common.Optional;
import io.muun.common.api.ClientTypeJson;
import io.muun.common.api.DiffJson;
import io.muun.common.api.ExternalAddressesRecord;
import io.muun.common.api.HardwareWalletWithdrawalJson;
import io.muun.common.api.IntegrityCheck;
import io.muun.common.api.IntegrityStatus;
import io.muun.common.api.KeySet;
import io.muun.common.api.PhoneConfirmation;
import io.muun.common.api.PublicKeySetJson;
import io.muun.common.api.RawTransaction;
import io.muun.common.api.SessionJson;
import io.muun.common.api.SetupChallengeResponse;
import io.muun.common.api.SignupJson;
import io.muun.common.api.UserJson;
import io.muun.common.api.UserProfileJson;
import io.muun.common.api.beam.notification.NotificationJson;
import io.muun.common.api.error.ErrorCode;
import io.muun.common.api.houston.HoustonService;
import io.muun.common.crypto.ChallengePublicKey;
import io.muun.common.crypto.ChallengeType;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.crypto.hwallet.HardwareWalletState;
import io.muun.common.crypto.schemes.TransactionSchemeSubmarineSwap;
import io.muun.common.model.CreateSessionOk;
import io.muun.common.model.Diff;
import io.muun.common.model.PhoneNumber;
import io.muun.common.model.VerificationType;
import io.muun.common.model.challenge.Challenge;
import io.muun.common.model.challenge.ChallengeSetup;
import io.muun.common.model.challenge.ChallengeSignature;
import io.muun.common.rx.ObservableFn;
import io.muun.common.rx.RxHelper;
import io.muun.common.utils.Encodings;

import android.content.Context;
import android.net.Uri;
import okhttp3.MediaType;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import rx.Observable;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.money.CurrencyUnit;
import javax.validation.constraints.NotNull;


public class HoustonClient extends BaseClient<HoustonService> {

    private final ModelObjectsMapper modelMapper;
    private final ApiObjectsMapper apiMapper;
    private final Context context;

    /**
     * Constructor.
     */
    @Inject
    public HoustonClient(ModelObjectsMapper modelMapper,
                         ApiObjectsMapper apiMapper,
                         Context context) {

        super(HoustonService.class);

        this.modelMapper = modelMapper;
        this.apiMapper = apiMapper;
        this.context = context;
    }

    /**
     * Creates a session.
     */
    public Observable<CreateSessionOk> createSession(@NotNull String email,
                                                     @NotNull String buildType,
                                                     @Nonnegative int version,
                                                     @NotNull String gcmRegistrationToken) {

        final SessionJson session = new SessionJson(
                UUID.randomUUID().toString(),
                email,
                buildType,
                version,
                gcmRegistrationToken,
                ClientTypeJson.APOLLO
        );

        return getService().createSession(session)
                .map(modelMapper::mapCreateSessionOk);
    }

    public Observable<UserPhoneNumber> createPhone(PhoneNumber phoneNumber) {
        return getService().createPhone(apiMapper.mapPhoneNumber(phoneNumber))
                .map(modelMapper::mapUserPhoneNumber);
    }

    public Observable<Void> resendVerificationCode(@NotNull VerificationType verificationType) {
        return getService().resendVerificationCode(verificationType).map(RxHelper::toVoid);
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

    public Observable<User> createProfile(UserProfile userProfile) {
        return getService().createProfile(apiMapper.mapUserProfile(userProfile))
                .map(modelMapper::mapUser);
    }

    /**
     * Sign-ups an user.
     */
    public Observable<PublicKey> signup(String encryptedRootPrivateKey,
                                        CurrencyUnit primaryCurrency,
                                        PublicKey basePublicKey,
                                        ChallengePublicKey passwordSecretPublicKey,
                                        byte[] passwordSecretSalt) {

        final ChallengeSetup passwordChallengeSetup = new ChallengeSetup(
                ChallengeType.PASSWORD,
                passwordSecretPublicKey,
                passwordSecretSalt,
                encryptedRootPrivateKey,
                ChallengeType.getVersion(ChallengeType.PASSWORD)
        );

        final SignupJson signup = apiMapper.mapSignup(
                primaryCurrency,
                basePublicKey,
                passwordChallengeSetup
        );

        return getService()
                .signup(signup)
                .map(signupOkJson -> modelMapper.mapPublicKey(signupOkJson.cosigningPublicKey));
    }

    /**
     * Login by sending a challenge signature.
     */
    public Observable<KeySet> login(ChallengeSignature challengeSignature) {
        return getService()
                .login(apiMapper.mapChallengeSignature(challengeSignature));
    }

    /**
     * Login with compatibility, no-challenge method.
     */
    public Observable<KeySet> loginCompatWithoutChallenge() {
        return getService().loginCompatWithoutChallenge();
    }

    /**
     * Login with compatibility, no-challenge method.
     */
    public Observable<Void> notifyLogout() {
        return getService().notifyLogout();
    }

    /**
     * Updates the GCM token for the current user.
     */
    public Observable<Void> updateFcmToken(@NotNull String fcmToken) {
        return getService().updateFcmToken(fcmToken);
    }

    /**
     * Fetch all the notifications after a given id.
     */
    public Observable<List<NotificationJson>> fetchNotificationsAfter(
            @Nullable Long notificationId) {

        return getService().fetchNotificationsAfter(notificationId);
    }

    /**
     * Confirm the delivery of all the notifications up to a given id.
     */
    public Observable<Void> confirmNotificationsDeliveryUntil(long notificationId) {

        return getService().confirmNotificationsDeliveryUntil(notificationId);
    }

    /**
     * Create a beam session authorized to send notification to this device.
     */
    public Observable<String> createReceivingSession(String satelliteSessionUuid) {

        return getService().createReceivingSession(satelliteSessionUuid);
    }

    /**
     * Expire a beam session authorized to send notifications to this device.
     */
    public Observable<Void> expireReceivingSession(String sessionUuid) {
        return getService().expireReceivingSession(sessionUuid);
    }

    /**
     * Fetches the current user.
     */
    public Observable<User> fetchUser() {
        return getService()
                .fetchUserInfo()
                .map(modelMapper::mapUser);
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
     * Fetches a single contact.
     */
    public Observable<Contact> fetchContact(long contactHid) {
        return getService().fetchContact(contactHid)
                .map(modelMapper::mapContact);
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
    public Observable<OperationCreated> newOperation(Operation operation) {
        return getService()
                .newOperation(apiMapper.mapOperation(operation))
                .map(modelMapper::mapOperationCreated);
    }

    /**
     * Pushes a raw transaction to Houston.
     *
     * @param tx  The bitcoinj's transaction.
     * @param operationHid The houston operation id.
     */
    public Observable<TransactionPushed> pushTransaction(Transaction tx, long operationHid) {
        final String transactionHex = Encodings.bytesToHex(tx.unsafeBitcoinSerialize());

        return getService()
                .pushTransaction(new RawTransaction(transactionHex), operationHid)
                .map(modelMapper::mapTransactionPushed);
    }

    /**
     * Fetches the operation history for this user from Houston.
     */
    public Observable<List<Operation>> fetchOperations() {
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
     * Submit a new signed withdrawal operation.
     */
    public Observable<OperationCreated> newWithdrawalOperation(Operation operation,
                                                               String signedTransaction,
                                                               List<Long> inputAmounts) {

        final HardwareWalletWithdrawalJson withdrawal = new HardwareWalletWithdrawalJson(
                apiMapper.mapOperation(operation),
                new RawTransaction(signedTransaction),
                inputAmounts
        );

        return getService()
                .newWithdrawalOperation(withdrawal)
                .map(modelMapper::mapOperationCreated);

    }

    /**
     * Request a new Submarine Swap.
     */
    public Observable<SubmarineSwap> createSubmarineSwap(SubmarineSwapRequest request,
                                                         PublicKeyPair publicKeyPair,
                                                         NetworkParameters network) {

        return getService()
                .createSubmarineSwap(apiMapper.mapSubmarineSwapRequest(request))
                .compose(ObservableFn.replaceHttpException(
                        ErrorCode.INVALID_INVOICE,
                        e -> new InvalidInvoiceException(request.invoice, e)
                ))
                .compose(ObservableFn.replaceHttpException(
                        ErrorCode.INVALID_INVOICE_AMOUNT,
                        e -> new InvalidInvoiceAmountException(request.invoice, e)
                ))
                .compose(ObservableFn.replaceHttpException(
                        ErrorCode.INVOICE_ALREADY_USED,
                        e -> new InvoiceAlreadyUsedException(request.invoice, e)
                ))
                .compose(ObservableFn.replaceHttpException(
                        ErrorCode.NO_PAYMENT_ROUTE,
                        e -> new NoPaymentRouteException(request.invoice, e)
                ))
                .compose(ObservableFn.replaceHttpException(
                        ErrorCode.INVOICE_EXPIRES_TOO_SOON,
                        e -> new InvoiceExpiresTooSoonException(request.invoice, e)
                ))
                .doOnNext(submarineSwapJson -> {
                    final boolean isValid = TransactionSchemeSubmarineSwap.validateSwap(
                            request.invoice,
                            publicKeyPair,
                            submarineSwapJson,
                            network
                    );

                    if (!isValid) {
                        throw new InvalidSwapException(submarineSwapJson.swapUuid);
                    }
                })
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
     */
    public Observable<SetupChallengeResponse> setupChallenge(ChallengeSetup setup) {
        return getService()
                .setupChallenge(apiMapper.mapChallengeSetup(setup));
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
     * Start a password change (which involves email authorization and a new challenge setup).
     */
    public Observable<SetupChallengeResponse> finishPasswordChange(String uuid,
                                                                   ChallengeSetup challengeSetup) {
        return getService()
                .finishPasswordChange(apiMapper.mapChallengeUpdate(uuid, challengeSetup));
    }

    /**
     * Create or update an existing HardwareWallet.
     */
    public Observable<HardwareWallet> createOrUpdateHardwareWallet(HardwareWallet wallet) {
        return getService()
                .createOrUpdateHardwareWallet(apiMapper.mapHardwareWallet(wallet))
                .map(modelMapper::mapHardwareWallet);
    }

    /**
     * Unpair (aka remove) a HardwareWallet.
     */
    public Observable<HardwareWallet> unpairHardwareWallet(HardwareWallet wallet) {
        return getService()
                .unpairHardwareWallet(wallet.getHid())
                .map(modelMapper::mapHardwareWallet);
    }

    /**
     * Fetch HardwareWallets from Houston.
     */
    public Observable<List<HardwareWallet>> fetchHardwareWallets() {
        return getService().fetchHardwareWallets()
                .flatMap(Observable::from)
                .map(modelMapper::mapHardwareWallet)
                .toList();
    }

    /**
     * Fetch a HardwareWallet state.
     */
    public Observable<HardwareWalletState> fetchHardwareWalletState(HardwareWallet wallet) {
        return getService()
                .fetchHardwareWalletState(wallet.getHid())
                .map(modelMapper::mapHardwareWalletState);
    }

    /**
     * Fetch data required for the challenge key crypto migration.
     */
    public Observable<ChallengeKeyUpdateMigration> fetchChallengeKeyMigrationData() {
        return getService()
                .fetchChallengeKeyUpdateMigration()
                .map(modelMapper::mapChalengeKeyUpdateMigration);
    }
}
