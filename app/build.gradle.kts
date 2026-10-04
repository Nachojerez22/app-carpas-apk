import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// La API key de AEMET se lee de local.properties (nunca versionado) o de la variable de
// entorno AEMET_API_KEY. Si no existe, queda vacía y la app avisa de que AEMET no está configurado.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val aemetApiKey: String =
    localProperties.getProperty("AEMET_API_KEY") ?: System.getenv("AEMET_API_KEY") ?: ""

// Firma de la versión de producción (release). Siempre la MISMA clave: Android solo actualiza una
// app firmada con la misma clave, y desinstalar borra los datos del usuario. La clave nunca se
// versiona: se lee de local.properties o de variables de entorno (secretos de GitHub en la CI).
// Sin clave, el APK release sale sin firmar (no instalable) y el de prueba sigue funcionando.
fun secret(name: String): String? =
    (localProperties.getProperty(name) ?: System.getenv(name))?.takeIf { it.isNotBlank() }

val releaseKeystore: java.io.File? = secret("CARP_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }

// En la CI el número de versión es el número de ejecución, para que cada APK actualice al anterior.
val ciVersionCode: Int = System.getenv("CARP_VERSION_CODE")?.toIntOrNull() ?: 1

android {
    namespace = "com.nachojerez.carpstrategy"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nachojerez.carpstrategy"
        minSdk = 26
        targetSdk = 37
        versionCode = ciVersionCode
        versionName = "0.9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "AEMET_API_KEY", "\"$aemetApiKey\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = secret("CARP_KEYSTORE_PASSWORD")
                keyAlias = secret("CARP_KEY_ALIAS")
                keyPassword = secret("CARP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // La versión de prueba se instala junto a la de producción sin pisar sus datos.
            applicationIdSuffix = ".prueba"
            versionNameSuffix = "-prueba"
            // Con la clave fija, también la versión de prueba se puede actualizar sin desinstalar.
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        release {
            // Sin R8: app personal; evita fallos en tiempo de ejecución que la CI no detectaría
            // (reflexión de Room, Hilt y Kotlinx Serialization).
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Esquemas de Room de la BD de datos del usuario (se versionan para poder migrar sin perder datos).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    implementation(libs.play.services.location)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
