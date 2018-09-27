package io.muun.common.api.houston;


import io.muun.common.api.ChallengeJson;
import io.muun.common.api.ChallengeSetupJson;
import io.muun.common.api.ChallengeSignatureJson;
import io.muun.common.api.ChallengeUpdateJson;
import io.muun.common.api.Contact;
import io.muun.common.api.CreateSessionOkJson;
import io.muun.common.api.DiffJson;
import io.muun.common.api.ExternalAddressesRecord;
import io.muun.common.api.FeedbackJson;
import io.muun.common.api.IntegrityCheck;
import io.muun.common.api.IntegrityStatus;
import io.muun.common.api.KeySet;
import io.muun.common.api.NextTransactionSizeJson;
import io.muun.common.api.OperationCreatedJson;
import io.muun.common.api.OperationJson;
import io.muun.common.api.PendingChallengeUpdateJson;
import io.muun.common.api.PhoneConfirmation;
import io.muun.common.api.PhoneNumberJson;
import io.muun.common.api.PublicKeySetJson;
import io.muun.common.api.PublicProfileJson;
import io.muun.common.api.RawTransaction;
import io.muun.common.api.RealTimeData;
import io.muun.common.api.SessionJson;
import io.muun.common.api.SetupChallengeResponse;
import io.muun.common.api.SignupJson;
import io.muun.common.api.SignupOkJson;
import io.muun.common.api.UserJson;
import io.muun.common.api.UserProfileJson;
import io.muun.common.api.NotificationJson;
import io.muun.common.model.VerificationType;

import okhttp3.RequestBody;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import rx.Observable;

import java.util.List;

import javax.annotation.Nullable;


public interface HoustonService {

    // ---------------------------------------------------------------------------------------------
    // Authentication and Sessions:

    @POST("sessions")
    Observable<CreateSessionOkJson> createSession(@Body SessionJson session);

    @POST("sessions/current/login")
    Observable<KeySet> login(@Body ChallengeSignatureJson challengeSignature);

    @POST("sessions/current/login/compat")
    Observable<KeySet> loginCompatWithoutChallenge();

    @POST("sign-up")
    Observable<SignupOkJson> signup(@Body SignupJson signupObject);

    @PUT("session/current/gcm-token")
    Observable<Void> updateGcmToken(@Body String gcmToken);

    @GET("sessions/notifications")
    Observable<List<NotificationJson>> fetchNotificationsAfter(
            @Query("after") @Nullable Long notificationId);

    @PUT("sessions/notifications/confirm")
    Observable<Void> confirmNotificationsDeliveryUntil(@Query("until") Long notificationId);

    @GET("user/challenge")
    Observable<ChallengeJson> requestChallenge(@Query("type") String challengeType);

    @POST("user/challenge/setup")
    Observable<SetupChallengeResponse> setupChallenge(@Body ChallengeSetupJson challengeSetupJson);

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

    @POST("user/password/finish")
    Observable<SetupChallengeResponse> finishPasswordChange(
            @Body ChallengeUpdateJson challengeUpdateJson
    );

    @POST("user/feedback")
    Observable<Void> submitFeedback(@Body FeedbackJson feedback);

    @POST("/user/phone/create")
    Observable<PhoneNumberJson> createPhone(@Body PhoneNumberJson phoneNumberJson);

    @POST("user/phone/resend-code")
    Observable<Void> resendVerificationCode(@Body VerificationType verificationType);

    @PUT("user/phone/confirm")
    Observable<PhoneNumberJson> confirmPhone(@Body PhoneConfirmation phoneConfirmation);

    @POST("user/profile")
    Observable<UserJson> createProfile(@Body UserProfileJson userProfileJson);

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
    Observable<RawTransaction> pushTransaction(@Body RawTransaction rawTransaction,
                                               @Path("operationId") Long operationId);

    @GET("operations/next-transaction-size")
    Observable<NextTransactionSizeJson> fetchNextTransactionSize();

    // ---------------------------------------------------------------------------------------------
    // Other endpoints:

    @POST("integrity/check")
    Observable<IntegrityStatus> checkIntegrity(@Body IntegrityCheck request);
}
