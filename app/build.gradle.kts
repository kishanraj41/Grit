plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.kishan.preptracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kishan.preptracker"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    buildTypes {
        release { isMinifyEnabled = false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.health.connect.client)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.material)
    testImplementation("junit:junit:4.13.2")
}

// Room writes one schema JSON per version; debug and release KSP racing that file
// produces "Empty schema file". Keep the two processors sequential.
tasks.matching { it.name == "kspReleaseKotlin" }.configureEach { mustRunAfter("kspDebugKotlin") }
tasks.matching { it.name == "kspReleaseUnitTestKotlin" }.configureEach { mustRunAfter("kspDebugUnitTestKotlin") }
