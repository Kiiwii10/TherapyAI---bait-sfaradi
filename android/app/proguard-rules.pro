# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile


# Add project specific ProGuard rules here.
# For more details, see http://developer.android.com/guide/developing/tools/proguard.html

# Keep line number information for debugging stack traces (Upload mapping.txt to Play Console)
-keepattributes SourceFile,LineNumberTable
# -renamesourcefileattribute SourceFile # Optional

# ===== ANDROID UI COMPONENTS & SERVICES =====
-keep public class com.example.therapyai.ui.welcome.LoginActivity { *; }
-keep public class com.example.therapyai.ui.welcome.WelcomeActivity { *; }
-keep public class com.example.therapyai.ui.** extends androidx.appcompat.app.AppCompatActivity { *; }
-keep public class com.example.therapyai.ui.** extends androidx.fragment.app.Fragment { *; }
-keep public class * extends androidx.lifecycle.ViewModel { *; }
-keep class com.example.therapyai.data.remote.NotificationService extends com.google.firebase.messaging.FirebaseMessagingService { *; }
-keepclassmembers class * extends android.app.Activity { public void *(android.view.View); }
-keepclassmembers class * extends androidx.fragment.app.Fragment { public void *(android.view.View); }
-keep class * implements android.os.Parcelable { public static final android.os.Parcelable$Creator *; }

# ===== RETROFIT, GSON, OKHTTP & DATA MODELS =====

# --- General Attributes for Reflection-based Libraries ---
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault, *Annotation*

# Keep generic type information (ESSENTIAL for Retrofit)
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod

# --- Retrofit Specific ---
# This rule is critical: it keeps the interface, its methods, AND importantly,
# it implies that the parameter types and return types (like LoginRequest, LoginResponse) are used.
-keep interface com.example.therapyai.data.remote.TherapyApiService {
    <methods>; # Keeps all methods and their original signatures
}

# Keep all Retrofit service interfaces and their methods
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation interface retrofit2.http.*

# Keep generic signature for Retrofit interfaces (CRITICAL for fixing your error)
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault
-keep interface * extends retrofit2.Call

# Specific rule to prevent the "Call return type must be parameterized" error
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep Retrofit method annotations
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Additional rule to preserve generic information for Call<T>
-keep class retrofit2.Call { *; }
-keep class retrofit2.Response { *; }

# --- Model Classes (Request/Response objects for Retrofit/Gson) ---
# These are the most important rules to prevent shrinking of classes used by Retrofit/Gson.
# The { *; } keeps all members (fields and methods) and the default constructor if one exists.
# It also prevents the class itself from being removed if it's only used via reflection.

-keep public class com.example.therapyai.data.remote.models.LoginRequest {
    public <init>(...); # Explicitly keep all constructors
    *; # Keep all fields and methods
}
-keep public class com.example.therapyai.data.remote.models.LoginResponse {
    public <init>(...); # Explicitly keep all constructors
    *; # Keep all fields and methods
}
-keep public class com.example.therapyai.data.remote.models.RefreshTokenRequest {
    public <init>(...); *;
}
-keep public class com.example.therapyai.data.remote.models.RefreshTokenResponse {
    public <init>(...); *;
}
# Add ALL other request/response model classes used in TherapyApiService here explicitly with the same pattern:
-keep public class com.example.therapyai.data.remote.models.DeviceRegistrationRequest { <init>(...); *; }
-keep public class com.example.therapyai.data.remote.models.PasswordChangeRequest { <init>(...); *; }
-keep public class com.example.therapyai.data.remote.models.PasswordResetResponse { <init>(...); *; }
-keep public class com.example.therapyai.data.remote.models.ProcessedDataEntryResponse { <init>(...); *; }
-keep public class com.example.therapyai.data.remote.models.ProfileResponse { <init>(...); *; }
-keep public class com.example.therapyai.data.remote.models.FinalSessionDetailResponse { <init>(...); *; }
# If FinalSessionDetailResponse has inner classes like TimedNoteEntry, SentimentScoreEntry, they need to be kept too:
-keep public class com.example.therapyai.data.remote.models.FinalSessionDetailResponse$TimedNoteEntry { <init>(...); *; }
-keep public class com.example.therapyai.data.remote.models.FinalSessionDetailResponse$SentimentScoreEntry { <init>(...); *; }
-keep public class com.example.therapyai.data.remote.models.SessionSubmissionResponse { <init>(...); *; }
-keep public class com.example.therapyai.data.remote.models.SessionSummaryResponse { <init>(...); *; }
-keep public class com.example.therapyai.data.remote.models.TranscriptDetailResponse { <init>(...); *; }
-keep public class com.example.therapyai.data.remote.models.TranscriptSentenceResponse { <init>(...); *; }

# General model package keep (can be a fallback, but explicit is better)
# -keep class com.example.therapyai.data.remote.models.** { *; } # You can try with this too if explicit ones fail
-keep class com.example.therapyai.data.local.entities.** { *; }


# --- Gson Specific (some of these are general and good for any class used with Gson) ---
-keep class * extends com.google.gson.TypeAdapter { *; }
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }

# Keep fields annotated with @SerializedName (important if field names differ from JSON keys)
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken { *; }
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken { *; }


# --- OkHttp Specific ---
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Kotlin Specific ---
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepattributes KotlinOUGHTiling
-keep class kotlin.Metadata { *; }
-keepclassmembers class * { public static ** Companion; }
-keepclassmembers class * {
    ** synthesis$default(java.lang.Object, int, kotlin.jvm.internal.DefaultConstructorMarker);
    ** execute$default(java.lang.Object, int, kotlin.jvm.internal.DefaultConstructorMarker);
}

# --- Additional Retrofit rules to prevent Call adapter issues ---
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.** <methods>;
}
-keepclassmembers class * {
    @retrofit2.http.** <methods>;
}

# Platform calls Class.forName on types which do not exist on Android to determine platform
-dontnote retrofit2.Platform
# Platform used when running on RoboVM or other Android runtime
-dontnote retrofit2.Platform$IOS$MainThreadExecutor
# Platform used when running on Java 8 VMs
-dontwarn retrofit2.Platform$Java8
# Retain service method parameters when optimizing
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# --- Other Important Classes from your code ---
-keep class com.example.therapyai.data.remote.ApiServiceProvider { *; }
-keep class com.example.therapyai.data.remote.TokenAuthenticator { *; }
-keep class com.example.therapyai.data.remote.TherapyApiImpl { *; }
-keep class com.example.therapyai.data.remote.TherapyApiImpl$ApiCallback { *; }