import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class IgngAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            extensions.configure<LibraryExtension> {
                compileSdk = IgngSdk.COMPILE
                defaultConfig {
                    minSdk = IgngSdk.MIN
                }
                compileOptions {
                    sourceCompatibility = IgngSdk.JAVA_VERSION
                    targetCompatibility = IgngSdk.JAVA_VERSION
                }
            }
        }
    }
}
