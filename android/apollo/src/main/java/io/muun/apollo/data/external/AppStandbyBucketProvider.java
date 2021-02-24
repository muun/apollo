package io.muun.apollo.data.external;

public interface AppStandbyBucketProvider {

    enum Bucket {
        RARE,
        FREQUENT,
        WORKING_SET,
        ACTIVE,
        UNKNOWN,
        UNAVAILABLE,
    }

    Bucket current();
}
