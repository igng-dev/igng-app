package net.igng.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色画布"),
    DARK("dark", "深色画布"),
}

@Serializable
enum class MotionIntensity(val id: String, val label: String) {
    FULL("full", "完整动效"),
    CALM("calm", "舒缓动效"),
    REDUCED("reduced", "精简动效"),
}

@Serializable
enum class ReaderCanvasTone {
    DEFAULT,
    WARM_PAPER,
    INK_DARK,
}

@Serializable
data class ReaderTypographyPrefs(
    val fontSizeSp: Float = 16.5f,
    val lineHeightMultiplier: Float = 1.72f,
    val canvasTone: ReaderCanvasTone = ReaderCanvasTone.DEFAULT,
    val preferSerif: Boolean = false,
)
