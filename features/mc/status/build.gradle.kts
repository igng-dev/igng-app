plugins {
    alias(libs.plugins.igng.android.feature)
}

android {
    namespace = "net.igng.mcstatus.feature.status"
}

dependencies {
    implementation(project(":features:mc:model"))
    implementation(project(":features:mc:common"))
    implementation(project(":features:mc:data"))
}
