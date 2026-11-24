plugins {
    alias(libs.plugins.igng.android.library)
}

android {
    namespace = "net.igng.mcstatus.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:network"))
    api(project(":core:auth"))
    api(project(":core:data"))
    api(project(":core:updater"))
    api(project(":features:mc:model"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
