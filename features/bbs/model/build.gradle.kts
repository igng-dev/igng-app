plugins {
    alias(libs.plugins.igng.android.library)
}

android {
    namespace = "net.igng.bbs.core.model"
}

dependencies {
    api(project(":core:model"))
    testImplementation(libs.junit)
}
