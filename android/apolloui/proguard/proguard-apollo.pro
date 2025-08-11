
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

-keepnames class io.muun.apollo.domain.analytics.**
-keep class io.muun.apollo.domain.analytics.** { *; }
-keepclassmembers class io.muun.apollo.domain.analytics.** { *; }
-keepclassmembernames class io.muun.apollo.domain.analytics.** { *; }

-keep public enum io.muun.apollo.data.os.secure_storage.SecureStorageMode$** {
    **[] $VALUES;
    public *;
}

# Don't mangle classes user for serialization
-keep class io.muun.apollo.data.preferences.ForwardingPoliciesRepository$StoredForwardingPolicy { *; }
-keep class io.muun.apollo.data.preferences.BackgroundTimesRepository$StoredBackgroundEvent { *; }

# Intentionally removed an old protobuf from an unused transitive dependecy.
-dontwarn com.google.protobuf.AbstractMessage$Builder
-dontwarn com.google.protobuf.AbstractMessage$BuilderParent
-dontwarn com.google.protobuf.AbstractMessage
-dontwarn com.google.protobuf.Descriptors$Descriptor
-dontwarn com.google.protobuf.Descriptors$EnumDescriptor
-dontwarn com.google.protobuf.Descriptors$EnumValueDescriptor
-dontwarn com.google.protobuf.Descriptors$FieldDescriptor
-dontwarn com.google.protobuf.Descriptors$FileDescriptor$InternalDescriptorAssigner
-dontwarn com.google.protobuf.Descriptors$FileDescriptor
-dontwarn com.google.protobuf.Descriptors$OneofDescriptor
-dontwarn com.google.protobuf.ExtensionRegistry
-dontwarn com.google.protobuf.GeneratedMessageV3$Builder
-dontwarn com.google.protobuf.GeneratedMessageV3$BuilderParent
-dontwarn com.google.protobuf.GeneratedMessageV3$FieldAccessorTable
-dontwarn com.google.protobuf.GeneratedMessageV3
-dontwarn com.google.protobuf.Message$Builder
-dontwarn com.google.protobuf.Message
-dontwarn com.google.protobuf.MessageOrBuilder
-dontwarn com.google.protobuf.ProtocolMessageEnum
-dontwarn com.google.protobuf.RepeatedFieldBuilderV3
-dontwarn com.google.protobuf.SingleFieldBuilderV3
-dontwarn com.google.protobuf.UnknownFieldSet$Builder
-dontwarn com.google.protobuf.UnknownFieldSet

# Have to keep this to use UDS as transport for gRPC.
-keepclassmembers class io.grpc.okhttp.OkHttpChannelBuilder {
    io.grpc.okhttp.OkHttpChannelBuilder forTarget(java.lang.String, io.grpc.ChannelCredentials);
    io.grpc.okhttp.OkHttpChannelBuilder socketFactory(javax.net.SocketFactory);
}

# Prevent Protobuf-generated message fields from being stripped or renamed
# Required for our grpc-protobuf impl
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
  <fields>;
}