package io.muun.apollo.domain.errors

import io.muun.apollo.domain.utils.getUnsupportedCurrencies
import io.muun.common.exception.PotentialBug
import java.util.*
import javax.money.CurrencyQueryBuilder
import javax.money.UnknownCurrencyException
import javax.money.spi.Bootstrap
import javax.money.spi.CurrencyProviderSpi

class MissingCurrencyError(
    cause: UnknownCurrencyException,
    regionLocales: List<Locale> = listOf(),
) : MuunError(cause), PotentialBug {

    constructor(cause: UnknownCurrencyException) : this(cause, listOf()) // oh Java!!! OMG

    init {
        val query = CurrencyQueryBuilder.of()
            .setCurrencyCodes(cause.currencyCode)
            .build()

        val services = Bootstrap.getServices(CurrencyProviderSpi::class.java)

        metadata["size(CurrencyProviderSpi[])"] = services.size
        metadata["CurrencyProviderSpi[]"] = services.toTypedArray().contentToString()

        // Null check to avoid NPE inside isCurrencyAvailable for certain providers
        if (cause.currencyCode != null) {
            for (spi in services) {
                val canonicalName = spi.javaClass.canonicalName
                metadata["Present in $canonicalName"] = spi.isCurrencyAvailable(query)
            }

        }

        metadata["cause.currencyCode"] = cause.currencyCode ?: "null"

        metadata["unsupportedCurrencies"] = getUnsupportedCurrencies(this).joinToString(",")
        metadata["regionLocales"] = regionLocales.joinToString(",")
    }
}