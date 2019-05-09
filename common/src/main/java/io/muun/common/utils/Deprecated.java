package io.muun.common.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface Deprecated {

    /**
     * Keep record of the client version at which some part of the code was deprecated.
     */
    int atVersion() default 0;

    /**
     * Keep record of the version of Apollo at which some part of the code was deprecated.
     */
    int atApolloVersion() default 0;

    /**
     * Keep record of the version of Falcon at which some part of the code was deprecated.
     */
    int atFalconVersion() default 0;
}
