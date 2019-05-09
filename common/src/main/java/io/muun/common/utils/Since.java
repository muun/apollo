package io.muun.common.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface Since {

    /**
     * Keep record of the version of Apollo at which some part of the code was added.
     */
    int apolloVersion() default 0;

    /**
     * Keep record of the version of Falcon at which some part of the code was added.
     */
    int falconVersion() default 0;
}