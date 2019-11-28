package io.muun.apollo.domain.errors

import io.muun.common.exception.PotentialBug
import javax.money.CurrencyQueryBuilder
import javax.money.UnknownCurrencyException
import javax.money.spi.Bootstrap
import javax.money.spi.CurrencyProviderSpi

class MissingCurrencyError(cause: UnknownCurrencyException): MuunError(cause), PotentialBug {

    init {
        val query = CurrencyQueryBuilder.of()
            .setCurrencyCodes(cause.currencyCode)
            .build()

        val services = Bootstrap.getServices(CurrencyProviderSpi::class.java)

        metadata["size(CurrencyProviderSpi[])"] = services.size
        metadata["CurrencyProviderSpi[]"] = services.toTypedArray().contentToString()

        for (spi in services) {
            metadata["Present in ${spi.javaClass.canonicalName}"] = spi.isCurrencyAvailable(query)
        }
    }
}