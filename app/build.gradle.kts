import org.gradle.api.tasks.testing.Test
plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.cipherwell.vaultzero"
    minSdk = 24
    targetSdk = 36
    versionCode = 32
    versionName = "1.7.23"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Screenshot protection is ON for every normal build. The only way to see the
    // UI from automation is an explicit, opt-in debug build:
    //   ./gradlew assembleDebug -PallowScreenshots=true
    buildConfigField(
      "boolean",
      "ALLOW_SCREENSHOTS",
      (project.findProperty("allowScreenshots") as String? ?: "false"),
    )

    // Dev-only convenience: keeps the vault unlocked on a test device.
    //   ./gradlew assembleDebug -PdevDisableAutoLock=true
    // Default false; release builds never pass the flag.
    buildConfigField(
      "boolean",
      "DEV_DISABLE_AUTO_LOCK",
      (project.findProperty("devDisableAutoLock") as String? ?: "false"),
    )
  }

  // Production signing is supplied via KEYSTORE_PATH / STORE_PASSWORD / KEY_PASSWORD.
  // When those are absent (F-Droid build server, third-party distro builders), the release
  // variant is intentionally left UNSIGNED so the distributor can sign it with its own key.
  // Never fall back to debug.keystore for release artifacts.
  val releaseKeystorePath = System.getenv("KEYSTORE_PATH")
  val releaseStorePassword = System.getenv("STORE_PASSWORD")
  val releaseKeyPassword = System.getenv("KEY_PASSWORD")
  val hasReleaseSigning = !releaseKeystorePath.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank() &&
    file(releaseKeystorePath).exists()

  signingConfigs {
    create("release") {
      if (hasReleaseSigning) {
        storeFile = file(releaseKeystorePath!!)
        storePassword = releaseStorePassword
        keyAlias = "upload"
        keyPassword = releaseKeyPassword
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Signed locally/CI with the production key; unsigned for F-Droid-style builders.
      signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.biometric)
  // Argon2id (RFC 9106) for the password/recovery KDF; no other provider is used.
  implementation(libs.bouncycastle.prov)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.bip39.test)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  "ksp"(libs.androidx.room.compiler)
}

/**
 * Robolectric normally downloads its Android SDK jar from Maven Central at test
 * runtime. GitHub Actions egress occasionally fails that fetch, which fails every
 * Robolectric class before a test runs. Resolve the exact jar through Gradle and
 * point Robolectric at it in offline mode instead.
 */
val robolectricAndroidAll by configurations.creating
dependencies {
    robolectricAndroidAll("org.robolectric:android-all-instrumented:14-robolectric-10818077-i7")
}
val robolectricDepsDir = layout.buildDirectory.dir("robolectric-deps")
val prepareRobolectricDeps = tasks.register<Copy>("prepareRobolectricDeps") {
    from(robolectricAndroidAll)
    into(robolectricDepsDir)
}
tasks.withType<Test>().configureEach {
    dependsOn(prepareRobolectricDeps)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricDepsDir.get().asFile.absolutePath)
}
