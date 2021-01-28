
# We don't use jackson DOM serialization
-dontwarn com.fasterxml.jackson.databind.ext.DOMSerializer

-dontwarn sun.misc.Unsafe

-keepnames class io.muun.apollo.domain.model.ExchangeRateWindow
-keep class io.muun.apollo.domain.model.ExchangeRateWindow { *; }
-keepclassmembers class io.muun.apollo.domain.model.ExchangeRateWindow { *; }
-keepclassmembernames class io.muun.apollo.domain.model.ExchangeRateWindow { *; }

-keepnames class io.muun.apollo.domain.model.NextTransactionSize
-keep class io.muun.apollo.domain.model.NextTransactionSize { *; }
-keepclassmembers class io.muun.apollo.domain.model.NextTransactionSize { *; }
-keepclassmembernames class io.muun.apollo.domain.model.NextTransactionSize { *; }

-keepnames class io.muun.apollo.domain.model.SignupDraft
-keep class io.muun.apollo.domain.model.SignupDraft { *; }
-keepclassmembers class io.muun.apollo.domain.model.SignupDraft { *; }
-keepclassmembernames class io.muun.apollo.domain.model.SignupDraft { *; }

-keepnames class io.muun.apollo.domain.model.LoginWithRc
-keep class io.muun.apollo.domain.model.LoginWithRc { *; }
-keepclassmembers class io.muun.apollo.domain.model.LoginWithRc { *; }
-keepclassmembernames class io.muun.apollo.domain.model.LoginWithRc { *; }

# Preserve all JSON preference classes and attributes:
-keepnames class io.muun.apollo.data.preferences.stored.**
-keep class io.muun.apollo.data.preferences.stored.** { *; }
-keepclassmembers class io.muun.apollo.data.preferences.stored.** { *; }
-keepclassmembernames class io.muun.apollo.data.preferences.stored.** { *; }

-keepnames class io.muun.apollo.data.preferences.UserPreferencesRepository$StoredUserPreferences
-keep class io.muun.apollo.data.preferences.UserPreferencesRepository$StoredUserPreferences { *; }
-keepclassmembers class io.muun.apollo.data.preferences.UserPreferencesRepository$StoredUserPreferences { *; }
-keepclassmembernames class io.muun.apollo.data.preferences.UserPreferencesRepository$StoredUserPreferences { *; }

-keepattributes InnerClasses

-keepnames class io.muun.apollo.presentation.analytics.**
-keep class io.muun.apollo.presentation.analytics.** { *; }
-keepclassmembers class io.muun.apollo.presentation.analytics.** { *; }
-keepclassmembernames class io.muun.apollo.presentation.analytics.** { *; }

-keep public enum io.muun.apollo.data.os.secure_storage.SecureStorageMode$** {
    **[] $VALUES;
    public *;
}

# Don't mangle classes user for serialization
-keep class io.muun.apollo.data.preferences.ForwardingPoliciesRepository$StoredForwardingPolicy { *; }
