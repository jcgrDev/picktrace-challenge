import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/** JVM tests incl. Robolectric; nothing uses androidTest. */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        dependencies {
            add("testImplementation", libs.bundle("testing-unit"))
            add("testImplementation", libs.bundle("testing-android"))
        }
        // Hilt/KSP generate test sources even in modules with no hand-written tests yet, which trips
        // Gradle 9's "no tests discovered" check. Keep the check wherever real tests exist.
        val hasHandWrittenTests = file("src/test/kotlin").exists() || file("src/test/java").exists()
        tasks.withType<Test>().configureEach {
            failOnNoDiscoveredTests.set(hasHandWrittenTests)
        }
    }
}
