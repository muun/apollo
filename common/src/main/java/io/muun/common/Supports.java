package io.muun.common;

public class Supports {

    public interface OutputAmountInSatoshis {
        int APOLLO = 35;
        int FALCON = 1;
    }

    public interface PreOpenChannel {
        int APOLLO = 45;
        int FALCON = 22;
    }

    // ON-RELEASE: check that these versions are right
    public interface CreationDateInUserInfo {
        int APOLLO = 46;
        int FALCON = 24;
    }

    public interface SignupVerifyEmail {
        int APOLLO = 46;
        int FALCON = 1;
    }

    // ON-RELEASE: check that these versions are right
    public interface BlockchainHeight {
        int APOLLO = 47;
        int FALCON = 25;
    }
}
