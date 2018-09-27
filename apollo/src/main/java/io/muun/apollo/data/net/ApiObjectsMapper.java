package io.muun.apollo.data.net;

import io.muun.apollo.data.serialization.dates.ApolloZonedDateTime;
import io.muun.apollo.domain.model.UserProfile;
import io.muun.common.api.BitcoinAmount;
import io.muun.common.api.ChallengeSetupJson;
import io.muun.common.api.ChallengeSignatureJson;
import io.muun.common.api.ChallengeUpdateJson;
import io.muun.common.api.ExternalAddressesRecord;
import io.muun.common.api.FeedbackJson;
import io.muun.common.api.OperationJson;
import io.muun.common.api.PhoneNumberJson;
import io.muun.common.api.PublicKeyJson;
import io.muun.common.api.PublicProfileJson;
import io.muun.common.api.SignupJson;
import io.muun.common.api.UserProfileJson;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.dates.MuunZonedDateTime;
import io.muun.common.model.PhoneNumber;
import io.muun.common.model.challenge.ChallengeSetup;
import io.muun.common.model.challenge.ChallengeSignature;
import io.muun.common.utils.Encodings;

import org.threeten.bp.ZonedDateTime;

import java.util.UUID;

import javax.inject.Inject;
import javax.money.CurrencyUnit;
import javax.validation.constraints.NotNull;

public class ApiObjectsMapper {

    @Inject
    public ApiObjectsMapper() {
    }

    /**
     * Create an API zoned date time.
     */
    @NotNull
    public MuunZonedDateTime mapDateTime(@NotNull ZonedDateTime dateTime) {

        return new ApolloZonedDateTime(dateTime);
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
    public PublicProfileJson mapPublicProfile(
            @NotNull io.muun.apollo.domain.model.PublicProfile publicProfile) {

        return new PublicProfileJson(
                publicProfile.hid,
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
    public BitcoinAmount mapBitcoinAmount(
            @NotNull io.muun.apollo.domain.model.BitcoinAmount bitcoinAmount) {

        return new BitcoinAmount(
                bitcoinAmount.inSatoshis,
                bitcoinAmount.inInputCurrency,
                bitcoinAmount.inPrimaryCurrency
        );
    }

    /**
     * Create an API operation.
     */
    @NotNull
    public OperationJson mapOperation(@NotNull io.muun.apollo.domain.model.Operation operation) {

        return new OperationJson(
                UUID.randomUUID().toString(),
                operation.direction,
                operation.isExternal,
                operation.senderProfile != null ? mapPublicProfile(operation.senderProfile) : null,
                operation.senderIsExternal,
                operation.receiverProfile != null
                        ? mapPublicProfile(operation.receiverProfile) : null,
                operation.receiverIsExternal,
                operation.receiverAddress,
                operation.receiverAddressDerivationPath,
                mapBitcoinAmount(operation.amount),
                mapBitcoinAmount(operation.fee),
                operation.exchangeRateWindowHid,
                operation.description,
                operation.status,
                mapDateTime(operation.creationDate)
        );
    }

    /**
     * Create an API Signup.
     */
    public SignupJson createSignup(CurrencyUnit primaryCurrency,
                                   PublicKey basePublicKey,
                                   ChallengeSetup passwordChallengeSetup) {

        return new SignupJson(
                primaryCurrency,
                createPublicKey(basePublicKey),
                createChallengeSetup(passwordChallengeSetup)
        );
    }

    /**
     * Create an API external addresses record.
     */
    @NotNull
    public ExternalAddressesRecord createExternalAddressesRecord(int maxUsedIndex) {

        return new ExternalAddressesRecord(maxUsedIndex);
    }

    /**
     * Create an API public key.
     */
    @NotNull
    public PublicKeyJson createPublicKey(PublicKey publicKey) {

        return new PublicKeyJson(
                publicKey.serializeBase58(),
                publicKey.getAbsoluteDerivationPath()
        );
    }

    /**
     * Create a ChallengeSetup.
     */
    public ChallengeSetupJson createChallengeSetup(ChallengeSetup setup) {
        return new ChallengeSetupJson(
                setup.type,
                Encodings.bytesToHex(setup.publicKey.toBytes()),
                Encodings.bytesToHex(setup.salt),
                setup.encryptedPrivateKey,
                setup.version
        );
    }

    /**
     * Create a ChallengeSetup.
     */
    public ChallengeSignatureJson createChallengeSignature(ChallengeSignature challengeSignature) {
        return new ChallengeSignatureJson(
                challengeSignature.type,
                Encodings.bytesToHex(challengeSignature.bytes)
        );
    }

    /**
     * Create a ChallengeUpdate.
     */
    public ChallengeUpdateJson createChallengeUpdate(String uuid, ChallengeSetup challengeSetup) {
        return new ChallengeUpdateJson(uuid, createChallengeSetup(challengeSetup));
    }

    /**
     * Create a Feedback.
     */
    public FeedbackJson createFeedback(String content) {
        return new FeedbackJson(content);
    }
}
