import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kapt)
    alias(libs.plugins.kotlin.serialization)
}

val releaseSigningProperties = Properties()
val releaseSigningPropertiesFile = rootProject.file("keystore.properties")
if (releaseSigningPropertiesFile.isFile) {
    releaseSigningPropertiesFile.inputStream().use(releaseSigningProperties::load)
}

val releaseSigningRequiredKeys = listOf(
    "storeFile",
    "storePassword",
    "keyAlias",
    "keyPassword",
)
val hasReleaseSigning = releaseSigningRequiredKeys.all(releaseSigningProperties::containsKey)
val releaseDistributionRequested = gradle.startParameter.taskNames.any { taskName ->
    val normalizedTaskName = taskName.lowercase()
    normalizedTaskName.endsWith("assemblerelease") || normalizedTaskName.endsWith("bundlerelease")
}

if (releaseDistributionRequested && !hasReleaseSigning) {
    throw GradleException(
        "正式构建缺少 android/keystore.properties。请先运行 android/scripts/create-release-signing.sh。",
    )
}

android {
    namespace = "com.jaydocoder.plateview"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jaydocoder.plateview"
        minSdk = 31
        targetSdk = 36
        versionCode = 12
        versionName = "0.3.9"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val apiBaseUrl = providers.gradleProperty("plateviewApiBaseUrl")
            .orElse(
                if (releaseDistributionRequested) {
                    "https://api.chenxiruyu.dpdns.org/"
                } else {
                    "http://10.0.2.2:8080/"
                },
            )
            .get()
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        lintConfig = file("lint.xml")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:${libs.versions.compose.bom.get()}"))
    androidTestImplementation(platform("androidx.compose:compose-bom:${libs.versions.compose.bom.get()}"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    kapt(libs.hilt.compiler)
    kapt(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
