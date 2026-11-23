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

data class IgngAppVersion(
    val name: String,
    val code: Int,
)

private val IGNG_SEMVER_REGEX = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$""")
private const val LEGACY_MAX_VERSION_CODE = 20302

fun Project.resolveIgngAppVersion(): IgngAppVersion {
    val rawName = providers.gradleProperty("IGNG_APP_VERSION_NAME").orNull?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("Missing IGNG_APP_VERSION_NAME in root gradle.properties.")
    val rawCode = providers.gradleProperty("IGNG_APP_VERSION_CODE").orNull?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("Missing IGNG_APP_VERSION_CODE in root gradle.properties.")

    val match = IGNG_SEMVER_REGEX.matchEntire(rawName)
        ?: throw GradleException(
            "Invalid IGNG_APP_VERSION_NAME='$rawName'. Expected SemVer format like 3.0.0 or 3.0.0-rc.1.",
        )
    val major = match.groupValues[1].toInt()
    val minor = match.groupValues[2].toInt()
    val patch = match.groupValues[3].toInt()
    val prerelease = match.groupValues[4]

    if (minor >= 100 || patch >= 100) {
        throw GradleException(
            "Invalid IGNG_APP_VERSION_NAME='$rawName': minor and patch must be < 100 for major*10000+minor*100+patch encoding.",
        )
    }

    val code = rawCode.toIntOrNull()
        ?: throw GradleException("Invalid IGNG_APP_VERSION_CODE='$rawCode': expected a positive integer.")
    if (code <= LEGACY_MAX_VERSION_CODE) {
        throw GradleException(
            "IGNG_APP_VERSION_CODE=$code must be strictly greater than legacy max versionCode ($LEGACY_MAX_VERSION_CODE) " +
                "to preserve seamless in-place upgrades.",
        )
    }

    val expectedStableCode = major * 10000 + minor * 100 + patch
    if (prerelease.isEmpty()) {
        if (code != expectedStableCode) {
            throw GradleException(
                "IGNG_APP_VERSION_CODE=$code does not match stable IGNG_APP_VERSION_NAME='$rawName' " +
                    "(expected $expectedStableCode = major*10000 + minor*100 + patch).",
            )
        }
    } else if (code > expectedStableCode) {
        throw GradleException(
            "Prerelease IGNG_APP_VERSION_CODE=$code must not exceed target stable code $expectedStableCode " +
                "for IGNG_APP_VERSION_NAME='$rawName'.",
        )
    }

    return IgngAppVersion(name = rawName, code = code)
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
