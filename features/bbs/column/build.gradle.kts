plugins {
    alias(libs.plugins.igng.android.feature)
}

android {
    namespace = "net.igng.bbs.feature.column"
}

dependencies {
    implementation(project(":features:bbs:model"))
    implementation(project(":features:bbs:common"))
    implementation(project(":features:bbs:data"))
}
