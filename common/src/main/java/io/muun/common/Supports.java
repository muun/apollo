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
}
