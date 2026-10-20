import java.util.Properties as JavaUtilProperties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing credentials live in a gitignored `keystore.properties` in the
// repository ROOT: copy `keystore.properties.example` and fill in the real
// values. The password is read here as a plain string and handed straight to the
// signing config, so it never appears in this file, in git, or in build output.

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties: JavaUtilProperties? = keystorePropertiesFile
    .takeIf { it.isFile }
    ?.inputStream()
    ?.use { stream -> JavaUtilProperties().apply { load(stream) } }

fun releaseSigningValue(key: String): String? =
    keystoreProperties?.getProperty(key)?.trim()?.takeIf { value -> value.isNotEmpty() }

val releaseStoreFile = releaseSigningValue("storeFile")
val releaseStorePassword = releaseSigningValue("storePassword")
val releaseKeyAlias = releaseSigningValue("keyAlias")
val releaseKeyPassword = releaseSigningValue("keyPassword")

// Only sign when every property is present AND the keystore actually exists on
// disk. Otherwise the release stays unsigned (never signed with the debug key).
val releaseSigningConfigured = releaseStoreFile != null &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null &&
    File(releaseStoreFile).isFile

if (!releaseSigningConfigured) {
    val reason = when {
        keystoreProperties == null ->
            "keystore.properties not found at ${keystorePropertiesFile.absolutePath}"
        else ->
            "keystore.properties is missing a value or storeFile does not exist: " +
                listOfNotNull(
                    "storeFile".takeIf { releaseStoreFile == null },
                    "storePassword".takeIf { releaseStorePassword == null },
                    "keyAlias".takeIf { releaseKeyAlias == null },
                    "keyPassword".takeIf { releaseKeyPassword == null },
                ).ifEmpty { listOf("storeFile=${releaseStoreFile} does not exist on disk") }
                .joinToString(", ")
    }
    logger.warn(
        "WARNING: release signing is not configured ($reason). " +
            "Release builds will be UNSIGNED. " +
            "Copy keystore.properties.example to keystore.properties and fill in the real values."
    )
}

android {
    namespace = "org.ziyoffice"
    compileSdk = 36

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = File(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "org.ziyoffice"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signed only when signingConfigs.create("release") succeeded above
            // (keystore.properties present AND keystore file on disk). Otherwise
            // left unset: an unsigned release, NOT a debug-signed one.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
    }

    testOptions {
        unitTests {
            // Pure state/geometry helpers are unit-tested on the JVM; the Android
            // stubs they never call return defaults instead of throwing.
            isReturnDefaultValues = true
        }
    }
}

// Failing guard: an unsigned release artifact must never ship silently.
// Runs before assembleRelease; hard-fails unless the release signingConfig was applied.
// Registered inside afterEvaluate because AGP only creates the assembleRelease task
// after the android {} block has been fully evaluated (lazy configuration).
afterEvaluate {
    val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
        group = "verification"
        description = "Fails the build if release builds would be unsigned."
        doLast {
            if (android.signingConfigs.findByName("release") == null) {
                throw GradleException(
                    "Release signing is not configured: create keystore.properties from " +
                        "keystore.properties.example (see docs/)"
                )
            }
        }
    }

    tasks.findByName("assembleRelease")?.dependsOn(verifyReleaseSigning)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:model"))
    implementation(project(":core:ooxml"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(kotlin("test"))
}
