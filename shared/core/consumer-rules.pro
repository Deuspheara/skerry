# Published with :shared:core (AGP KMP consumer keep rules). Only -dontwarn: no -keep rules.
# kotlinx.serialization: its own consumer rules (serialization-core's META-INF/com.android.tools/r8) keep the
# Companion.serializer() lookups and $$serializer descriptors; broader keeps only blocked R8.
-dontwarn kotlinx.serialization.**

# Ktor core: optional JVM-only integrations are absent on Android
-dontwarn org.slf4j.**
-dontwarn io.ktor.util.debug.**
-dontwarn java.lang.management.**
