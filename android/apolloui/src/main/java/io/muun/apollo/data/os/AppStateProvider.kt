package io.muun.apollo.data.os

import rx.Observable

enum class AppState { FOREGROUND, BACKGROUND }

interface AppStateProvider {

    fun current(): AppState

    /** Emit the current state on subscription if the app is already started, then every change. */
    fun watch(): Observable<AppState>
}

val AppState.isForeground: Boolean get() = this == AppState.FOREGROUND

fun AppStateProvider.isForeground(): Boolean = current().isForeground

fun AppStateProvider.watchForeground(): Observable<Boolean> = watch().map { it.isForeground }
