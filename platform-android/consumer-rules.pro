# Published with :platform-android (consumer keep rules of an Android library). Only -dontwarn: no -keep rules.
# The module binds the OkHttp engine for Ktor and Coil: its optional JVM-only integrations are absent on Android.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**
-dontwarn javax.annotation.**
