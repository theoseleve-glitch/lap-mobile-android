import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

// Release signing reads from a gitignored keystore.properties at repo root
// (schema: storeFile, storePassword, keyAlias, keyPassword). When the file is
// absent (fresh clone, CI without secrets) we fall back to the debug keystore
// so `assembleRelease` still produces a runnable APK — it just isn't
// Play-uploadable.
private val keystorePropsFile = rootProject.file("keystore.properties")
private val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.lap.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lap.mobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "API_BASE_URL", "\"https://dev.api.lap.com\"")
            buildConfigField("String", "CLERK_PUBLISHABLE_KEY", "\"pk_test_dev_PLACEHOLDER\"")
            buildConfigField("String", "SENTRY_DSN", "\"https://placeholder@o0.ingest.sentry.io/dev\"")
            buildConfigField("String", "POSTHOG_KEY", "\"phc_placeholder_dev\"")
        }
        create("staging") {
            dimension = "env"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "API_BASE_URL", "\"https://staging.api.lap.com\"")
            buildConfigField("String", "CLERK_PUBLISHABLE_KEY", "\"pk_test_staging_PLACEHOLDER\"")
            buildConfigField("String", "SENTRY_DSN", "\"https://placeholder@o0.ingest.sentry.io/staging\"")
            buildConfigField("String", "POSTHOG_KEY", "\"phc_placeholder_staging\"")
        }
        create("prod") {
            dimension = "env"
            buildConfigField("String", "API_BASE_URL", "\"https://api.lap.com\"")
            buildConfigField("String", "CLERK_PUBLISHABLE_KEY", "\"pk_live_PROD_PLACEHOLDER\"")
            buildConfigField("String", "SENTRY_DSN", "\"https://placeholder@o0.ingest.sentry.io/prod\"")
            buildConfigField("String", "POSTHOG_KEY", "\"phc_placeholder_prod\"")
        }
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (keystorePropsFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        named("main") { java.srcDir("src/main/kotlin") }
        named("test") { java.srcDir("src/test/kotlin") }
        named("androidTest") { java.srcDir("src/androidTest/kotlin") }
    }
}

detekt {
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
}

ktlint {
    android.set(true)
}

val installGitHooks by tasks.registering(Exec::class) {
    group = "git"
    description = "Configure git to use .githooks/ as core.hooksPath (idempotent)"
    workingDir = rootProject.rootDir
    commandLine("git", "config", "--local", "core.hooksPath", ".githooks")
    isIgnoreExitValue = true
    onlyIf { rootProject.rootDir.resolve(".git").isDirectory }
}

tasks.named("preBuild") {
    dependsOn(installGitHooks)
}

// Aggregate task so the /loop + pre-commit hook contract
// (`./gradlew app:testDebugUnitTest`) keeps working now that flavors split
// the real task into testDevDebugUnitTest / testStagingDebugUnitTest /
// testProdDebugUnitTest.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs unit tests for every flavor's debug variant."
    dependsOn("testDevDebugUnitTest", "testStagingDebugUnitTest", "testProdDebugUnitTest")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.location)
    implementation(libs.nordic.ble)
    implementation(libs.nordic.ble.ktx)
    implementation(libs.nordic.scanner)
    implementation(project(":schemas"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
