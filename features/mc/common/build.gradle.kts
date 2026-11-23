plugins {
    alias(libs.plugins.igng.android.feature)
}

android {
    namespace = "net.igng.mcstatus.common"
}

dependencies {
    api(project(":features:mc:model"))
}
