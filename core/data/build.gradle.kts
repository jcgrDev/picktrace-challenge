plugins {
    alias(libs.plugins.picktrace.android.library)
    alias(libs.plugins.picktrace.hilt)
    alias(libs.plugins.picktrace.android.test)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.sync)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(projects.core.testing)
    testImplementation(projects.core.network)
}
