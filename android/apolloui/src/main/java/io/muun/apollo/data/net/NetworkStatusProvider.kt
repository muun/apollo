package io.muun.apollo.data.net

import rx.Observable

interface NetworkStatusProvider {

    /**
     * Whether the device is online.
     */
    fun isConnected(): Boolean

    /**
     * Emit isConnected on subscription and then on every change, until unsubscribed.
     */
    fun watchConnected(): Observable<Boolean>
}
