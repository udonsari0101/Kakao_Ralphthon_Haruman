plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun buildConfigLiteral(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\r", "\\r").replace("\n", "\\n") + "\""

android {
    namespace = "org.ralphthon.haruman_ralphthon"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.ralphthon.haruman_ralphthon"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "1.1.0-rc.4"
        // Supplied only by the local build environment. Never commit the shared test credential.
        buildConfigField("String", "PRODUCT_API_BASE_URL", buildConfigLiteral(providers.environmentVariable("HARUMAN_API_BASE_URL").orNull.orEmpty()))
        buildConfigField("String", "PRODUCT_API_TOKEN", buildConfigLiteral(providers.environmentVariable("HARUMAN_API_TOKEN").orNull.orEmpty()))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val storePath = providers.environmentVariable("HARUMAN_RELEASE_STORE_FILE").orNull
        if (!storePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(storePath)
                storePassword = providers.environmentVariable("HARUMAN_RELEASE_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("HARUMAN_RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("HARUMAN_RELEASE_KEY_PASSWORD").orNull
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
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
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.10.0")
    implementation("androidx.activity:activity-ktx:1.12.4")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
