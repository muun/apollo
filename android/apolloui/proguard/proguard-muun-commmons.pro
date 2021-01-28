-keepnames class io.muun.common.api.**
-keep class io.muun.common.api.** { *; }
-keepclassmembers class io.muun.common.api.** { *; }
-keepclassmembernames class io.muun.common.api.** { *; }

-keepnames class io.muun.common.model.UserPreferences
-keep class io.muun.common.model.UserPreferences { *; }
-keepclassmembers class io.muun.common.model.UserPreferences { *; }
-keepclassmembernames class io.muun.common.model.UserPreferences { *; }

-keepnames class io.muun.common.api.messages.**
-keep class io.muun.common.api.messages.** { *; }
-keepclassmembers class io.muun.common.api.messages.** { *; }
-keepclassmembernames class io.muun.common.api.messages.** { *; }

-keepnames class io.muun.common.model.SizeForAmount
-keep class io.muun.common.model.SizeForAmount { *; }
-keepclassmembers class io.muun.common.model.SizeForAmount { *; }
-keepclassmembernames class io.muun.common.model.SizeForAmount { *; }

-keep public enum io.muun.common.model.SessionStatus$** {
    **[] $VALUES;
    public *;
}