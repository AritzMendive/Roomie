plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23"
}

android {
    namespace = "com.example.roomie"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.roomie"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.espresso.core)
    implementation(libs.firebase.firestore)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("androidx.navigation:navigation-compose:2.5.3")
    implementation("androidx.compose.material3:material3:1.2.0-alpha04")
    implementation("androidx.compose.material:material-icons-extended")

    // Import the BoM for the Firebase platform
    implementation(platform("com.google.firebase:firebase-bom:33.12.0"))

    // Add the dependency for the Firebase Authentication library
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation("com.google.firebase:firebase-auth")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("io.coil-kt:coil-compose:2.6.0") // Usa la última versión estable de Coil

    // Dependencias para Ktor (Cliente HTTP)
    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0") // O la versión más reciente
    implementation("com.squareup.retrofit2:converter-gson:2.9.0") // O converter-moshi
    // Gson (si usas el conversor Gson)
    implementation("com.google.code.gson:gson:2.10.1") // O la versión más reciente

    // Opcional: Coroutines support for Retrofit
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Ktor Client (Android)
    implementation("io.ktor:ktor-client-android:2.3.13") // O versión más reciente

    implementation("io.ktor:ktor-client-okhttp:2.3.13")

// Ktor Content Negotiation (para JSON)
    implementation("io.ktor:ktor-client-content-negotiation:2.3.13")
// Ktor Kotlinx Serialization JSON
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.13")

// Kotlinx Serialization (asegúrate que el plugin está aplicado también)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // O versión más reciente

// Coil (para cargar imágenes desde URL)
    implementation("io.coil-kt:coil-compose:2.7.0") // O versión más reciente
    implementation("io.coil-kt:coil:2.4.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

}