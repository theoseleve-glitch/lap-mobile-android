plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
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
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Placeholder: sign release with the debug keystore so
            // `assembleRelease` completes before T015 wires a real signing
            // config from keystore.properties.
            signingConfig = signingConfigs.getByName("debug")
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
    }

    sourceSets {
        named("main") { java.srcDir("src/main/kotlin") }
        named("test") { java.srcDir("src/test/kotlin") }
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

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
}
