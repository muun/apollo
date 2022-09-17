package io.muun.common.model;

public enum ChallengeKeyStatus {

    /**
     * Newly created challenge keys, which have a 2-step verification process, and haven't been
     * verified (aka fully set up) yet. Normally can't be used to recover a wallet. We may only
     * allow wallet recovery for the most recent unverified challenge key, provided that the user
     * doesn't have a verified one.
     */
    CREATED,

    /**
     * Verified and fully set up challenge key. Valid for wallet recovery and Emergency Kit
     * generation (and usage).
     */
    VERIFIED,

    /**
     * Special status, reserved for when/if we need to manually alter the status of a challenge key
     * (e.g because a legitimate user contacted us and is blocked out of her account). This may
     * allow users to, briefly, recover access to their account.
     */
    MANUALLY_VERIFIED
}
