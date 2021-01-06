package io.muun.common.api.houston;


import io.muun.common.api.ChallengeJson;
import io.muun.common.api.ChallengeKeyUpdateMigrationJson;
import io.muun.common.api.ChallengeSetupJson;
import io.muun.common.api.ChallengeSignatureJson;
import io.muun.common.api.ChallengeUpdateJson;
import io.muun.common.api.Contact;
import io.muun.common.api.CreateFirstSessionJson;
import io.muun.common.api.CreateFirstSessionOkJson;
import io.muun.common.api.CreateLoginSessionJson;
import io.muun.common.api.CreateRcLoginSessionJson;
import io.muun.common.api.CreateSessionOkJson;
import io.muun.common.api.CreateSessionRcOkJson;
import io.muun.common.api.DiffJson;
import io.muun.common.api.ExportEmergencyKitJson;
import io.muun.common.api.ExternalAddressesRecord;
import io.muun.common.api.FeedbackJson;
import io.muun.common.api.IncomingSwapFulfillmentDataJson;
import io.muun.common.api.IntegrityCheck;
import io.muun.common.api.IntegrityStatus;
import io.muun.common.api.KeyFingerprintMigrationJson;
import io.muun.common.api.KeySet;
import io.muun.common.api.LinkActionJson;
import io.muun.common.api.NextTransactionSizeJson;
import io.muun.common.api.OperationCreatedJson;
import io.muun.common.api.OperationJson;
import io.muun.common.api.PasswordSetupJson;
import io.muun.common.api.PendingChallengeUpdateJson;
import io.muun.common.api.PhoneConfirmation;
import io.muun.common.api.PhoneNumberJson;
import io.muun.common.api.PublicKeySetJson;
import io.muun.common.api.PublicProfileJson;
import io.muun.common.api.RawTransaction;
import io.muun.common.api.RealTimeData;
import io.muun.common.api.SendEncryptedKeysJson;
import io.muun.common.api.SetupChallengeResponse;
import io.muun.common.api.StartEmailSetupJson;
import io.muun.common.api.SubmarineSwapJson;
import io.muun.common.api.SubmarineSwapRequestJson;
import io.muun.common.api.TransactionPushedJson;
import io.muun.common.api.UserInvoiceJson;
import io.muun.common.api.UserJson;
import io.muun.common.api.UserProfileJson;
import io.muun.common.api.beam.notification.NotificationJson;
import io.muun.common.model.UserPreferences;
import io.muun.common.model.VerificationType;

import okhttp3.RequestBody;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import rx.Completable;
import rx.Observable;
import rx.Single;

import java.util.List;
import javax.annotation.Nullable;


public interface HoustonService {

    // ---------------------------------------------------------------------------------------------
    // Authentication and Sessions:

    @POST("sessions-v2/first")
    Observable<CreateFirstSessionOkJson> createFirstSession(@Body CreateFirstSessionJson session);

    @POST("sessions-v2/login")
    Observable<CreateSessionOkJson> createLoginSession(@Body CreateLoginSessionJson session);

    @POST("sessions-v2/email/start")
    Observable<Void> startEmailSetup(@Body StartEmailSetupJson startEmailSetup);

    @POST("sessions-v2/email/finish")
    Observable<Void> useVerifyLink(@Body LinkActionJson linkActionJson);

    @POST("sessions-v2/password")
    Observable<Void> setUpPassword(@Body PasswordSetupJson passwordSetup);

    @POST("sessions/current/authorize")
    Observable<Void> useAuthorizeLink(@Body LinkActionJson linkActionJson);

    @POST("sessions/current/login")
    Observable<KeySet> login(@Body ChallengeSignatureJson challengeSignature);

    @POST("sessions/current/login/compat")
    Observable<KeySet> loginCompatWithoutChallenge();

    @POST("sessions/logout")
    Observable<Void> notifyLogout(@Header("Authorization") String authHeader);

    @PUT("sessions/current/gcm-token")
    Observable<Void> updateFcmToken(@Body String gcmToken);

    @GET("sessions/notifications")
    Observable<List<NotificationJson>> fetchNotificationsAfter(
            @Query("after") @Nullable Long notificationId);

    @PUT("sessions/notifications/confirm")
    Observable<Void> confirmNotificationsDeliveryUntil(
            @Query("until") Long notificationId,
            @Query("deviceModel") String deviceModel,
            @Query("osVersion") String osVersion,
            @Query("appStatus") String appStatus
    );

    @GET("user/challenge")
    Observable<ChallengeJson> requestChallenge(@Query("type") String challengeType);

    @POST("user/challenge/setup")
    Observable<SetupChallengeResponse> setupChallenge(@Body ChallengeSetupJson challengeSetupJson);

    // ---------------------------------------------------------------------------------------------
    // Recovery Code Only Login:

    @POST("sessions-v2/recovery-code/start")
    Observable<ChallengeJson> createRecoveryCodeLoginSession(@Body CreateRcLoginSessionJson body);

    @POST("sessions-v2/recovery-code/finish")
    Observable<CreateSessionRcOkJson> loginWithRecoveryCode(@Body ChallengeSignatureJson signature);

    @POST("sessions-v2/recovery-code/authorize")
    Observable<Void> authorizeLoginWithRecoveryCode(@Body LinkActionJson linkActionJson);

    @GET("sessions-v2/current/key-set")
    Observable<KeySet> getKeySet();


    // ---------------------------------------------------------------------------------------------
    // User and Profile:

    @GET("user")
    Observable<UserJson> fetchUserInfo();

    @PUT("user/public-key-set")
    Observable<PublicKeySetJson> updatePublicKeySet(@Body PublicKeySetJson publicKeySet);

    @GET("user/external-addresses-record")
    Observable<ExternalAddressesRecord> fetchExternalAddressesRecord();

    @PUT("user/external-addresses-record")
    Observable<ExternalAddressesRecord> updateExternalAddressesRecord(
            @Body ExternalAddressesRecord externalAddressesRecord
    );

    @Multipart
    @PUT("user/profile/picture")
    Observable<PublicProfileJson> uploadProfilePicture(@Part("picture") RequestBody file);

    @PATCH("user/profile")
    Observable<UserJson> updateUser(@Body UserProfileJson user);

    @POST("user/currency")
    Observable<UserJson> changeCurrency(@Body UserJson user);

    @POST("user/password")
    Observable<PendingChallengeUpdateJson> beginPasswordChange(
            @Body ChallengeSignatureJson challengeSignatureJson
    );

    @POST("user/password/authorize")
    Observable<Void> useConfirmLink(@Body LinkActionJson linkActionJson);

    @POST("user/password/finish")
    Observable<SetupChallengeResponse> finishPasswordChange(
            @Body ChallengeUpdateJson challengeUpdateJson
    );

    @POST("user/feedback")
    Observable<Void> submitFeedback(@Body FeedbackJson feedback);

    @POST("user/phone/create")
    Observable<PhoneNumberJson> createPhone(@Body PhoneNumberJson phoneNumberJson);

    @POST("user/phone/resend-code")
    Observable<Void> resendVerificationCode(@Body VerificationType verificationType);

    @PUT("user/phone/confirm")
    Observable<PhoneNumberJson> confirmPhone(@Body PhoneConfirmation phoneConfirmation);

    @POST("user/profile")
    Observable<UserJson> createProfile(@Body UserProfileJson userProfileJson);

    @POST("user/export-keys")
    Observable<Void> sendExportKeysEmail(@Body SendEncryptedKeysJson json);

    @POST("user/emergency-kit/exported")
    Observable<Void> reportEmergencyKitExported(@Body ExportEmergencyKitJson json);

    @PUT("user/preferences")
    Completable updateUserPreferences(@Body UserPreferences userPreferences);

    // ---------------------------------------------------------------------------------------------
    // Contacts:

    @GET("contacts")
    Observable<List<Contact>> fetchContacts();

    @GET("contacts/{contactId}")
    Observable<Contact> fetchContact(@Path("contactId") long contactId);

    @PATCH("watched-phone-numbers")
    Observable<List<Contact>> patchPhoneNumbers(@Body DiffJson<String> phoneNumberHashDiff);


    // ---------------------------------------------------------------------------------------------
    // Real-time data and Operations:

    @GET("realtime")
    Observable<RealTimeData> fetchRealTimeData();

    @GET("operations")
    Observable<List<OperationJson>> fetchOperations();

    @POST("operations")
    Observable<OperationCreatedJson> newOperation(@Body OperationJson operation);

    @PUT("operations/{operationId}/raw-transaction")
    Observable<TransactionPushedJson> pushTransaction(@Path("operationId") Long operationId);

    @PUT("operations/{operationId}/raw-transaction")
    Observable<TransactionPushedJson> pushTransaction(@Body RawTransaction rawTransaction,
                                                      @Path("operationId") Long operationId);

    @GET("operations/next-transaction-size")
    Observable<NextTransactionSizeJson> fetchNextTransactionSize();

    @POST("operations/sswap/create")
    Observable<SubmarineSwapJson> createSubmarineSwap(@Body SubmarineSwapRequestJson data);

    // ---------------------------------------------------------------------------------------------
    // Incoming swaps:

    @POST("incoming-swaps/invoices")
    Completable registerInvoices(@Body List<UserInvoiceJson> invoices);

    @POST("incoming-swaps/{incomingSwapUuid}/fulfillment")
    Single<IncomingSwapFulfillmentDataJson> fetchFulfillmentData(
            @Path("incomingSwapUuid") String incomingSwapUuid);

    @PUT("incoming-swaps/{incomingSwapUuid}/fulfillment")
    Completable pushFulfillmentTransaction(
            @Path("incomingSwapUuid") String incomingSwapUuid,
            @Body RawTransaction tx);

    // ---------------------------------------------------------------------------------------------
    // Other endpoints:

    @POST("integrity/check")
    Observable<IntegrityStatus> checkIntegrity(@Body IntegrityCheck request);

    // ---------------------------------------------------------------------------------------------
    // Migrations:

    @GET("migrations/challenge-keys")
    Observable<ChallengeKeyUpdateMigrationJson> fetchChallengeKeyUpdateMigration();

    @GET("migrations/fingerprints")
    Observable<KeyFingerprintMigrationJson> fetchKeyFingerprintMigration();
}
