package io.muun.apollo.domain.action

import rx.Observable

interface NotificationPoller {

    /**
     * Pull the latest notifications from Houston.
     */
    fun pullNotifications(): Observable<Void>
}