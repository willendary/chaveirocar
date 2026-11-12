import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

// A keystore e as senhas ficam em local.properties, que nao vai para o git.
val propriedadesLocais = Properties().apply {
  val arquivo = rootProject.file("local.properties")
  if (arquivo.exists()) arquivo.inputStream().use { entrada -> load(entrada) }
}
val caminhoKeystore = propriedadesLocais.getProperty("CHAVEIRO_KEYSTORE")
  ?.let { rootProject.file(it) }
  ?.takeIf { it.exists() }

android {
  namespace = "com.willendary.chaveirocar"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.willendary.chaveirocar"
    minSdk = 24
    targetSdk = 36
    versionCode = 11
    versionName = "1.4.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    if (caminhoKeystore != null) {
      create("release") {
        storeFile = caminhoKeystore
        storePassword = propriedadesLocais.getProperty("CHAVEIRO_KEYSTORE_SENHA")
        keyAlias = propriedadesLocais.getProperty("CHAVEIRO_KEY_ALIAS")
        keyPassword = propriedadesLocais.getProperty("CHAVEIRO_KEY_SENHA")
      }
    }
  }

  buildTypes {
    release {
      if (caminhoKeystore != null) signingConfig = signingConfigs.getByName("release")
      isMinifyEnabled = false
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
    buildConfig = false
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
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.biometric)
  implementation(libs.play.services.auth)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
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

}
