import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // AGP 9 compiles Kotlin itself (built-in Kotlin); kotlin-android is not applied.
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            namespace = picktraceNamespace
            compileSdk = libs.version("compileSdk").toInt()
            defaultConfig {
                minSdk = libs.version("minSdk").toInt()
            }
            compileOptions {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }
            testOptions {
                targetSdk = libs.version("targetSdk").toInt()
                unitTests.isIncludeAndroidResources = true
            }
            lint {
                targetSdk = libs.version("targetSdk").toInt()
            }
        }
        configureKotlinJvmTarget()
    }
}
