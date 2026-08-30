# VibeLink ProGuard Rules

# Keep Kotlin serialization
-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** { *** *; }
-keep @kotlinx.serialization.Serializable class * { *; }
-keep class kotlinx.serialization.** { *; }

# Keep Room entities
-keep class com.vibeapp.core.db.entity.** { *; }
-keep class com.vibeapp.data.model.** { *; }

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclasseswithmembers class * { @dagger.hilt.android.lifecycle.HiltViewModel <init>(...); }

# Keep OkHttp WebSocket
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Remove debug logging in release
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
}

# Keep Android Keystore related
-keep class java.security.** { *; }
-keep class javax.crypto.** { *; }
-keep class android.security.keystore.** { *; }
