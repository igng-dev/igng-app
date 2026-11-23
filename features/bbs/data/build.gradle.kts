plugins {
    alias(libs.plugins.igng.android.library)
}

android {
    namespace = "net.igng.bbs.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:network"))
    api(project(":core:auth"))
    api(project(":core:data"))
    api(project(":features:bbs:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
