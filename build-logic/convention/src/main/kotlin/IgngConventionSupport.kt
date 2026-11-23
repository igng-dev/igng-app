import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal object IgngSdk {
    const val COMPILE = 36
    const val MIN = 26
    const val TARGET = 36
    val JAVA_VERSION = JavaVersion.VERSION_17
}

fun Project.signingCredential(name: String, appPrefix: String? = null): String? {
    val candidates = buildList {
        if (!appPrefix.isNullOrBlank()) {
            add("${appPrefix}_$name")
        }
        add(name)
    }
    for (key in candidates) {
        val fromEnv = providers.environmentVariable(key).orNull?.takeIf { it.isNotBlank() }
        if (fromEnv != null) return fromEnv
        val fromProp = providers.gradleProperty(key).orNull?.takeIf { it.isNotBlank() }
        if (fromProp != null) return fromProp
    }
    return null
}

fun Project.configureIgngReleaseSigning(
    extension: ApplicationExtension,
    appPrefix: String,
    enableMinify: Boolean = true,
) {
    val storeFileValue = signingCredential("ANDROID_SIGNING_STORE_FILE", appPrefix)
    val storePasswordValue = signingCredential("ANDROID_SIGNING_STORE_PASSWORD", appPrefix)
    val keyAliasValue = signingCredential("ANDROID_SIGNING_KEY_ALIAS", appPrefix)
    val keyPasswordValue = signingCredential("ANDROID_SIGNING_KEY_PASSWORD", appPrefix)
    val hasReleaseSigning = listOf(storeFileValue, storePasswordValue, keyAliasValue, keyPasswordValue)
        .all { !it.isNullOrBlank() }
    val requireReleaseSigning = signingCredential("IGNG_REQUIRE_RELEASE_SIGNING")
        ?.equals("true", ignoreCase = true) == true
    if (requireReleaseSigning && !hasReleaseSigning) {
        throw GradleException(
            "Release signing is required (IGNG_REQUIRE_RELEASE_SIGNING=true) for $appPrefix " +
                "but signing credentials are missing.",
        )
    }

    extension.apply {
        signingConfigs {
            if (hasReleaseSigning) {
                create("release") {
                    storeFile = rootProject.file(storeFileValue!!)
                    storePassword = storePasswordValue
                    keyAlias = keyAliasValue
                    keyPassword = keyPasswordValue
                }
            }
        }
        buildTypes {
            getByName("release") {
                isMinifyEnabled = enableMinify
                isShrinkResources = enableMinify
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro",
                )
                if (hasReleaseSigning) {
                    signingConfig = signingConfigs.getByName("release")
                }
            }
        }
    }
}

internal fun configureCommonAndroid(extension: LibraryExtension) {
    extension.apply {
        compileSdk = IgngSdk.COMPILE
        defaultConfig {
            minSdk = IgngSdk.MIN
            consumerProguardFiles("consumer-rules.pro")
        }
        compileOptions {
            sourceCompatibility = IgngSdk.JAVA_VERSION
            targetCompatibility = IgngSdk.JAVA_VERSION
        }
    }
}
