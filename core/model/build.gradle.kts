plugins {
    alias(libs.plugins.igng.android.library)
}

android {
    namespace = "net.igng.core.model"
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
