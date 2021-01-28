package io.muun.apollo.domain;

import io.muun.common.api.beam.notification.NotificationJson;
import io.muun.common.api.messages.MessageSpec;

import rx.Completable;
import rx.functions.Func1;

class NotificationHandler {

    public final MessageSpec spec;
    public final Func1<NotificationJson, Completable> handler;

    /**
     * Constructor.
     */
    public NotificationHandler(MessageSpec spec, Func1<NotificationJson, Completable> handler) {
        this.spec = spec;
        this.handler = handler;
    }
}
