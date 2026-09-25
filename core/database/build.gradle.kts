plugins {
    alias(libs.plugins.picktrace.android.library)
    alias(libs.plugins.picktrace.android.room)
    alias(libs.plugins.picktrace.hilt)
    alias(libs.plugins.picktrace.android.test)
}

dependencies {
    api(projects.core.model)
}

dependencies {
    testImplementation(projects.core.testing)
}
