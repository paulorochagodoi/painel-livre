import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Assinatura local: crie keystore/keystore.properties (veja README). Sem ele, o release usa a
// chave de debug — instala e funciona igual, só não é a "sua" assinatura definitiva.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "br.painellivre"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.painellivre"
        // DiLink 3 = Android 10 (API 29). Mantemos targetSdk 29 como o DashCast: o comportamento
        // de runtime do Android 10 é o que a central da BYD espera.
        minSdk = 28
        targetSdk = 29
        versionCode = 14
        versionName = "0.5.8"
        // Central DiLink 3 = Snapdragon (ARM64). Corta as libs nativas x86 do MapLibre do release.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            ndk { abiFilters += listOf("x86_64") } // para testar no emulador do PC
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
        buildConfig = true
    }

    lint {
        // targetSdk 29 é proposital (Android 10 do DiLink 3); não é um app de Play Store.
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
        abortOnError = false
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/versions/**")
    }

    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "PainelLivre-v${versionName}-${buildType.name}.apk"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Mapa do painel: MapLibre (BSD-2) + tiles OpenStreetMap do OpenFreeMap (sem chave de API).
    implementation("org.maplibre.gl:android-sdk:11.13.5")

    // Cliente ADB em Kotlin puro (Apache-2.0): conecta no adbd local (127.0.0.1:5555) para rodar
    // comandos como uid 2000 (shell) — mesmo caminho usado pelo DashCast e pelo SealNav.
    implementation("dev.mobile:dadb:2.0.0")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // org.json de verdade nos testes da JVM (o do Android é stub)
}
