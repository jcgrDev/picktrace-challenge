plugins {
    `kotlin-dsl`
}

group = "com.jcgrdev.picktracechallenge.buildlogic"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.room.gradle.plugin)
    compileOnly(libs.hilt.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = libs.plugins.picktrace.jvm.library.get().pluginId
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.picktrace.android.library.get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = libs.plugins.picktrace.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.picktrace.android.compose.get().pluginId
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("hilt") {
            id = libs.plugins.picktrace.hilt.get().pluginId
            implementationClass = "HiltConventionPlugin"
        }
        register("androidRoom") {
            id = libs.plugins.picktrace.android.room.get().pluginId
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("androidTest") {
            id = libs.plugins.picktrace.android.test.get().pluginId
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("androidFeature") {
            id = libs.plugins.picktrace.android.feature.get().pluginId
            implementationClass = "AndroidFeatureConventionPlugin"
        }
    }
}
