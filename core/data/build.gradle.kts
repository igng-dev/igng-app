plugins {
    alias(libs.plugins.igng.android.library)
    alias(libs.plugins.igng.android.compose)
}

android {
    namespace = "net.igng.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:network"))
    api(project(":core:auth"))
    api(libs.coil.compose)
    api(libs.coil.gif)
    api(libs.coil.network.okhttp)
    api(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.lifecycle.viewmodel.ktx)
    api(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
