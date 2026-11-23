plugins {
    alias(libs.plugins.igng.android.feature)
}

android {
    namespace = "net.igng.bbs.common"
}

dependencies {
    api(project(":features:bbs:model"))
}
