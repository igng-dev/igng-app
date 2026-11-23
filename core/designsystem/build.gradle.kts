plugins {
    alias(libs.plugins.igng.android.library)
    alias(libs.plugins.igng.android.compose)
}

android {
    namespace = "net.igng.core.designsystem"
}

dependencies {
    api(project(":core:model"))
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.ui.text)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.animation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.coil.compose)
}
