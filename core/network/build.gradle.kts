plugins {
    alias(libs.plugins.igng.android.library)
}

android {
    namespace = "net.igng.core.network"
}

dependencies {
    api(project(":core:model"))
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
