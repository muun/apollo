package io.muun.apollo.data.net;

import io.muun.apollo.data.external.Globals;
import io.muun.apollo.data.serialization.dates.ApolloZonedDateTime;
import io.muun.apollo.domain.libwallet.Invoice;
import io.muun.apollo.domain.model.BitcoinAmount;
import io.muun.apollo.domain.model.EmergencyKitExport;
import io.muun.apollo.domain.model.IncomingSwapFulfillmentData;
import io.muun.apollo.domain.model.OperationWithMetadata;
import io.muun.apollo.domain.model.PublicProfile;
import io.muun.apollo.domain.model.SubmarineSwapRequest;
import io.muun.apollo.domain.model.user.UserProfile;
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
import io.muun.common.api.OperationJson;
import io.muun.common.api.PasswordSetupJson;
import io.muun.common.api.PhoneNumberJson;
import io.muun.common.api.PublicKeyJson;
import io.muun.common.api.PublicProfileJson;
import io.muun.common.api.StartEmailSetupJson;
import io.muun.common.api.SubmarineSwapRequestJson;
import io.muun.common.api.UserInvoiceJson;
import io.muun.common.api.UserProfileJson;
import io.muun.common.crypto.ChallengeType;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.model.PhoneNumber;
import io.muun.common.model.challenge.ChallengeSetup;
import io.muun.common.model.challenge.ChallengeSignature;
import io.muun.common.utils.Encodings;

import libwallet.MusigNonces;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
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
            final MusigNonces musigNonces
    ) {

        final Long outputAmountInSatoshis = operation.getSwap() != null
                ? operation.getSwap().getFundingOutput().getOutputAmountInSatoshis()
                : operation.getAmount().inSatoshis;

        final List<String> userPublicNoncesHex = new LinkedList<>();
        for (int i = 0; i < outpoints.size(); i++) {
            userPublicNoncesHex.add(musigNonces.getPubnonceHex(i));
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
                ApolloZonedDateTime.of(operation.getCreationDate()),
                operation.getSwap() != null ? operation.getSwap().houstonUuid : null,
                operation.getSenderMetadata(),
                operation.getReceiverMetadata(),
                outpoints,
                false, // TODO: Set it to proper value.
                userPublicNoncesHex
        );
    }

    /**
     * Map client information.
     */
    public ClientJson mapClient(String buildType, int version, final String bigQueryPseudoId) {
        return new ClientJson(
                ClientTypeJson.APOLLO,
                buildType,
                version,
                Globals.INSTANCE.getDeviceName(),
                TimeZone.getDefault().getRawOffset() / 1000L,
                Locale.getDefault().toString(),
                bigQueryPseudoId
        );
    }

    /**
     * Map a CreateFirstSession object.
     */
    public CreateFirstSessionJson mapCreateFirstSession(String buildType,
                                                        int version,
                                                        String gcmToken,
                                                        PublicKey basePublicKey,
                                                        CurrencyUnit primaryCurrency,
                                                        String bigQueryPseudoId) {

        return new CreateFirstSessionJson(
                mapClient(buildType, version, bigQueryPseudoId),
                gcmToken,
                primaryCurrency,
                mapPublicKey(basePublicKey),
                null // No longer needed
        );
    }

    /**
     * Map a CreateLoginSession object.
     */
    public CreateLoginSessionJson mapCreateLoginSession(String buildType,
                                                        int clientVersion,
                                                        String gcmToken,
                                                        String email,
                                                        String bigQueryPseudoId) {

        return new CreateLoginSessionJson(
                mapClient(buildType, clientVersion, bigQueryPseudoId),
                gcmToken,
                email
        );
    }

    /**
     * Map a CreateRcLoginSession object.
     */
    public CreateRcLoginSessionJson mapCreateRcLoginSession(String buildType,
                                                            int clientVersion,
                                                            String gcmToken,
                                                            String rcChallengePublicKeyHex,
                                                            String bigQueryPseudoId) {

        return new CreateRcLoginSessionJson(
                mapClient(buildType, clientVersion, bigQueryPseudoId),
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
        return new SubmarineSwapRequestJson(request.invoice, request.swapExpirationInBlocks);
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
            final IncomingSwapFulfillmentDataJson json) {

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
    private ExportEmergencyKitJson.Method mapExportMethod(@NotNull EmergencyKitExport.Method meth) {
        switch (meth) {
            case UNKNOWN: return null;
            case DRIVE: return ExportEmergencyKitJson.Method.DRIVE;
            case MANUAL: return ExportEmergencyKitJson.Method.MANUAL;
            case ICLOUD: return ExportEmergencyKitJson.Method.ICLOUD;
            default:
                throw new MissingCaseError(meth);
        }
    }
}
