plugins {
    alias(libs.plugins.picktrace.android.library)
    alias(libs.plugins.picktrace.hilt)
    alias(libs.plugins.picktrace.android.test)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    // api: SyncApi returns retrofit2.Response, DTOs expose JsonObject, interceptors are okhttp3.Interceptor.
    api(libs.retrofit)
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)
}
