package io.muun.apollo.data.net;

import io.muun.apollo.data.serialization.dates.ApolloZonedDateTime;
import io.muun.apollo.domain.model.BitcoinAmount;
import io.muun.apollo.domain.model.HardwareWallet;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.PublicProfile;
import io.muun.apollo.domain.model.UserProfile;
import io.muun.common.api.BitcoinAmountJson;
import io.muun.common.api.ChallengeSetupJson;
import io.muun.common.api.ChallengeSignatureJson;
import io.muun.common.api.ChallengeUpdateJson;
import io.muun.common.api.ExternalAddressesRecord;
import io.muun.common.api.FeedbackJson;
import io.muun.common.api.HardwareWalletJson;
import io.muun.common.api.OperationJson;
import io.muun.common.api.PhoneNumberJson;
import io.muun.common.api.PublicKeyJson;
import io.muun.common.api.PublicProfileJson;
import io.muun.common.api.SignupJson;
import io.muun.common.api.UserProfileJson;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.model.PhoneNumber;
import io.muun.common.model.challenge.ChallengeSetup;
import io.muun.common.model.challenge.ChallengeSignature;
import io.muun.common.utils.Encodings;

import java.util.UUID;

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
    public OperationJson mapOperation(@NotNull Operation operation) {

        final Long outputAmountInSatoshis = operation.swap != null
                ? operation.swap.getOutputAmountInSatoshis()
                : operation.amount.inSatoshis;

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
                operation.hardwareWalletHid,
                mapBitcoinAmount(operation.amount),
                mapBitcoinAmount(operation.fee),
                outputAmountInSatoshis,
                operation.exchangeRateWindowHid,
                operation.description,
                operation.status,
                ApolloZonedDateTime.of(operation.creationDate),
                operation.swap != null ? operation.swap.houstonUuid : null
        );
    }

    /**
     * Create an API Signup.
     */
    public SignupJson mapSignup(CurrencyUnit primaryCurrency,
                                PublicKey basePublicKey,
                                ChallengeSetup passwordChallengeSetup) {

        return new SignupJson(
                primaryCurrency,
                mapPublicKey(basePublicKey),
                mapChallengeSetup(passwordChallengeSetup)
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
        return new FeedbackJson(content);
    }

    /**
     * Create a HardwareWallet.
     */
    public HardwareWalletJson mapHardwareWallet(HardwareWallet wallet) {
        return new HardwareWalletJson(
                wallet.hid,
                wallet.brand,
                wallet.model,
                wallet.label,
                mapPublicKey(wallet.basePublicKey),
                ApolloZonedDateTime.of(wallet.createdAt),
                ApolloZonedDateTime.of(wallet.lastPairedAt),
                wallet.isPaired
        );
    }
}
