package io.muun.apollo.external

import io.muun.apollo.domain.model.User
import io.muun.apollo.domain.model.UserPhoneNumber
import io.muun.apollo.domain.model.UserProfile
import io.muun.common.Optional
import io.muun.common.exception.MissingCaseError
import io.muun.common.model.PhoneNumber
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.RegTestParams
import org.bitcoinj.params.TestNet3Params
import javax.money.CurrencyUnit
import javax.money.Monetary
import kotlin.random.Random

object Gen {

    private val CHARSET_ALPHA = ('A'..'Z').union('a'..'z')
    private val CHARSET_NUM = ('0'..'9')

    /**
     * Get an alphabetic string of random length within a range.
     */
    fun alpha(min: Int, max: Int) = concatGen(min, max) { CHARSET_ALPHA.random() }

    /**
     * Get an alphabetic string of exact length.
     */
    fun alpha(length: Int) = concatGen(length) { CHARSET_ALPHA.random() }

    /**
     * Get a numeric string of random length within a range.
     */
    fun numeric(min: Int, max: Int) = concatGen(min, max) { CHARSET_NUM.random() }

    /**
     * Get a numeric string of exact length.
     */
    fun numeric(length: Int) = concatGen(length) { CHARSET_NUM.random() }

    /**
     * Get a Houston ID.
     */
    fun houstonId() = Random.nextLong(10000)

    /**
     * Get a list of CurrencyUnits.
     */
    fun currencyUnits(): List<CurrencyUnit> = listOf("USD", "ARS", "EUR", "BTC")
            .map { Monetary.getCurrency(it) }

    /**
     * Get a CurrencyUnit.
     */
    fun currencyUnit() = currencyUnits().random()

    /**
     * Get a country code.
     */
    fun countryCode() = listOf("AR", "US", "GB").random()

    /**
     * Get an email.
     */
    fun email() = alpha(3, 20) + "@" + alpha(2, 5) + "." + alpha(2, 3)

    /**
     * Get a PIN.
     */
    fun pin() = listOf(digit(), digit(), digit(), digit())

    /**
     * Get a phone number string.
     */
    fun phoneNumber() = PhoneNumber.getExample(countryCode()).get()
            .toE164String()
            .dropLast(6) + numeric(6) // randomize by replacing the last digits, keeping prefixes

    /**
     * Get a UserPhoneNumber.
     */
    fun userPhoneNumber() = UserPhoneNumber(phoneNumber(), Random.nextBoolean())

    /**
     * Get a UserProfile.
     */
    fun userProfile() = UserProfile(alpha(5, 10), alpha(5, 10))


    /**
     * Get a User.
     */
    fun user(
            hid: Long = houstonId(),
            email: String = email(),
            isEmailVerified: Boolean = bool(),
            phoneNumber: Optional<UserPhoneNumber> = maybe(Gen::userPhoneNumber),
            profile: Optional<UserProfile> = maybe(Gen::userProfile),
            primaryCurrency: CurrencyUnit = currencyUnit(),
            hasRecoveryCode: Boolean = bool(),
            hasP2PEnabled: Boolean = bool()

    ) = User(
            hid,
            email,
            isEmailVerified,
            phoneNumber,
            profile,
            primaryCurrency,
            hasRecoveryCode,
            hasP2PEnabled
    )

    /**
     * Get an address.
     */
    fun address() = when (val network = Globals.INSTANCE.network) {
        is MainNetParams -> "1Ph5CSPUHGXRaxzxFjH717VouWsnuCqF6q"
        is TestNet3Params -> "mzEdsQibwTg5KnYbc9y87LbqACCdooRjLP"
        is RegTestParams -> "mtXWDB6k5yC5v7TcwKZHB89SUp85yCKshy"
        else ->
            throw MissingCaseError(network, "NetworkParameters")
    }

    /**
     * Get a string of fixed length with characters obtained from a generator.
     */
    fun concatGen(length: Int, gen: () -> Char) = (1..length)
            .map { gen() }
            .fold("", String::plus)

    /**
     * Get a string of a random length with characters obtained from a generator.
     */
    fun concatGen(min: Int, max: Int, gen: () -> Char) = (min..Random.nextInt(min, max))
            .map { gen() }
            .fold("", String::plus)

    /**
     * Get a random digit.
     */
    fun digit() = Random.nextInt(0, 9)

    /**
     * Get an Optional of a generated value, or an empty optional.
     */
    fun <T> maybe(gen: () -> T) = if (bool()) Optional.empty() else Optional.of(gen())

    /**
     * Get a boolean.
     */
    fun bool() = Random.nextBoolean()
}