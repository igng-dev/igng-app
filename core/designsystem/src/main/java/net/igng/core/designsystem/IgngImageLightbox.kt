package net.igng.core.designsystem

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.decode.DataSource
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.igng.core.model.MotionIntensity

/**
 * Full-screen gesture lightbox supporting pinch zoom, double-tap zoom, spring-physics vertical
 * drag-to-dismiss, and directional height-aware predictive back dismissal.
 */
@Composable
fun IgngImageLightbox(
    imageUrl: String,
    title: String? = null,
    onDismiss: () -> Unit,
) {
    IgngImageLightbox(
        imageUrls = listOf(imageUrl),
        initialIndex = 0,
        titles = listOf(title),
        onDismiss = onDismiss,
    )
}

/**
 * Multi-image gesture lightbox supporting horizontal swipe paging, pinch/double-tap zoom,
 * spring-physics vertical drag-to-dismiss, and directional height-aware predictive back dismissal.
 */
@Composable
fun IgngImageLightbox(
    imageUrls: List<String>,
    initialIndex: Int = 0,
    titles: List<String?> = emptyList(),
    onDismiss: () -> Unit,
) {
    if (imageUrls.isEmpty()) return
    val safeInitial = initialIndex.coerceIn(0, imageUrls.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeInitial) { imageUrls.size }
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val haptic = rememberIgngHaptics()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current

    // Lightweight prefetch of immediate neighbor images when browsing multi-image galleries
    LaunchedEffect(pagerState.currentPage, imageUrls) {
        if (imageUrls.size <= 1) return@LaunchedEffect
        val loader = SingletonImageLoader.get(context)
        listOf(pagerState.currentPage - 1, pagerState.currentPage + 1)
            .filter { it in imageUrls.indices }
            .forEach { idx ->
                loader.enqueue(ImageRequest.Builder(context).data(imageUrls[idx]).build())
            }
    }

    val dragYAnim = remember { Animatable(0f) }
    val dismissAlphaAnim = remember { Animatable(1f) }
    var verticalDismissing by remember { mutableStateOf(false) }

    // Predictive-back state inside the Dialog window (reuses the same Bento Peel physics engine)
    val backProgressAnim = remember { Animatable(0f) }
    val backCommitAnim = remember { Animatable(0f) }
    var backEdge by remember { mutableStateOf(IgngPredictiveBackEdge.NONE) }
    var backStartY by remember { mutableFloatStateOf(Float.NaN) }
    var backCurrentY by remember { mutableFloatStateOf(Float.NaN) }
    var backThresholdCrossed by remember { mutableStateOf(false) }
    var backActive by remember { mutableStateOf(false) }
    var backCancelJob by remember { mutableStateOf<Job?>(null) }

    val dragDismissThresholdPx = with(density) { 120.dp.toPx() }
    val dragBackdropFadeRangePx = with(density) { 360.dp.toPx() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        PredictiveBackHandler(enabled = !verticalDismissing) { backEvents ->
            var lastTimeMs = 0L
            var lastProgress = 0f
            var smoothedVel = 0f
            var started = false
            try {
                backEvents.collect { event ->
                    val edge = IgngPredictiveBackEdge.fromCompat(event.swipeEdge)
                    val p = if (event.progress.isFinite()) event.progress.coerceIn(0f, 1f) else 0f
                    if (!started) {
                        started = true
                        backCancelJob?.cancel()
                        backCancelJob = null
                        backEdge = edge
                        backStartY = event.touchY
                        backCurrentY = event.touchY
                        backThresholdCrossed = false
                        backActive = edge != IgngPredictiveBackEdge.NONE && intensity != MotionIntensity.REDUCED
                        backCommitAnim.snapTo(0f)
                    } else if (event.touchY.isFinite() && event.touchY >= 0f) {
                        backCurrentY = if (backCurrentY.isFinite() && backCurrentY >= 0f) {
                            backCurrentY * 0.35f + event.touchY * 0.65f
                        } else {
                            event.touchY
                        }
                        if (!backStartY.isFinite() || backStartY < 0f) {
                            backStartY = event.touchY
                        }
                    }

                    if (event.frameTimeMillis > lastTimeMs && lastTimeMs > 0L) {
                        val dtSec = (event.frameTimeMillis - lastTimeMs).coerceAtLeast(1L) / 1000f
                        val inst = ((p - lastProgress) / dtSec).coerceIn(0f, 5f)
                        smoothedVel = smoothedVel * 0.6f + inst * 0.4f
                    }
                    lastTimeMs = event.frameTimeMillis
                    lastProgress = p

                    if (backActive) {
                        val (nextCrossed, shouldTick) = IgngPredictiveBackPhysics.evaluateHapticDetent(
                            rawProgress = p,
                            alreadyTriggered = backThresholdCrossed,
                        )
                        backThresholdCrossed = nextCrossed
                        if (shouldTick) haptic(IgngHapticKind.Tick)
                        backProgressAnim.snapTo(p)
                    }
                }

                // Gesture committed
                if (backActive && intensity != MotionIntensity.REDUCED) {
                    val durMs = IgngPredictiveBackPhysics.computeCommitDurationMs(
                        releaseProgress = backProgressAnim.value,
                        velocityPerSec = smoothedVel,
                        intensity = intensity,
                    )
                    backCommitAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = durMs, easing = LinearEasing),
                    )
                }
                onDismiss()
            } catch (e: CancellationException) {
                backThresholdCrossed = false
                if (backActive) {
                    backCancelJob?.cancel()
                    backCancelJob = scope.launch {
                        val spec = when (intensity) {
                            MotionIntensity.REDUCED -> tween<Float>(0)
                            MotionIntensity.CALM -> tween(200, easing = IgngMotion.Ease)
                            MotionIntensity.FULL -> spring(dampingRatio = 0.80f, stiffness = 520f)
                        }
                        backProgressAnim.animateTo(0f, spec)
                        backActive = false
                        backEdge = IgngPredictiveBackEdge.NONE
                    }
                }
                throw e
            }
        }

        val currentDragY = dragYAnim.value
        val dragBackdropFactor = (1f - (abs(currentDragY) / dragBackdropFadeRangePx)).coerceIn(0.18f, 1f)
        val backDragFade = 1f - IgngPredictiveBackPhysics.easeDragProgress(backProgressAnim.value) * 0.58f
        val backCommitFade = 1f - backCommitAnim.value
        val backdropAlpha = (0.96f * dragBackdropFactor * backDragFade * backCommitFade * dismissAlphaAnim.value)
            .coerceIn(0f, 0.96f)

        val chromeAlpha = (
            (1f - (abs(currentDragY) / (dragDismissThresholdPx * 1.1f))).coerceIn(0f, 1f) *
                (1f - IgngPredictiveBackPhysics.easeDragProgress(backProgressAnim.value) * 1.5f).coerceIn(0f, 1f) *
                (1f - backCommitAnim.value * 2f).coerceIn(0f, 1f) *
                dismissAlphaAnim.value
            ).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backdropAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .igngPredictiveCardLayer(
                        transformProvider = { widthPx, heightPx, dens ->
                            if (backActive) {
                                IgngPredictiveBackPhysics.computeCardTransform(
                                    edge = backEdge,
                                    rawProgress = backProgressAnim.value,
                                    commitProgress = backCommitAnim.value,
                                    startTouchY = backStartY,
                                    currentTouchY = backCurrentY,
                                    widthPx = widthPx,
                                    heightPx = heightPx,
                                    density = dens,
                                    intensity = intensity,
                                    profile = IgngPredictiveBackProfile.CardPeel,
                                    thresholdCrossed = backThresholdCrossed,
                                )
                            } else {
                                IgngPredictiveCardTransform.Identity
                            }
                        },
                        canvasColor = Color.Black,
                        borderColor = Color.White.copy(alpha = 0.24f),
                        accentColor = colors.accent,
                        drawCardChrome = true,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = imageUrls.size > 1 && !backActive && !verticalDismissing,
                ) { page ->
                    val url = imageUrls[page]
                    val caption = titles.getOrNull(page)
                    ZoomableLightboxPage(
                        imageUrl = url,
                        title = caption,
                        dragY = currentDragY,
                        dismissAlpha = dismissAlphaAnim.value,
                        onDragDelta = { delta ->
                            if (!verticalDismissing && !backActive) {
                                scope.launch { dragYAnim.snapTo(dragYAnim.value + delta) }
                            }
                        },
                        onDragEnd = {
                            if (verticalDismissing) return@ZoomableLightboxPage
                            val dist = dragYAnim.value
                            if (abs(dist) >= dragDismissThresholdPx) {
                                verticalDismissing = true
                                haptic(IgngHapticKind.Tick)
                                scope.launch {
                                    if (intensity == MotionIntensity.REDUCED) {
                                        onDismiss()
                                        return@launch
                                    }
                                    val targetY = dist + (sign(dist).takeIf { it != 0f } ?: 1f) * with(density) { 260.dp.toPx() }
                                    launch {
                                        dragYAnim.animateTo(
                                            targetValue = targetY,
                                            animationSpec = tween(durationMillis = 190, easing = IgngMotion.Ease),
                                        )
                                    }
                                    dismissAlphaAnim.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(durationMillis = 175, easing = IgngMotion.Ease),
                                    )
                                    onDismiss()
                                }
                            } else {
                                scope.launch {
                                    dragYAnim.animateTo(
                                        targetValue = 0f,
                                        animationSpec = IgngMotion.standardSpring(intensity),
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            if (!verticalDismissing) {
                                scope.launch {
                                    dragYAnim.animateTo(
                                        targetValue = 0f,
                                        animationSpec = IgngMotion.standardSpring(intensity),
                                    )
                                }
                            }
                        },
                        onDismiss = onDismiss,
                    )
                }

                val currentTitle = titles.getOrNull(pagerState.currentPage)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = chromeAlpha }
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!currentTitle.isNullOrBlank()) {
                        Text(
                            text = currentTitle,
                            style = IgngTheme.type.bodyMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (imageUrls.size > 1) {
                        Box(
                            modifier = Modifier
                                .clip(IgngTheme.radii.pill)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${imageUrls.size}",
                                style = IgngTheme.type.badge,
                                color = Color.White,
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            tint = Color.White,
                        )
                    }
                }

                if (imageUrls.size > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .graphicsLayer { alpha = chromeAlpha }
                            .navigationBarsPadding()
                            .padding(bottom = 18.dp)
                            .clip(IgngTheme.radii.pill)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "左右滑动切换 · 双击放大 · 下拉或侧滑关闭",
                            style = IgngTheme.type.badge,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableLightboxPage(
    imageUrl: String,
    title: String?,
    dragY: Float,
    dismissAlpha: Float,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val intensity = IgngTheme.motionIntensity
    val reduced = IgngTheme.motionReduced
    val haptic = rememberIgngHaptics()

    var scale by remember(imageUrl) { mutableFloatStateOf(1f) }
    var offset by remember(imageUrl) { mutableStateOf(Offset.Zero) }
    var loadState by remember(imageUrl) { mutableStateOf(IgngImageLoadState.Loading) }
    var fromMemoryCache by remember(imageUrl) { mutableStateOf(false) }
    var retryNonce by remember(imageUrl) { mutableIntStateOf(0) }

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
        label = "lightboxImageAlpha",
    )

    val request = remember(imageUrl, retryNonce) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .apply {
                if (retryNonce > 0) {
                    memoryCachePolicy(CachePolicy.WRITE_ONLY)
                    diskCachePolicy(CachePolicy.WRITE_ONLY)
                }
            }
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(imageUrl, isSuccess) {
                detectTapGestures(
                    onDoubleTap = {
                        if (!isSuccess) return@detectTapGestures
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.4f
                        }
                    },
                    onTap = {
                        if (scale <= 1.05f) onDismiss()
                    },
                )
            }
            .pointerInput(imageUrl, scale, isSuccess) {
                if (!isSuccess || scale <= 1.05f) {
                    detectVerticalDragGestures(
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                    ) { _, delta ->
                        onDragDelta(delta)
                    }
                } else {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = nextScale
                        offset = if (nextScale <= 1.02f) Offset.Zero else offset + pan
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            val glyphAlpha = rememberIgngLoadingVisibility(loading = true)
            val frameIconAlpha = (1f - glyphAlpha).coerceIn(0f, 1f)
            val breath = rememberIgngImageBreathFraction(active = true)
            val trayAlpha = 0.05f + 0.05f * breath
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer {
                        translationY = dragY
                        alpha = dismissAlpha
                    }
                    .clip(IgngTheme.radii.lg)
                    .background(Color.White.copy(alpha = trayAlpha))
                    .border(IgngStroke.hairline, Color.White.copy(alpha = 0.14f), IgngTheme.radii.lg),
                contentAlignment = Alignment.Center,
            ) {
                if (frameIconAlpha > 0.01f) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = "原图加载中",
                        tint = Color.White.copy(alpha = 0.42f),
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { alpha = frameIconAlpha },
                    )
                }
                if (glyphAlpha > 0.01f) {
                    IgngFoldedCoreGlyph(
                        size = FoldedCoreSize.Md,
                        color = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.graphicsLayer { alpha = glyphAlpha },
                    )
                }
            }
        }

        key(retryNonce) {
            AsyncImage(
                model = request,
                contentDescription = title ?: "图片预览",
                contentScale = ContentScale.Fit,
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
                            scale = 1f
                            offset = Offset.Zero
                            loadState = IgngImageLoadState.Error
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val dismissScale = (1f - abs(dragY) / 1450f).coerceIn(0.76f, 1f)
                        scaleX = scale * dismissScale
                        scaleY = scale * dismissScale
                        translationX = offset.x
                        translationY = offset.y + dragY
                        alpha = dismissAlpha * imageAlpha
                    },
            )
        }

        if (isError) {
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = dragY
                        alpha = dismissAlpha
                    }
                    .clip(IgngTheme.radii.lg)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(IgngStroke.hairline, Color.White.copy(alpha = 0.16f), IgngTheme.radii.lg)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Rounded.BrokenImage,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "原图未能加载",
                    style = IgngTheme.type.bodyMedium,
                    color = Color.White.copy(alpha = 0.90f),
                )
                Spacer(Modifier.height(12.dp))
                val pillShape = RoundedCornerShape(8.dp)
                Row(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(IgngStroke.hairline, Color.White.copy(alpha = 0.24f), pillShape)
                        .clickable {
                            haptic(IgngHapticKind.Tap)
                            loadState = IgngImageLoadState.Loading
                            retryNonce++
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "重新加载",
                        style = IgngTheme.type.badge,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
fun IgngImageGalleryLightbox(
    imageUrls: List<String>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit,
) {
    IgngImageLightbox(
        imageUrls = imageUrls,
        initialIndex = initialIndex,
        onDismiss = onDismiss,
    )
}
