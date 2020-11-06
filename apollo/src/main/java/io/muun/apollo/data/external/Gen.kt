package io.muun.apollo.data.external

import io.muun.apollo.domain.model.ExchangeRateWindow
import io.muun.apollo.domain.model.FeeWindow
import io.muun.apollo.domain.model.ForwardingPolicy
import io.muun.apollo.domain.model.NextTransactionSize
import io.muun.apollo.domain.model.PaymentRequest
import io.muun.apollo.domain.model.SubmarineSwap
import io.muun.apollo.domain.model.SubmarineSwapFees
import io.muun.apollo.domain.model.SubmarineSwapFundingOutput
import io.muun.apollo.domain.model.SubmarineSwapReceiver
import io.muun.apollo.domain.model.User
import io.muun.apollo.domain.model.UserPhoneNumber
import io.muun.apollo.domain.model.UserProfile
import io.muun.common.Optional
import io.muun.common.crypto.hd.MuunAddress
import io.muun.common.exception.MissingCaseError
import io.muun.common.model.DebtType
import io.muun.common.model.PhoneNumber
import io.muun.common.model.SizeForAmount
import io.muun.common.model.UtxoStatus
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.RegTestParams
import org.bitcoinj.params.TestNet3Params
import org.javamoney.moneta.Money
import org.threeten.bp.ZonedDateTime
import javax.money.CurrencyUnit
import javax.money.Monetary
import javax.money.MonetaryAmount
import kotlin.random.Random

object Gen {

    private val CHARSET_ALPHA_LOWER = ('a'..'z')
    private val CHARSET_ALPHA = ('A'..'Z').union(CHARSET_ALPHA_LOWER)
    private val CHARSET_NUM = ('0'..'9')
    private val CHARSET_HEX = CHARSET_NUM.plus('A'..'F')

    /**
     * Get an alphabetic string of random length within a range.
     */
    fun alpha(min: Int, max: Int) = concatGen(min, max) { CHARSET_ALPHA.random() }

    /**
     * Get an alphabetic string of exact length.
     */
    fun alpha(length: Int) = concatGen(length) { CHARSET_ALPHA.random() }

    /**
     * Get an alphabetic lowercase string of random length within a range.
     */
    fun alphaLower(min: Int, max: Int) = concatGen(min, max) { CHARSET_ALPHA_LOWER.random() }

    /**
     * Get a numeric string of random length within a range.
     */
    fun numeric(min: Int, max: Int) = concatGen(min, max) { CHARSET_NUM.random() }

    /**
     * Get a numeric string of exact length.
     */
    fun numeric(length: Int) = concatGen(length) { CHARSET_NUM.random() }

    /**
     * Get a hex string of random length within a range.
     */
    fun hex(min: Int, max: Int) = concatGen(min, max) { CHARSET_HEX.random() }

    /**
     * Get a hex string of exact length.
     */
    fun hex(length: Int) = concatGen(length) { CHARSET_HEX.random() }

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
    fun email() = alpha(3, 20) + "@" + alpha(2, 5) + "." + alphaLower(2, 3)

    /**
     * Get a known PIN.
     */
    fun pin() = listOf(1, 1, 1, 1)

    /**
     * Get a PIN.
     */
    fun randomPin() = listOf(digit(), digit(), digit(), digit())

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
        hasExportedKeys: Boolean = bool(),
        phoneNumber: Optional<UserPhoneNumber> = maybe(Gen::userPhoneNumber),
        profile: Optional<UserProfile> = maybe(Gen::userProfile),
        primaryCurrency: CurrencyUnit = currencyUnit(),
        hasRecoveryCode: Boolean = bool(),
        hasPassword: Boolean = bool(),
        hasP2PEnabled: Boolean = bool(),
        emergencyKitLastExportedAt: ZonedDateTime? = pastDate(),
        createdAt: ZonedDateTime? = pastDate()

    ) = User(
        hid,
        Optional.ofNullable(email),
        isEmailVerified,
        phoneNumber,
        profile,
        primaryCurrency,
        hasRecoveryCode,
        hasPassword,
        hasP2PEnabled,
        hasExportedKeys,
        Optional.ofNullable(emergencyKitLastExportedAt),
        Optional.ofNullable(createdAt)
    )

    /**
     * Get a FeeWindow.
     */
    fun feeWindow(vararg feeRates: Pair<Int, Double>) =
        FeeWindow(
            1,
            ZonedDateTime.now(),
            if (feeRates.isNotEmpty()) mapOf(*feeRates) else mapOf(1 to double(), 5 to double(), 9 to double()),
            1,
            5,
            9
        )

    /**
     * Get an ExchangeRateWindow.
     */
    fun exchangeRateWindow(vararg exchangeRates: Pair<String, Double>) =
        ExchangeRateWindow(
            1,
            ZonedDateTime.now(),
            if (exchangeRates.isNotEmpty()) mapOf(*exchangeRates) else mapOf("USD" to 10.0, "BTC" to 30.0)
        )

    /**
     * Get a SizeForAmount.
     */
    fun sizeForAmount(pair: Pair<Long, Int>) =
        SizeForAmount(
                pair.first,
                pair.second,
                transactionHash() + ":0",
                UtxoStatus.CONFIRMED,
                pair.second
        )

    /**
     * Get a size progression for the next transaction size.
     */
    fun sizeProgression(vararg entries: SizeForAmount) =
        if (entries.isEmpty()) {
            listOf(sizeForAmount(10000L to 240))
        } else {
            listOf(*entries)
        }

    /**
     * Get a NextTransactionSize vector.
     */
    fun nextTransactionSize(vararg entries: SizeForAmount, expectedDebtInSat: Long = 0) =
        NextTransactionSize(sizeProgression(*entries), 1, expectedDebtInSat)

    /**
     * Get a Transaction Hash.
     */
    fun transactionHash() =
        hex(64)

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
     * Get a MuunAddress.
     */
    fun muunAddress() =
        MuunAddress(1, "m/1/2/3", address())

    /**
     * Get a PaymentRequest
     */
    fun payReq(amount: MonetaryAmount = Money.of(0, "USD"),
               feeRate: Double = 10.0,
               takeFeeFromAmount: Boolean = false

    ) = PaymentRequest(
        type = PaymentRequest.Type.TO_ADDRESS,
        amount = amount,
        description = "foo",
        address = address(),
        feeInSatoshisPerByte = feeRate,
        takeFeeFromAmount = takeFeeFromAmount
    )

    fun submarineSwap(outputAmountInSatoshis: Long,
                      sweepFeeInSatoshis: Long = 0,
                      lightningFeeInSatoshis: Long = 0,
                      debtType: DebtType = DebtType.NONE,
                      debtAmountInSatoshis: Long = 0) =
        SubmarineSwap(
            houstonId(),
            "1234-5675",
            lnInvoice(),
            submarineSwapReceiver(),
            submarineSwapFundingOutput(
                outputAmountInSatoshis,
                debtType = debtType,
                debtAmountInSatoshis = debtAmountInSatoshis
            ),
            submarineSwapFees(lightningFeeInSatoshis, sweepFeeInSatoshis),
            futureDate(),
            null,
            null
        )

    fun submarineSwapReceiver() =
        SubmarineSwapReceiver("Some ln node", lnAddress(), lnPublicKey())

    fun submarineSwapFundingOutput(outputAmountInSatoshis: Long,
                                   confirmationsNeeded: Int = 0,
                                   userLockTime: Int = 30,
                                   userRefundAddress: MuunAddress = muunAddress(),
                                   debtType: DebtType = DebtType.NONE,
                                   debtAmountInSatoshis: Long = 0) =
        SubmarineSwapFundingOutput(
            address(),
            outputAmountInSatoshis,
            debtType,
            debtAmountInSatoshis,
            confirmationsNeeded,
            userLockTime,
            userRefundAddress,
            lnPaymentHash(),
            lnPublicKey()
        )

    fun submarineSwapFees(lightningInSats: Long, sweepInSats: Long) =
        SubmarineSwapFees(lightningInSats, sweepInSats)

    fun lnInvoice() =
        """
            lnbcrt100n1pw3vtrqpp57dcqv2lx8a4tk86rc6gptkngwcea0mdqslw5034dgz0kdyvs664qdqqcqzpgu0rt752
            vxd3re6qgknyfa6ff54prg7n42d4d7at2rnpcxvjk2eysypz9f80tu0ew5afvq2mm2drjhk2jshsnjy5g7lffqva
            al792ttqpvserp8
        """.trimIndent().replace("\n", "")

    fun lnAddress() =
        "${lnPublicKey()}:123.456.789.123:8080"

    fun lnPublicKey() =
        "0351114ba294a8802c6c49301a45a99c5ed5d2da71f2a19a53c5b192532fdd744d"

    /** TODO this should be in hex **/
    fun lnPaymentHash() =
        Gen.alpha(32)

    fun forwardingPolicy() =
        ForwardingPolicy(ByteArray(0), 1, 1000, 9)

    /**
     * Get a string of fixed length with characters obtained from a generator.
     */
    fun concatGen(length: Int, gen: () -> Char) = (1..length)
        .map { gen() }
        .fold("", String::plus)

    /**
     * Get a string of a random length with characters obtained from a generator.
     */
    fun concatGen(min: Int, max: Int, gen: () -> Char) = (min..1 + Random.nextInt(min, max))
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

    /**
     * Get a double.
     */
    fun double() = Random.nextDouble()

    fun long(max: Long) = Random.nextLong(max)

    fun futureDate() =
        ZonedDateTime.now().plusDays(long(1000))

    fun pastDate() =
        ZonedDateTime.now().minusDays(long(1000))
}