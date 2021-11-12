package io.muun.common.net;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sets a custom retry policy for a retrofit HTTP method definition.
 * Simple example:
 *
 * <pre>{@code
 *      // '@' CANNOT be escaped with HTML code
 *      &#64;Retry(baseInterval = 50, count = 3)
 *      Call<ResponseBody> example(&#64;Path("id") int id);
 *
 * }</pre>
 * Set a 50 millisecond base delay with 3 retries.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NetworkRetry {

    /**
     * Number of max retries that will attempted.
     */
    int count() default 3;

    /**
     * The base time to wait between retries in milliseconds.
     *
     * <p>Delay between retries follows an exponential backoff policy with this number as base.
     */
    long baseIntervalInMs() default 10;
}
