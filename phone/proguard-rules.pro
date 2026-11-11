# ---- R8 rules (full mode). Keep this file minimal: no -keep rules. ----
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, RuntimeVisibleAnnotations

# kotlinx.serialization and Ktor core -dontwarn rules live in shared/core/consumer-rules.pro; the OkHttp ones in
# platform-android/consumer-rules.pro (both published with their modules).

# Strip verbose logging from release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
