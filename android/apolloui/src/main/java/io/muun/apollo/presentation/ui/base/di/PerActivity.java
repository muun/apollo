package io.muun.apollo.presentation.ui.base.di;

import java.lang.annotation.Retention;
import javax.inject.Scope;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A scoping annotation to indicate objects whose lifetime is the same as the life of an activity.
 */
@Scope
@Retention(RUNTIME)
public @interface PerActivity {
}
