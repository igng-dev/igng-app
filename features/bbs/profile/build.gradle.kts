plugins {
    alias(libs.plugins.igng.android.feature)
}

android {
    namespace = "net.igng.bbs.feature.profile"
}

dependencies {
    implementation(project(":features:bbs:model"))
    implementation(project(":features:bbs:common"))
    implementation(project(":features:bbs:data"))
    implementation(project(":core:auth"))
    implementation(libs.androidx.activity.compose)
}
