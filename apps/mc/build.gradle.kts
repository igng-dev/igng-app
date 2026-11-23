plugins {
    alias(libs.plugins.igng.android.application)
    alias(libs.plugins.igng.android.compose)
}

val apiBaseUrl = providers.gradleProperty("IGNG_API_BASE_URL").orElse("https://www.igng.net").get().trimEnd('/')
val ssoBaseUrl = providers.gradleProperty("IGNG_SSO_BASE_URL").orElse("https://sso.igng.net").get().trimEnd('/')
val mcBaseUrl = providers.gradleProperty("IGNG_MC_BASE_URL").orElse("https://mc.igng.net").get().trimEnd('/')
val dashBaseUrl = providers.gradleProperty("IGNG_DASH_BASE_URL").orElse("https://dash.igng.net").get().trimEnd('/')
val chatBaseUrl = providers.gradleProperty("IGNG_CHAT_BASE_URL").orElse("https://chat.igng.net").get().trimEnd('/')

android {
    namespace = "net.igng.mcstatus"

    defaultConfig {
        applicationId = "net.igng.mcstatus"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "SSO_BASE_URL", "\"$ssoBaseUrl\"")
        buildConfigField("String", "MC_BASE_URL", "\"$mcBaseUrl\"")
        buildConfigField("String", "DASH_BASE_URL", "\"$dashBaseUrl\"")
        buildConfigField("String", "CHAT_BASE_URL", "\"$chatBaseUrl\"")
    }
}

configureIgngReleaseSigning(android, appPrefix = "MC", enableMinify = true)

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":core:auth"))
    implementation(project(":features:mc:model"))
    implementation(project(":features:mc:common"))
    implementation(project(":features:mc:data"))
    implementation(project(":features:mc:explore"))
    implementation(project(":features:mc:status"))
    implementation(project(":features:mc:map"))
    implementation(project(":features:mc:chat"))
    implementation(project(":features:mc:wiki"))
    implementation(project(":features:mc:player"))
    implementation(project(":features:mc:ticket"))
    implementation(project(":features:mc:admin"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
