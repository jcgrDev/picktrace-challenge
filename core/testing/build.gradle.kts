plugins {
    alias(libs.plugins.picktrace.android.library)
    alias(libs.plugins.picktrace.android.test)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    api(projects.core.database)
    api(projects.core.network)
    api(projects.core.sync)
    implementation(libs.retrofit.kotlinx.serialization)
    // compileOnly: debug :app pulls this module in for FakeSyncServer; JUnit must not ship in the APK.
    compileOnly(libs.junit)
    compileOnly(libs.kotlinx.coroutines.test)
}
