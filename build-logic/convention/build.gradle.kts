import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "net.igng.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.serialization.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "igng.android.application"
            implementationClass = "IgngAndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "igng.android.library"
            implementationClass = "IgngAndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "igng.android.compose"
            implementationClass = "IgngAndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "igng.android.feature"
            implementationClass = "IgngAndroidFeatureConventionPlugin"
        }
    }
}
