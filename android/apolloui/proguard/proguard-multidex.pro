# Needed to make sure these classes are available in the main DEX file for API 19
# See: https://spin.atomicobject.com/2018/07/16/support-kitkat-multidex/

-keep class androidx.test.** {*;}
-keep class io.muun.apollo.utils.** {*;}

-keep class android.support.test.internal** { *; }
-keep class org.junit.** { *; }
-keep public class com.company.application.acceptance.** { *; }
-keep public class com.company.application.integration.** { *; }

# Needed to keep CompositeException on main DEX file (otherwise NoClassDefFoundError is thrown
# at several points for API 19, e.g. at signup/in final step)
-keep class rx.** {*;}
-include proguard-crashlytics-2.pro
-include proguard-square-retrofit2.pro