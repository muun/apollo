package io.muun.apollo.presentation.ui.base.di

import javax.inject.Scope

/**
 * A scoping annotation to indicate objects whose lifetime is the same as the life of a view.
 */
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class PerView 