# Taken from kotlinx.serialization Github repo. https://github.com/Kotlin/kotlinx.serialization

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt # core serialization annotations

# kotlinx-serialization-json specific. Add this if you have java.lang.NoClassDefFoundError kotlinx.serialization.json.JsonObjectSerializer
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Change here com.yourcompany.yourpackage
-keep,includedescriptorclasses class io.muun.apollo.**$$serializer { *; }
-keepclassmembers class io.muun.apollo.** {
    *** Companion;
}
-keepclasseswithmembers class io.muun.apollo.** {
    kotlinx.serialization.KSerializer serializer(...);
}