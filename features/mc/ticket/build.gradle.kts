plugins {
    alias(libs.plugins.igng.android.feature)
}

android {
    namespace = "net.igng.mcstatus.feature.ticket"
}

dependencies {
    implementation(project(":features:mc:model"))
    implementation(project(":features:mc:common"))
    implementation(project(":features:mc:data"))
    implementation(libs.androidx.activity.compose)
}
