plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
}

import java.util.Properties
import java.io.FileInputStream

val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
val prNumber = System.getenv("GITHUB_REF")
    ?.removePrefix("refs/pull/")
    ?.substringBefore("/")
    ?.toIntOrNull() ?: 0

// Support version name override from CI (e.g., for PR builds)
val versionNameOverride: String? by project

// Load keystore properties for signing
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.routesnap.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.routesnap.app"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber.takeIf { it > 0 } ?: 1
        versionName = versionNameOverride ?: "0.1.$prNumber.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            } else if (System.getenv("KEYSTORE") != null && System.getenv("KEYSTORE").isNotEmpty()) {
                // CI/CD environment variables
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                storeFile = file(System.getenv("KEYSTORE"))
                storePassword = System.getenv("STORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only apply signing config if keystore is available
            val releaseSigning = signingConfigs.getByName("release")
            releaseSigning.storeFile?.let { storeFile ->
                if (storeFile.exists()) {
                    signingConfig = releaseSigning
                }
            }
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        abortOnError = false
        checkDependencies = false
        ignoreTestSources = true
        warningsAsErrors = false
        // DISABLED: Kotlin 2.0+ incompatibility - detector crashes with IncompatibleClassChangeError
        // These checks are REPLACED by compose-rules via ktlint (see .editorconfig)
        // See: https://issuetracker.google.com/issues/330774752
        // See: https://github.com/mrmans0n/compose-rules
        disable += "NullSafeMutableLiveData"
        disable += "RememberInComposition"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.8")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    // Activity
    implementation("androidx.activity:activity-compose:1.10.1")

    // Media3 (Video playback and editing)
    implementation("androidx.media3:media3-common:1.5.1")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("androidx.media3:media3-effect:1.5.1")

    // MapLibre (Open source map engine)
    implementation("org.maplibre.gl:android-sdk:13.3.1")

    // ExifInterface (GPS metadata extraction)
    implementation("androidx.exifinterface:exifinterface:1.4.2")

    // Hilt (Dependency Injection)
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.4.0")

    // Room (Local Database)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Coil (Image loading)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Moshi (JSON serialization)
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.11.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Ktlint - Compose rules for lint checks that work with Kotlin 2.0+
    // Replaces broken Android lint detectors (NullSafeMutableLiveData, RememberInComposition)
    ktlintRuleset("io.nlopez.compose.rules:ktlint:0.6.2")

    // Detekt - Compose rules for static analysis (works with Kotlin 2.0+)
    // Complements ktlint (formatting) with deep code analysis
    detektPlugins("io.nlopez.compose.rules:detekt:0.6.2")
}

// Ktlint configuration - use ktlint 1.8.0 for compose-rules compatibility
ktlint {
    version.set("1.8.0")
    kotlinScriptAdditionalPaths {
        include(fileTree("scripts/"))
    }
    filter {
        exclude("**/scripts/**")
        exclude("**/*.kts")
        include("**/*.kt")
    }
}

// Detekt configuration
detekt {
    toolVersion = "1.23.8"
    source.setFrom("src/main/java", "src/test/java")
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
    baseline = file("$rootDir/config/detekt/baseline.xml")
    ignoreFailures = false

    // Skip detekt on release builds (faster CI)
    ignoredBuildTypes = listOf("release")
}

// Configure detekt reports
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        html.outputLocation.set(file("build/reports/detekt/detekt.html"))
        sarif.required.set(true)  // For GitHub PR annotations
        sarif.outputLocation.set(file("build/reports/detekt/detekt.sarif"))
        txt.required.set(false)
        xml.required.set(false)
    }
}
