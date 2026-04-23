# ---------------------------------------------------------------------------
# R8 / ProGuard configuration for lap-mobile-android.
#
# Most libraries we depend on (Hilt, Room, Retrofit, Kotlinx Serialization)
# ship consumer-proguard rules inside their AAR/JAR. The rules below are
# defensive safety nets that satisfy the sprint T014 contract and guarantee
# the release build survives future R8 version bumps even if a consumer rule
# regresses.
#
# `proguard-android-optimize.txt` from AGP is applied first; these rules
# layer on top.
# ---------------------------------------------------------------------------

# Keep source-file attributes for meaningful crash stack traces (Sentry,
# Play Console). The `-renamesourcefileattribute` hides real .kt paths.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Hilt / Dagger
# ---------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keepclassmembers class ** extends dagger.hilt.internal.GeneratedComponent { *; }

# ---------------------------------------------------------------------------
# Room — entities rely on reflection, so their field set must survive
# ---------------------------------------------------------------------------
-keep class androidx.room.** { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
}

# ---------------------------------------------------------------------------
# Retrofit 2 (not wired yet; rules here so T148 onwards are pre-covered)
# ---------------------------------------------------------------------------
-dontwarn retrofit2.**
-keep interface retrofit2.Call
-keep class retrofit2.Response
-keepattributes Signature, Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ---------------------------------------------------------------------------
# Kotlinx Serialization — keep @Serializable classes and their serializers.
# Library ships consumer rules since 1.6; kept defensively for pre-1.6 forks.
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
