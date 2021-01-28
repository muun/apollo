package io.muun.apollo.presentation.app.di;

import java.lang.annotation.Retention;
import javax.inject.Scope;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A scoping annotation to indicate objects whose lifetime is the same as the life of the
 * application.
 */
@Scope
@Retention(RUNTIME)
public @interface PerApplication {
}
