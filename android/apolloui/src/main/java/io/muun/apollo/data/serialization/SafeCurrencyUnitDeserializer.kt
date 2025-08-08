package io.muun.apollo.data.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import io.muun.apollo.domain.errors.MissingCurrencyError
import io.muun.apollo.domain.utils.DeprecatedCurrencyUnit
import timber.log.Timber
import java.io.IOException
import javax.money.CurrencyUnit
import javax.money.Monetary
import javax.money.UnknownCurrencyException

class SafeCurrencyUnitDeserializer : JsonDeserializer<CurrencyUnit>() {

    @Throws(IOException::class)
    override fun deserialize(parser: JsonParser, context: DeserializationContext): CurrencyUnit {
        val currencyCode = parser.valueAsString

        if (Monetary.isCurrencyAvailable(currencyCode)) {
            return Monetary.getCurrency(currencyCode)

        } else {
            // In practice, only this type of error should arise.
            Timber.e(MissingCurrencyError(UnknownCurrencyException(currencyCode)))

            // This can happen for example if user primary currency is no longer supported
            // after an app or OS update, or if user has changed to a device that no longer supports
            // their primary currency.
            return DeprecatedCurrencyUnit(currencyCode)
        }
    }
}