package io.muun.common.utils;

import io.muun.common.Optional;

public class ExceptionUtils {

    /**
     * Walk the causal chain of `e`, looking for an instance of Throwable class `cls`.
     */
    public static <T> Optional<T> getTypedCause(Throwable e, Class<T> cls) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cls.isInstance(cause)) {
                return Optional.of(cls.cast(cause));
            }
        }

        return Optional.empty();
    }

}
