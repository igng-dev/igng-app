plugins {
    alias(libs.plugins.igng.android.application)
    alias(libs.plugins.igng.android.compose)
}

val apiBaseUrl = providers.gradleProperty("IGNG_API_BASE_URL").orElse("https://www.igng.net").get()
val bbsBaseUrl = providers.gradleProperty("IGNG_BBS_BASE_URL").orElse("https://www.igngbbs.net").get()
val ssoBaseUrl = providers.gradleProperty("IGNG_SSO_BASE_URL").orElse("https://sso.igng.net").get()

android {
    namespace = "net.igng.bbs"

    defaultConfig {
        applicationId = "net.igng.bbs"

        buildConfigField("String", "IGNG_API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "IGNG_BBS_BASE_URL", "\"$bbsBaseUrl\"")
        buildConfigField("String", "IGNG_SSO_BASE_URL", "\"$ssoBaseUrl\"")
    }
}

configureIgngReleaseSigning(android, appPrefix = "BBS", enableMinify = true)

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":core:auth"))
    implementation(project(":core:data"))
    implementation(project(":core:markdown"))
    implementation(project(":features:bbs:model"))
    implementation(project(":features:bbs:common"))
    implementation(project(":features:bbs:data"))
    implementation(project(":features:bbs:feed"))
    implementation(project(":features:bbs:column"))
    implementation(project(":features:bbs:post"))
    implementation(project(":features:bbs:inbox"))
    implementation(project(":features:bbs:creator"))
    implementation(project(":features:bbs:profile"))
    implementation(project(":features:bbs:admin"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
