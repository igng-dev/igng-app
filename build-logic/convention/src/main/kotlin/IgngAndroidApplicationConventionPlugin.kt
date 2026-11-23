import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class IgngAndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            val appVersion = resolveIgngAppVersion()

            extensions.configure<ApplicationExtension> {
                compileSdk = IgngSdk.COMPILE
                defaultConfig {
                    minSdk = IgngSdk.MIN
                    targetSdk = IgngSdk.TARGET
                    versionCode = appVersion.code
                    versionName = appVersion.name
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = IgngSdk.JAVA_VERSION
                    targetCompatibility = IgngSdk.JAVA_VERSION
                }
                buildFeatures {
                    buildConfig = true
                }
                packaging {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    }
                }
            }
        }
    }
}
