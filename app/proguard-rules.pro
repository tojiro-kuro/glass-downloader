# Glass Downloader - R8/ProGuard

-keepattributes SourceFile,LineNumberTable,Signature,Exceptions,*Annotation*
-optimizationpasses 5

# Kotlin / Coroutines
-dontwarn kotlinx.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }

# NewPipe Extractor
-keep class org.schabi.newpipe.extractor.** { *; }
-keep interface org.schabi.newpipe.extractor.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn com.grack.nanojson.**
-dontwarn javax.annotation.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# AndroidX / Compose keep generated
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver

# Serialization
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep model/data classes used by app
-keep class io.glassdown.app.data.** { *; }
-keep class io.glassdown.app.download.** { *; }
