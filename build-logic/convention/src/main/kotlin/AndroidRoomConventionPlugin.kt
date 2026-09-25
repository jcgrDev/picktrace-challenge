import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("androidx.room")
        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }
        dependencies {
            // api: consumers call withTransaction and see RoomDatabase types.
            add("api", libs.lib("room-runtime"))
            add("api", libs.lib("room-ktx"))
            add("ksp", libs.lib("room-compiler"))
            add("testImplementation", libs.lib("room-testing"))
        }
    }
}
