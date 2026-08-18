import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

/**
 * Release signing, kept out of the repository: see keystore.properties.example for the four
 * values and README for how to make the keystore. Absent — which it is on any machine but the
 * one that holds the key — the release build stays unsigned rather than failing, so everything
 * else about it can still be built and checked.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

kotlin {
    // kotlinx-datetime 0.8 hands back the stdlib's kotlin.time.Instant, which Kotlin 2.2 still
    // marks experimental. Opting in once here rather than annotating every file that reads a
    // clock; the type is stable in practice and stops being experimental in 2.3.
    compilerOptions { optIn.add("kotlin.time.ExperimentalTime") }

    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    /**
     * The device and the simulator, which are both arm64 but not the same arm64. No iosX64:
     * Compose Multiplatform stopped publishing it at 1.11, so the Intel-Mac simulator is off
     * the table — adding the target back would only fail to resolve. Configuring these costs
     * nothing on Linux; building them needs Xcode, so the link tasks only run on the Mac.
     */
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "OpenBreath"
            isStatic = true
            // without it the compiler warns that it cannot infer one and falls back to the bundle
            // name, which is what a crash report would then have to be symbolicated against
            binaryOption("bundleId", "io.github.wlemkens.openbreath.shared")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            // JetBrains' multiplatform build of androidx.lifecycle, versioned in step with it:
            // 2.10+ still wants compileSdk 37 / AGP 9, so the ceiling that held before the port
            // holds after it. 2.9.6 has LifecycleEventEffect and builds on 36.
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6")
            implementation("androidx.datastore:datastore-preferences:1.2.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.13.0")
        }
        // The session engine, the stored shape and the cue are checked on every platform that
        // runs them, not just Android. StorageTest especially: a backup written on a phone has to
        // open on an iPhone, so the strings older releases really wrote are now decoded by both
        // compilers rather than one.
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // JUnit stays for what is genuinely Android's: the icon generator, the interruption-filter
        // arithmetic, and the reminder scheduling still sitting on java.time.
        val androidUnitTest by getting {
            dependencies { implementation("junit:junit:4.13.2") }
        }
    }
}

/**
 * The generated accessor for commonMain/composeResources — `Res.readBytes("files/…")`.
 *
 * Named explicitly rather than left to the default, which derives a package from the group and
 * module and so changes if either is ever renamed. The two bowls are reached through this on both
 * platforms: they are recordings and not arithmetic, so unlike the bell they cannot simply be
 * computed on each side.
 */
compose.resources {
    packageOfResClass = "io.github.wlemkens.openbreath.media"
    publicResClass = false
}

android {
    namespace = "io.github.wlemkens.openbreath"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.wlemkens.openbreath"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
        }
    }
}
