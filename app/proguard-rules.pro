-keepattributes Signature
-keepclassmembers class * {
    @com.squareup.moshi.* <methods>;
}

# Tink references these compile-time annotations but does not require them at runtime.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
