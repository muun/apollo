# Proguard configuration for Jackson 2.x (fasterxml package instead of codehaus package)

-keep class com.fasterxml.jackson.databind.ObjectMapper {
    public <methods>;
    protected <methods>;
}
-keep class com.fasterxml.jackson.databind.ObjectWriter {
    public ** writeValueAsString(**);
}

-keep class com.fasterxml.jackson.annotation.** { *; }

-keepnames class com.fasterxml.jackson.** { *; }

-dontwarn com.fasterxml.jackson.databind.**

-keep interface com.Foo

-keep,allowobfuscation public class * implements com.Foo

-keepnames class com.fasterxml.jackson.annotation.**

-keepclassmembernames class com.fasterxml.jackson.annotation.**