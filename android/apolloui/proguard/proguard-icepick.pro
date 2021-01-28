#Icepick

-dontwarn icepick.**
-keep class **$$Icepick { *; }
-keepclasseswithmembernames class * {
    @icepick.* <fields>;
}

-keepnames class io.muun.apollo.presentation.ui.** { @icepick.State *;}