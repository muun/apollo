# Crashlytics 2.+

-keep class com.crashlytics.** { *; }
-keep class com.crashlytics.android.**
-keepattributes SourceFile, LineNumberTable, *Annotation*

# If you are using custom exceptions, add this line so that custom exception types are skipped during obfuscation:
# -keep public class * extends java.lang.Exception

# The line above is the crashlytics recommended way to whitelist this. We are wrapping some exceptions with io.muun.Cause
# that extends from Throwable, using this more permisive way we prevent crashlytics obfuscating it
-keep public class * extends java.lang.Throwable

# For Fabric to properly de-obfuscate your crash reports, you need to remove this line from your ProGuard config:
-printmapping mapping.txt

-keep class io.fabric.sdk.android.** { *; }
-dontwarn io.fabric.sdk.android.**