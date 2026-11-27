package net.igng.core.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.decode.DataSource
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Precision
import coil3.size.Size as CoilSize
import kotlin.math.abs
import net.igng.core.model.MotionIntensity

/**
 * Lightweight in-process LRU cache of decoded image aspect ratios (`width / height`),
 * keyed by URL string. Prevents layout height jumps when `LazyColumn` recycles and
 * re-enters previously viewed image blocks.
 */
object IgngImageAspectRatioCache {
    const val DEFAULT_CONTENT_RATIO: Float = 16f / 10f
    const val MIN_KNOWN_PLACEHOLDER_RATIO: Float = 0.45f
    const val MAX_KNOWN_PLACEHOLDER_RATIO: Float = 2.8f
    const val MIN_CONTENT_RATIO: Float = 0.12f
    const val MAX_CONTENT_RATIO: Float = 6.0f
    private const val MAX_ENTRIES: Int = 256

    private val lock = Any()
    private val cache = object : LinkedHashMap<String, Float>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?): Boolean =
            size > MAX_ENTRIES
    }

    fun get(key: String?): Float? {
        val normalized = key?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return synchronized(lock) { cache[normalized] }
    }

    fun put(key: String?, ratio: Float) {
        val normalized = key?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val safe = sanitizeContentRatio(ratio) ?: return
        synchronized(lock) { cache[normalized] = safe }
    }

    fun clear() {
        synchronized(lock) { cache.clear() }
    }

    fun sanitizeKnownPlaceholderRatio(ratio: Float?): Float? =
        ratio?.takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(MIN_KNOWN_PLACEHOLDER_RATIO, MAX_KNOWN_PLACEHOLDER_RATIO)

    fun sanitizeContentRatio(ratio: Float?): Float? =
        ratio?.takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(MIN_CONTENT_RATIO, MAX_CONTENT_RATIO)

    fun sanitizeContentRatio(width: Int, height: Int): Float? {
        if (width <= 0 || height <= 0) return null
        return sanitizeContentRatio(width.toFloat() / height.toFloat())
    }

    fun resolvePlaceholderRatio(
        explicitRatio: Float?,
        cachedRatio: Float?,
        defaultRatio: Float = DEFAULT_CONTENT_RATIO,
    ): Float = sanitizeKnownPlaceholderRatio(explicitRatio)
        ?: sanitizeKnownPlaceholderRatio(cachedRatio)
        ?: defaultRatio.coerceIn(MIN_KNOWN_PLACEHOLDER_RATIO, MAX_KNOWN_PLACEHOLDER_RATIO)
}

internal enum class IgngImageLoadState {
    Loading,
    Success,
    Error,
}

internal data class IgngImagePlaceholderPalette(
    val base: Color,
    val pulseOverlay: Color,
    val pulseMinAlpha: Float,
    val pulseMaxAlpha: Float,
    val border: Color,
    val frameIconTint: Color,
    val glyphTint: Color,
    val errorBase: Color,
    val errorBorder: Color,
)

/**
 * Resolves Quiet Craft recessed photo-frame tokens across all 4 reading surfaces
 * (Light Default, Dark / Ink Dark, Light Warm Paper, Dark Warm Paper) without hardcoding
 * neutral gray or blowing out semi-transparent line alphas.
 */
internal fun resolveIgngImagePlaceholderPalette(colors: IgngColors): IgngImagePlaceholderPalette {
    val isWarmPaper = colors.canvas == colors.warmPaperCanvas
    return when {
        !colors.isDark && isWarmPaper -> IgngImagePlaceholderPalette(
            base = colors.codeBackground,
            pulseOverlay = colors.tile,
            pulseMinAlpha = 0.08f,
            pulseMaxAlpha = 0.42f,
            border = colors.lineStrong,
            frameIconTint = colors.textMuted.copy(alpha = 0.34f),
            glyphTint = colors.accent.copy(alpha = 0.78f),
            errorBase = colors.codeBackground,
            errorBorder = colors.lineStrong,
        )
        !colors.isDark -> IgngImagePlaceholderPalette(
            base = colors.tilePressed,
            pulseOverlay = colors.tile,
            pulseMinAlpha = 0.06f,
            pulseMaxAlpha = 0.44f,
            border = colors.lineStrong,
            frameIconTint = colors.textMuted.copy(alpha = 0.34f),
            glyphTint = colors.accent.copy(alpha = 0.76f),
            errorBase = colors.tileSoft,
            errorBorder = colors.lineStrong,
        )
        else -> IgngImagePlaceholderPalette(
            base = colors.tile,
            pulseOverlay = colors.tileSoft,
            pulseMinAlpha = 0.20f,
            pulseMaxAlpha = 0.82f,
            border = colors.lineStrong,
            frameIconTint = colors.textMuted.copy(alpha = 0.36f),
            glyphTint = colors.accent.copy(alpha = 0.82f),
            errorBase = colors.tile,
            errorBorder = colors.lineStrong,
        )
    }
}

@Composable
internal fun rememberIgngImageBreathFraction(active: Boolean): Float {
    val intensity = IgngTheme.motionIntensity
    if (!active || intensity == MotionIntensity.REDUCED) return 0.38f
    val durationMs = if (intensity == MotionIntensity.CALM) 1600 else 1100
    val transition = rememberInfiniteTransition(label = "igngImageBreath")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = IgngMotion.LoadEase),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "igngImageBreathFraction",
    ).value
}

/**
 * Quiet Craft content image for Markdown articles, editor blocks, and rich feeds.
 *
 * - Never collapses to 0dp before decode: reserves a crisp framed photo plate at t = 0ms.
 * - Uses IGNG's low-amplitude opacity breathing (no flashy shimmer sweep) and delays the
 *   centered [IgngFoldedCoreGlyph] by 140ms so cached images render with zero loader flash.
 * - Preserves display-width decoding sharpness for tall vertical screenshots via explicit
 *   width-bounded [ImageRequest] sizing.
 * - Exposes a clean retryable error state and suppresses accidental click-through while loading.
 */
@Composable
fun IgngContentImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    aspectRatioHint: Float? = null,
    defaultAspectRatio: Float = IgngImageAspectRatioCache.DEFAULT_CONTENT_RATIO,
    shape: Shape = IgngTheme.radii.md,
    minPlaceholderHeight: Dp = 148.dp,
    maxPlaceholderHeight: Dp = 260.dp,
    maxKnownPlaceholderHeight: Dp = 540.dp,
    compactError: Boolean = false,
    uploadingOverlay: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val reduced = IgngTheme.motionReduced
    val haptic = rememberIgngHaptics()
    val palette = remember(colors) { resolveIgngImagePlaceholderPalette(colors) }

    val urlKey = (model as? String)?.trim()?.takeIf { it.isNotEmpty() }
    val cachedRatio = remember(urlKey) { IgngImageAspectRatioCache.get(urlKey) }
    val knownRatio = remember(aspectRatioHint, cachedRatio) {
        IgngImageAspectRatioCache.sanitizeKnownPlaceholderRatio(aspectRatioHint)
            ?: IgngImageAspectRatioCache.sanitizeKnownPlaceholderRatio(cachedRatio)
    }

    var loadState by remember(model) { mutableStateOf(IgngImageLoadState.Loading) }
    var resolvedRatio by remember(model) { mutableStateOf<Float?>(null) }
    var fromMemoryCache by remember(model) { mutableStateOf(false) }
    var retryNonce by remember(model) { mutableIntStateOf(0) }

    val isLoading = loadState == IgngImageLoadState.Loading
    val isSuccess = loadState == IgngImageLoadState.Success
    val isError = loadState == IgngImageLoadState.Error

    val imageAlpha by animateFloatAsState(
        targetValue = if (isSuccess) 1f else 0f,
        animationSpec = if (fromMemoryCache || reduced || !isSuccess) {
            snap()
        } else {
            IgngMotion.normalTween(intensity)
        },
        label = "igngContentImageAlpha",
    )

    val labelText = contentDescription?.trim().orEmpty()
    val semanticDesc = when {
        uploadingOverlay -> "正在上传图片"
        isLoading -> if (labelText.isNotEmpty()) "${labelText}，正在加载" else "插图，正在加载"
        isError -> "图片未能加载，点按重试"
        else -> labelText.ifEmpty { "插图" }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
    ) {
        val containerWidth = maxWidth
        val widthPx = with(density) { containerWidth.roundToPx() }.coerceIn(64, 2048)

        val targetHeight: Dp = when (loadState) {
            IgngImageLoadState.Success -> {
                val r = resolvedRatio
                    ?: IgngImageAspectRatioCache.sanitizeContentRatio(aspectRatioHint)
                    ?: IgngImageAspectRatioCache.sanitizeContentRatio(cachedRatio)
                    ?: defaultAspectRatio
                (containerWidth / r).coerceAtLeast(36.dp)
            }
            IgngImageLoadState.Error -> {
                if (compactError) {
                    136.dp
                } else {
                    knownRatio?.let { (containerWidth / it).coerceIn(148.dp, 220.dp) } ?: 168.dp
                }
            }
            IgngImageLoadState.Loading -> {
                if (knownRatio != null) {
                    (containerWidth / knownRatio).coerceIn(minPlaceholderHeight, maxKnownPlaceholderHeight)
                } else {
                    (containerWidth / defaultAspectRatio).coerceIn(minPlaceholderHeight, maxPlaceholderHeight)
                }
            }
        }

        val initialHeight = remember(model, containerWidth, knownRatio) { targetHeight }
        val shouldAnimateHeight = !fromMemoryCache && !reduced && abs(targetHeight.value - initialHeight.value) > 2f
        val animatedHeight by animateDpAsState(
            targetValue = targetHeight,
            animationSpec = if (shouldAnimateHeight) {
                IgngMotion.standardSpring(intensity)
            } else {
                snap()
            },
            label = "igngContentImageHeight",
        )

        val request = remember(model, widthPx, retryNonce) {
            ImageRequest.Builder(context)
                .data(model)
                .size(CoilSize(Dimension.Pixels(widthPx), Dimension.Undefined))
                .precision(Precision.INEXACT)
                .apply {
                    if (retryNonce > 0) {
                        memoryCachePolicy(CachePolicy.WRITE_ONLY)
                        diskCachePolicy(CachePolicy.WRITE_ONLY)
                    }
                }
                .build()
        }

        val interactiveMod = when {
            isSuccess && onClick != null && !uploadingOverlay -> Modifier.igngPressScale(
                haptic = true,
                onClick = onClick,
            )
            isError -> Modifier.clickable(role = Role.Button) {
                haptic(IgngHapticKind.Tap)
                loadState = IgngImageLoadState.Loading
                retryNonce++
            }
            else -> Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(animatedHeight)
                .clip(shape)
                .background(if (isError) palette.errorBase else palette.base)
                .border(
                    width = IgngStroke.hairline,
                    color = if (isError) palette.errorBorder else palette.border,
                    shape = shape,
                )
                .then(interactiveMod)
                .semantics {
                    this.contentDescription = semanticDesc
                    if (isError) this.role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            // Keep placeholder layer beneath the image until the image fully fades in
            if (isLoading || (isSuccess && imageAlpha < 0.99f)) {
                IgngQuietImagePlaceholderPlate(
                    palette = palette,
                    loading = isLoading,
                    glyphSize = FoldedCoreSize.Md,
                    frameIconSize = 24.dp,
                )
            }

            if (model != null) {
                key(retryNonce) {
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onState = { state ->
                            when (state) {
                                is AsyncImagePainter.State.Empty,
                                is AsyncImagePainter.State.Loading,
                                -> {
                                    if (loadState != IgngImageLoadState.Success) {
                                        loadState = IgngImageLoadState.Loading
                                    }
                                }
                                is AsyncImagePainter.State.Success -> {
                                    val img = state.result.image
                                    val ratio = IgngImageAspectRatioCache.sanitizeContentRatio(img.width, img.height)
                                        ?: state.painter.intrinsicSize.takeIf { it.isSpecified && it.width > 0f && it.height > 0f }
                                            ?.let { IgngImageAspectRatioCache.sanitizeContentRatio(it.width / it.height) }
                                    if (ratio != null) {
                                        resolvedRatio = ratio
                                        IgngImageAspectRatioCache.put(urlKey, ratio)
                                    }
                                    fromMemoryCache = state.result.dataSource == DataSource.MEMORY_CACHE ||
                                        state.result.dataSource == DataSource.MEMORY
                                    loadState = IgngImageLoadState.Success
                                }
                                is AsyncImagePainter.State.Error -> {
                                    fromMemoryCache = false
                                    loadState = IgngImageLoadState.Error
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = imageAlpha },
                    )
                }
            }

            if (isError) {
                IgngImageErrorPlate(
                    compact = compactError,
                    onRetry = {
                        haptic(IgngHapticKind.Tap)
                        loadState = IgngImageLoadState.Loading
                        retryNonce++
                    },
                )
            }

            if (uploadingOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.36f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(IgngTheme.radii.md)
                            .background(Color.Black.copy(alpha = 0.48f))
                            .border(IgngStroke.hairline, Color.White.copy(alpha = 0.16f), IgngTheme.radii.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        IgngFoldedCoreGlyph(
                            size = FoldedCoreSize.Md,
                            color = Color.White.copy(alpha = 0.92f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Quiet Craft fixed-size thumbnail image (e.g. cloud media assets 64dp square).
 * Keeps visual feedback minimal and calm: soft breathing + 16dp Folded Core (after 140ms)
 * and a clean neutral broken-image icon with tap-to-retry on failure.
 */
@Composable
fun IgngThumbnailImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(IgngRadius.control),
    contentScale: ContentScale = ContentScale.Crop,
    allowRetryOnError: Boolean = true,
) {
    val context = LocalContext.current
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val reduced = IgngTheme.motionReduced
    val haptic = rememberIgngHaptics()
    val palette = remember(colors) { resolveIgngImagePlaceholderPalette(colors) }

    var loadState by remember(model) { mutableStateOf(IgngImageLoadState.Loading) }
    var fromMemoryCache by remember(model) { mutableStateOf(false) }
    var retryNonce by remember(model) { mutableIntStateOf(0) }

    val isLoading = loadState == IgngImageLoadState.Loading
    val isSuccess = loadState == IgngImageLoadState.Success
    val isError = loadState == IgngImageLoadState.Error

    val imageAlpha by animateFloatAsState(
        targetValue = if (isSuccess) 1f else 0f,
        animationSpec = if (fromMemoryCache || reduced || !isSuccess) {
            snap()
        } else {
            IgngMotion.microTween(intensity)
        },
        label = "igngThumbImageAlpha",
    )

    val request = remember(model, retryNonce) {
        ImageRequest.Builder(context)
            .data(model)
            .apply {
                if (retryNonce > 0) {
                    memoryCachePolicy(CachePolicy.WRITE_ONLY)
                    diskCachePolicy(CachePolicy.WRITE_ONLY)
                }
            }
            .build()
    }

    val retryMod = if (isError && allowRetryOnError) {
        Modifier.clickable(role = Role.Button) {
            haptic(IgngHapticKind.Tap)
            loadState = IgngImageLoadState.Loading
            retryNonce++
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (isError) palette.errorBase else palette.base)
            .border(IgngStroke.hairline, palette.border, shape)
            .then(retryMod)
            .semantics {
                this.contentDescription = when {
                    isError -> "缩略图加载失败，点按重试"
                    isLoading -> contentDescription?.takeIf { it.isNotBlank() }?.let { "${it}，正在加载" } ?: "缩略图，正在加载"
                    else -> contentDescription ?: "缩略图"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading || (isSuccess && imageAlpha < 0.99f)) {
            IgngQuietImagePlaceholderPlate(
                palette = palette,
                loading = isLoading,
                glyphSize = FoldedCoreSize.Sm,
                frameIconSize = 18.dp,
            )
        }

        if (model != null) {
            key(retryNonce) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = contentScale,
                    onState = { state ->
                        when (state) {
                            is AsyncImagePainter.State.Empty,
                            is AsyncImagePainter.State.Loading,
                            -> {
                                if (loadState != IgngImageLoadState.Success) {
                                    loadState = IgngImageLoadState.Loading
                                }
                            }
                            is AsyncImagePainter.State.Success -> {
                                fromMemoryCache = state.result.dataSource == DataSource.MEMORY_CACHE ||
                                    state.result.dataSource == DataSource.MEMORY
                                loadState = IgngImageLoadState.Success
                            }
                            is AsyncImagePainter.State.Error -> {
                                fromMemoryCache = false
                                loadState = IgngImageLoadState.Error
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = imageAlpha },
                )
            }
        }

        if (isError) {
            Icon(
                imageVector = Icons.Rounded.BrokenImage,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
internal fun IgngQuietImagePlaceholderPlate(
    palette: IgngImagePlaceholderPalette,
    loading: Boolean,
    glyphSize: FoldedCoreSize,
    frameIconSize: Dp,
) {
    val breathFraction = rememberIgngImageBreathFraction(active = loading)
    val pulseAlpha = palette.pulseMinAlpha + (palette.pulseMaxAlpha - palette.pulseMinAlpha) * breathFraction
    val glyphAlpha = rememberIgngLoadingVisibility(loading = loading)
    val staticFrameAlpha = (1f - glyphAlpha).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.pulseOverlay.copy(alpha = pulseAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        if (staticFrameAlpha > 0.01f) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = palette.frameIconTint,
                modifier = Modifier
                    .size(frameIconSize)
                    .graphicsLayer { alpha = staticFrameAlpha },
            )
        }
        if (glyphAlpha > 0.01f) {
            IgngFoldedCoreGlyph(
                size = glyphSize,
                color = palette.glyphTint,
                modifier = Modifier.graphicsLayer { alpha = glyphAlpha },
            )
        }
    }
}

@Composable
private fun IgngImageErrorPlate(
    compact: Boolean,
    onRetry: () -> Unit,
) {
    val colors = IgngTheme.colors
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.BrokenImage,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(if (compact) 22.dp else 26.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "图片未能加载",
            style = IgngTheme.type.caption,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
        val pillShape = RoundedCornerShape(8.dp)
        Row(
            modifier = Modifier
                .clip(pillShape)
                .background(colors.tile)
                .border(IgngStroke.hairline, colors.lineStrong, pillShape)
                .clickable(onClick = onRetry)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = "点击重试",
                style = IgngTheme.type.badge,
                color = colors.accent,
            )
        }
    }
}
