# Whispr ProGuard rules

# ── Retrofit ──
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ── OkHttp / OkIO ──
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepclasseswithmembers class * {
    @okhttp3.* <methods>;
}

# ── Gson ──
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep all model/data classes (Gson serialization)
-keep class com.whispr.id.data.** { *; }
-keepclassmembers class com.whispr.id.data.** {
    <fields>;
}

# ── SerializedName annotations ──
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Coil (image loading) ──
-keep class coil.** { *; }
-dontwarn coil.**

# ── Compose ──
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keepclasseswithmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ── Kotlin coroutines ──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ── DataStore ──
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ── WebSocket (OkHttp) ──
-keep class okhttp3.internal.ws.** { *; }

# ── WebRTC (critical: R8 strips these without keep → NoClassDefFoundError) ──
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keepclassmembers class org.webrtc.** {
    <fields>;
    <methods>;
}

# ── Firebase Cloud Messaging ──
-keep class com.google.firebase.** { *; }
-keep class com.whispr.id.util.WhisprMessagingService { *; }
-dontwarn com.google.firebase.**

# ── Keep app classes referenced by reflection/navigation ──
-keep class com.whispr.id.navigation.** { *; }
-keep class com.whispr.id.network.** { *; }

# ── Credential Manager / Google ID ──
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keepnames class com.google.android.gms.common.annotation.KeepName

# ── General ──
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
