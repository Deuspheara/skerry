# ---- R8 rules (full mode). Keep this file minimal: only what reflection/serialization needs. ----
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, RuntimeVisibleAnnotations

# kotlinx.serialization: its own consumer rules (serialization-core's META-INF/com.android.tools/r8) keep the
# Companion.serializer() lookups and $$serializer descriptors; broader app-side keeps only blocked R8.
-dontwarn kotlinx.serialization.**

# Ktor + OkHttp engine: optional JVM-only integrations are absent on Android
-dontwarn org.slf4j.**
-dontwarn io.ktor.util.debug.**
-dontwarn java.lang.management.**
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
