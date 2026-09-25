plugins {
    alias(libs.plugins.picktrace.android.library)
    alias(libs.plugins.picktrace.hilt)
    alias(libs.plugins.picktrace.android.test)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.network)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(libs.work.testing)
    testImplementation(projects.core.testing)
}
