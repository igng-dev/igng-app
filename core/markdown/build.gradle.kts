plugins {
    alias(libs.plugins.igng.android.library)
    alias(libs.plugins.igng.android.compose)
}

android {
    namespace = "net.igng.core.markdown"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
