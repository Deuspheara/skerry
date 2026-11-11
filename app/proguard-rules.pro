# ---- R8 rules (full mode). Keep this file minimal: only what reflection/serialization needs. ----
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, RuntimeVisibleAnnotations

# kotlinx.serialization and Ktor core -dontwarn rules live in shared/core/consumer-rules.pro (published with the module).

# OkHttp engine: optional JVM-only integrations are absent on Android
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**
-dontwarn javax.annotation.**

# Strip verbose logging from release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
