import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  id("com.google.devtools.ksp")
}

// Release imzası: proje kökündeki keystore.properties dosyasından okunur (git'e EKLENMEZ).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystorePropsFile.exists()

android {
    namespace = "com.tivimax.player"
    compileSdk = 36
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }
    val tmdbApiKey = localProperties.getProperty("TMDB_API_KEY") ?: ""

    // Release imzası: proje kökündeki keystore.properties dosyasından okunur (git'e EKLENMEZ).
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
    }
    val hasReleaseKeystore = keystorePropsFile.exists()

    defaultConfig {
        applicationId = "com.tivimax.player"
        minSdk = 26
        targetSdk = 36
        versionCode = 58
        versionName = "1.3.6"

        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")

        ndk {
            abiFilters.addAll(setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"))
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
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
            // keystore.properties yoksa debug anahtarına düşer (sadece yerel deneme için).
            // Müşteriye dağıtılan APK mutlaka release anahtarıyla imzalanmalı.
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("UYARI: keystore.properties bulunamadi, release APK DEBUG anahtariyla imzalaniyor!")
                signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.media3.common.util.UnstableApi"
        )
    }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Media3 / ExoPlayer (TiviMate core video engine)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.exoplayer.dash)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.session)
  implementation("androidx.media3:media3-datasource-okhttp:1.5.1")
  implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1")

  // Coil for poster & logo caching
  implementation(libs.coil.compose)

  // OkHttp & Gson
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)
  implementation(libs.gson)

  // DataStore
  implementation(libs.androidx.datastore.preferences)

  // Room Database
  val roomVersion = "2.7.0"
  implementation("androidx.room:room-runtime:$roomVersion")
  implementation("androidx.room:room-ktx:$roomVersion")
  implementation("androidx.room:room-paging:$roomVersion")
  ksp("androidx.room:room-compiler:$roomVersion")

  // Paging 3
  val pagingVersion = "3.3.6"
  implementation("androidx.paging:paging-runtime:$pagingVersion")
  implementation("androidx.paging:paging-compose:$pagingVersion")
}
