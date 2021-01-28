
# Keep the currency providers

-keep interface javax.money.spi.CurrencyProviderSpi
-keepnames interface javax.money.spi.CurrencyProviderSpi
-keepclassmembernames interface javax.money.spi.CurrencyProviderSpi

-keep class * implements javax.money.spi.CurrencyProviderSpi { *; }
-keepnames class * implements javax.money.spi.CurrencyProviderSpi
-keepclassmembernames class * implements javax.money.spi.CurrencyProviderSpi

-dontwarn org.javamoney.moneta.internal.**
-dontwarn org.javamoney.moneta.function.**

# Since we use Monetary.getDefaultRounding()
-keep interface javax.money.spi.RoundingProviderSpi
-keepnames interface javax.money.spi.RoundingProviderSpi
-keepclassmembernames interface javax.money.spi.RoundingProviderSpi
-keep class * implements org.javamoney.moneta.internal.DefaultRoundingProvider { *; }
-keepnames class * implements org.javamoney.moneta.internal.DefaultRoundingProvider
-keepclassmembernames class * implements org.javamoney.moneta.internal.DefaultRoundingProvider