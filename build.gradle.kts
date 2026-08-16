plugins {
    id("com.android.application") version "8.13.2" apply false
    // 2.3 and not 2.2 because of iOS, which had never once been compiled: every Compose
    // Multiplatform 1.11.1 klib — runtime, ui, foundation, animation — is ABI 2.3.0, built by the
    // 2.3.20 compiler, and Kotlin 2.2.21 refuses any klib above ABI 2.2.0. Android never noticed
    // because it consumes the .aar and JVM bytecode has no such gate. Nothing below 2.3.20 can
    // build this app for a phone.
    id("org.jetbrains.kotlin.multiplatform") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
    id("org.jetbrains.compose") version "1.11.1" apply false
}
