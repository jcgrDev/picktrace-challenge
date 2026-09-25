plugins {
    alias(libs.plugins.picktrace.android.application)
    alias(libs.plugins.picktrace.android.compose)
    alias(libs.plugins.picktrace.hilt)
    alias(libs.plugins.picktrace.android.test)
}

android {
    defaultConfig {
        applicationId = "com.jcgrdev.picktracechallenge"
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField(
            "String",
            "SYNC_BASE_URL",
            "\"${providers.gradleProperty("picktrace.syncBaseUrl").get()}\"",
        )
    }
}

dependencies {
    implementation(projects.feature.capture)
    implementation(projects.feature.events)
    implementation(projects.core.data)
    implementation(projects.core.sync)
    implementation(projects.core.network)
    implementation(projects.core.designsystem)
    debugImplementation(projects.core.testing)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlinx.serialization.json)
}
