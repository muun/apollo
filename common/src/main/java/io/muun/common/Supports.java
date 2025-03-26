package io.muun.common;

public class Supports {

    private static final int NOT_SUPPORTED = Integer.MAX_VALUE;

    public interface OutputAmountInSatoshis {
        int APOLLO = 35;
        int FALCON = 1;
    }

    public interface PreOpenChannel {
        int APOLLO = 45;
        int FALCON = 22;
    }

    public interface CreationDateInUserInfo {
        int APOLLO = 46;
        int FALCON = 24;
    }

    public interface SignupVerifyEmail {
        int APOLLO = 46;
        int FALCON = 1;
    }

    public interface BlockchainHeight {
        int APOLLO = 47;
        int FALCON = 25;
    }

    public interface SubmarineSwapsV2 {
        int APOLLO = 52;
        int FALCON = 36;
    }

    // ON-RELEASE: check that these versions are right
    public interface SubmarineSwapsV3 {
        int APOLLO = NOT_SUPPORTED;
        int FALCON = NOT_SUPPORTED;
    }

    public interface FcmV1 {
        int APOLLO = 1001;
        int FALCON = 1008;
    }

    public interface TransactionSchemeV2 {
        int APOLLO = 13;
    }

    public interface TransactionSchemeV3 {
        int APOLLO = 15;
    }

    public interface TransactionSchemeV4 {
        int APOLLO = 52;
        int FALCON = 36;
    }

    public interface InvoiceExpiredError {
        int APOLLO = 52;
        int FALCON = NOT_SUPPORTED;
    }

    public interface UserDebt {
        int APOLLO = 63;
        int FALCON = 42;
    }

    // ON-RELEASE: check that these versions are right
    public interface ReSigning {
        int APOLLO = NOT_SUPPORTED;
        int FALCON = NOT_SUPPORTED;
    }

    public interface DynamicFeeTargets {
        int APOLLO = 75;
        int FALCON = 48;
    }

    public interface IncomingSwaps {
        int APOLLO = 200;
        int FALCON = 53;
    }

    public interface SortUtxosByFinality {
        int APOLLO = 75;
        int FALCON = 48;
    }

    public interface ChallengeUserKey {
        int APOLLO = 100;
        int FALCON = 52;
    }

    public interface InvoicesWithoutAmount {
        int APOLLO = 200;
        int FALCON = 53;
    }

    public interface Fingerprint {
        int APOLLO = 200;
        int FALCON = 200;
    }

    public interface FullDebtIncomingSwaps {
        int APOLLO = 300;
        int FALCON = 300;
    }

    public interface IncomingMpp {
        int APOLLO = NOT_SUPPORTED;
        int FALCON = NOT_SUPPORTED;
    }

    public interface NonBrokenUserPreferences {
        int APOLLO = 600;
    }

    public interface Taproot {
        int APOLLO = 700;
        int FALCON = 605;
    }

    public interface PaginatedNotifications {
        int APOLLO = 900;
        int FALCON = 706;
    }

    public interface BrokenTransactionHashProcessing {
        int APOLLO = 901;
    }

    // Up until version 907 (included), apollo didn't support additional feature flags (added after
    // the initial ones) due to a proguard bug regarding MuunFeatureJson
    public interface AdditionalFeatureFlags {
        int APOLLO = 908;
    }

    // This marks the start of the support for Unverified Challenge Keys and the implementation of
    // our 2-step Recovery Code Setup flow, part of our RC Setup Interrupted initiative.
    public interface UnverifiedRecoveryCodes {
        int APOLLO = 911;
        int FALCON = 1000;
    }

    // This marks the time when we migrated from sending totalInternalStorage and totalRamStorage
    // fraud signals only once at session creation, to sending them on each request via http
    // headers.
    public interface RefreshTotalInternalStorageAndRam {
        int APOLLO = 1007;
        int FALCON = 1012;
    }

    // TODO: turn it on as operation updates are supported on Falcon
    public interface OperationUpdatesNotificationsForFalcon {
        int APOLLO = 1;
        int FALCON = NOT_SUPPORTED;
    }

    public interface EffectiveFeesCalculation {
        int APOLLO = 1300;
        int FALCON = 1200;
    }

    public interface SigningAlternativeTransactions {
        int APOLLO = NOT_SUPPORTED;
        int FALCON = NOT_SUPPORTED;
    }
}
