package io.muun.apollo.domain.action

import io.muun.common.api.beam.notification.NotificationJson

fun List<NotificationJson>.mapIds(): List<Long> {
    return this.map { it.id }
}

fun List<Long>.asString(): String {
    return this.map { it.toString() }.toTypedArray().contentToString()
}