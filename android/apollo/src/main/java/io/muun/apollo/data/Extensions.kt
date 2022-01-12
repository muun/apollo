package io.muun.apollo.data

import io.muun.apollo.data.serialization.dates.ApolloZonedDateTime
import io.muun.apollo.domain.model.ExchangeRateWindow
import io.muun.common.dates.MuunZonedDateTime
import io.muun.common.model.ExchangeRateProvider
import org.threeten.bp.ZonedDateTime

fun ExchangeRateProvider.getRateWindow(): ExchangeRateWindow =
    ExchangeRateWindow.fromJson(this.rateWindow)

fun MuunZonedDateTime?.toApolloModel(): ZonedDateTime? {
    return if (this == null) {
        null
    } else {
        (this as ApolloZonedDateTime).dateTime
    }
}
