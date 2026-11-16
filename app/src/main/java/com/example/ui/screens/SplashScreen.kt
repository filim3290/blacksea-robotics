package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TOTAL_SPLASH_MS = 3220f
private const val SRC_DIM = 388f
private const val NORM_DIM = 194f

private val SplashBgEdge = Color(0xFF1A0432)
private val SplashBgMid = Color(0xFF28094B)
private val SplashBgCenter = Color(0xFF3E126E)

private val EnergyWhite = Color(0xFFFFFBF0)
private val EnergyGold = Color(0xFFFFC31F)
private val EnergyOrange = Color(0xFFFF6A00)

private val StrokeLavenderTop = Color(0xFFFFFFFF)
private val StrokeLavenderBottom = Color(0xFFD3AEFF)
private val StrokeOutlineDark = Color(0xFF150629)

private data class LetterSlice(val x: Int, val width: Int)

private val RoboticsSlices = listOf(
    LetterSlice(108, 26), // R
    LetterSlice(135, 22), // O
    LetterSlice(159, 21), // B
    LetterSlice(181, 22), // O
    LetterSlice(204, 20), // T
    LetterSlice(225, 7),  // I
    LetterSlice(233, 21), // C
    LetterSlice(255, 23)  // S
)

private const val ROBOTICS_SRC_Y = 282
private const val ROBOTICS_SRC_H = 28

/**
 * Sequential handwriting strokes for "BLACK SEA" mapped directly in 194x194 logo coordinates.
 * Drawn left-to-right with PathMeasure + a subtle glowing pen tip trail.
 */
private fun buildBlackSeaStrokes(): List<Path> = listOf(
    // B - spine & two italic brush bowls
    Path().apply {
        moveTo(46f, 120.5f)
        lineTo(39.5f, 136.5f)
    },
    Path().apply {
        moveTo(41.5f, 121.5f)
        quadraticTo(54.5f, 120.5f, 54f, 125f)
        quadraticTo(53.5f, 128.5f, 43.5f, 129f)
        quadraticTo(55f, 130.5f, 52.5f, 135.5f)
        quadraticTo(49f, 138f, 38f, 136.8f)
    },
    // L
    Path().apply {
        moveTo(60f, 122.8f)
        lineTo(56f, 135.2f)
        lineTo(66.5f, 134.8f)
    },
    // A (in BLACK)
    Path().apply {
        moveTo(69f, 136.2f)
        lineTo(75.5f, 123.2f)
        lineTo(76f, 136.2f)
    },
    Path().apply {
        moveTo(70.5f, 131f)
        lineTo(78.2f, 130.8f)
    },
    // C
    Path().apply {
        moveTo(89f, 126f)
        quadraticTo(86.5f, 123.5f, 82.5f, 125f)
        quadraticTo(78.5f, 129.5f, 80.5f, 135.5f)
        quadraticTo(85f, 137.5f, 89.5f, 133.5f)
    },
    // K
    Path().apply {
        moveTo(93.2f, 122.8f)
        lineTo(90f, 136.5f)
    },
    Path().apply {
        moveTo(100.8f, 124f)
        lineTo(92.5f, 130f)
        lineTo(98.8f, 136.5f)
    },
    // S (in SEA)
    Path().apply {
        moveTo(120.5f, 123f)
        quadraticTo(116f, 121f, 112f, 124f)
        quadraticTo(110.5f, 127.5f, 115.5f, 129.5f)
        quadraticTo(120.5f, 131.5f, 117.5f, 135.2f)
        quadraticTo(112.5f, 137f, 108.5f, 134f)
    },
    // E
    Path().apply {
        moveTo(133.5f, 124.2f)
        lineTo(124.8f, 124.4f)
        lineTo(121.8f, 135.2f)
        lineTo(131.8f, 135f)
    },
    Path().apply {
        moveTo(123.8f, 129.8f)
        lineTo(130.5f, 129.6f)
    },
    // A (in SEA)
    Path().apply {
        moveTo(136f, 136f)
        lineTo(143f, 124f)
        lineTo(145f, 136.5f)
    },
    Path().apply {
        moveTo(138f, 131f)
        lineTo(144.8f, 130.8f)
    }
)

private fun normTime(elapsedMs: Float, startMs: Float, endMs: Float): Float {
    if (endMs <= startMs) return 1f
    return ((elapsedMs - startMs) / (endMs - startMs)).coerceIn(0f, 1f)
}

private fun easeOutCubic(x: Float): Float {
    val inv = 1f - x.coerceIn(0f, 1f)
    return 1f - inv * inv * inv
}

private fun easeInOutCubic(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
}

private fun brightnessMatrix(brightness: Float): ColorMatrix {
    val b = brightness.coerceIn(0.2f, 1.3f)
    return ColorMatrix(
        floatArrayOf(
            b, 0f, 0f, 0f, 0f,
            0f, b, 0f, 0f, 0f,
            0f, 0f, b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

@Composable
fun BlackseaSplashScreen(
    modifier: Modifier = Modifier,
    onAnimationFinished: () -> Unit = {}
) {
    val resources = LocalContext.current.resources

    val emblemBitmap = remember(resources) {
        ImageBitmap.imageResource(resources, R.drawable.ic_bsr_logo_emblem)
    }
    val shieldBitmap = remember(resources) {
        ImageBitmap.imageResource(resources, R.drawable.ic_bsr_shield_only)
    }
    val birdBodyBitmap = remember(resources) {
        ImageBitmap.imageResource(resources, R.drawable.ic_bsr_bird_body)
    }
    val leftWingBitmap = remember(resources) {
        ImageBitmap.imageResource(resources, R.drawable.ic_bsr_bird_wing_left)
    }
    val rightWingBitmap = remember(resources) {
        ImageBitmap.imageResource(resources, R.drawable.ic_bsr_bird_wing_right)
    }
    val blackSeaTextBitmap = remember(resources) {
        ImageBitmap.imageResource(resources, R.drawable.ic_bsr_text_blacksea)
    }
    val roboticsTextBitmap = remember(resources) {
        ImageBitmap.imageResource(resources, R.drawable.ic_bsr_text_robotics)
    }

    val blackSeaStrokes = remember { buildBlackSeaStrokes() }
    val strokeLengths = remember(blackSeaStrokes) {
        val pm = PathMeasure()
        blackSeaStrokes.map { path ->
            pm.setPath(path, false)
            pm.length.coerceAtLeast(1f)
        }
    }
    val totalStrokeLength = remember(strokeLengths) {
        strokeLengths.sum().coerceAtLeast(1f)
    }
    val reusableMeasure = remember { PathMeasure() }
    val reusableSegmentPath = remember { Path() }
    val reusableScaledPath = remember { Path() }

    val elapsedMs = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        elapsedMs.animateTo(
            targetValue = TOTAL_SPLASH_MS,
            animationSpec = tween(
                durationMillis = TOTAL_SPLASH_MS.toInt(),
                easing = LinearEasing
            )
        )
        onAnimationFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SplashBgEdge)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .semantics { contentDescription = "BLACKSEA ROBOTICS Açılış Ekranı" }
            .testTag("splash_screen")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("splash_logo")
        ) {
            val t = elapsedMs.value

            // Background deep purple gradient matching logo tone
            val logoSizePx = minOf(size.width * 0.78f, size.height * 0.48f, 320.dp.toPx())
            val logoLeft = (size.width - logoSizePx) / 2f
            val logoTop = (size.height - logoSizePx) / 2f
            val s = logoSizePx / NORM_DIM

            val emblemCenter = Offset(logoLeft + 95f * s, logoTop + 88f * s)
            val birdCenter = Offset(logoLeft + 95f * s, logoTop + 72f * s)
            val leftShoulder = Offset(logoLeft + 86f * s, logoTop + 73f * s)
            val rightShoulder = Offset(logoLeft + 102f * s, logoTop + 73f * s)

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(SplashBgCenter, SplashBgMid, SplashBgEdge),
                    center = emblemCenter,
                    radius = size.maxDimension * 0.78f
                )
            )

            // Subtle dark technological grid lines (extremely low contrast)
            val gridStep = 44.dp.toPx()
            val gridColor = Color(0xFF8B5CF6).copy(alpha = 0.045f)
            var gx = (size.width % gridStep) / 2f
            while (gx < size.width) {
                drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1.dp.toPx())
                gx += gridStep
            }
            var gy = (size.height % gridStep) / 2f
            while (gy < size.height) {
                drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
                gy += gridStep
            }

            // Scene progress calculations
            val s1 = normTime(t, 0f, 500f)
            val wingOpenP = easeOutCubic(normTime(t, 500f, 920f))
            val burstP = normTime(t, 760f, 1180f)
            val shieldAlpha = easeOutCubic(normTime(t, 880f, 1220f))

            val writeP = easeInOutCubic(normTime(t, 1200f, 2240f))
            val blackSeaRasterReveal = easeOutCubic(normTime(t, 1340f, 2380f))
            val strokeFadeOut = 1f - easeOutCubic(normTime(t, 2200f, 2400f))

            val emblemLockAlpha = easeOutCubic(normTime(t, 2860f, 3000f))
            val sheenP = normTime(t, 2900f, 3160f)

            // Scene 1: subtle emerging aura before burst
            val preAuraAlpha = if (t < 880f) {
                0.26f * easeOutCubic(s1)
            } else {
                0.26f * (1f - easeOutCubic(normTime(t, 880f, 1220f)))
            }
            if (preAuraAlpha > 0.005f) {
                val auraR = logoSizePx * (0.28f + 0.14f * s1)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            EnergyGold.copy(alpha = preAuraAlpha),
                            EnergyOrange.copy(alpha = preAuraAlpha * 0.55f),
                            Color.Transparent
                        ),
                        center = birdCenter,
                        radius = auraR
                    ),
                    radius = auraR,
                    center = birdCenter
                )
            }

            // Scene 2: Dual lateral wing energy burst & shockwave behind the bird wings (~0.42s fast decay)
            if (burstP > 0f && burstP < 1f) {
                val burstEnvelope = (1f - burstP).pow(1.45f) * minOf(1f, burstP * 6f)
                val coreRadius = logoSizePx * (0.26f + 0.68f * easeOutCubic(burstP))

                // Behind-wings radiant energy core
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            EnergyWhite.copy(alpha = 0.62f * burstEnvelope),
                            EnergyGold.copy(alpha = 0.52f * burstEnvelope),
                            EnergyOrange.copy(alpha = 0.28f * burstEnvelope),
                            Color.Transparent
                        ),
                        center = birdCenter,
                        radius = coreRadius
                    ),
                    radius = coreRadius,
                    center = birdCenter
                )

                // Lateral energy shockwaves sweeping toward both sides of the screen behind the wings
                val maxHalfSpan = size.width * 0.54f
                val waveReach = maxOf(logoSizePx * 0.22f, maxHalfSpan * easeOutCubic(burstP))
                val ribbonHalfHeight = (10.dp.toPx() * (1f - 0.55f * burstP)).coerceAtLeast(2.dp.toPx())

                // Left lateral wave
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            EnergyOrange.copy(alpha = 0.55f * burstEnvelope),
                            EnergyGold.copy(alpha = 0.78f * burstEnvelope),
                            EnergyWhite.copy(alpha = 0.85f * burstEnvelope)
                        ),
                        startX = birdCenter.x - waveReach,
                        endX = birdCenter.x
                    ),
                    topLeft = Offset(birdCenter.x - waveReach, birdCenter.y - ribbonHalfHeight),
                    size = Size(waveReach, ribbonHalfHeight * 2f)
                )
                // Right lateral wave
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            EnergyWhite.copy(alpha = 0.85f * burstEnvelope),
                            EnergyGold.copy(alpha = 0.78f * burstEnvelope),
                            EnergyOrange.copy(alpha = 0.55f * burstEnvelope),
                            Color.Transparent
                        ),
                        startX = birdCenter.x,
                        endX = birdCenter.x + waveReach
                    ),
                    topLeft = Offset(birdCenter.x, birdCenter.y - ribbonHalfHeight),
                    size = Size(waveReach, ribbonHalfHeight * 2f)
                )

                // Crisp symmetric shockwave wing arcs
                val arcRadius = logoSizePx * (0.24f + 0.52f * easeOutCubic(burstP))
                val arcStroke = (3.2.dp.toPx() * (1f - 0.6f * burstP)).coerceAtLeast(1.dp.toPx())
                drawArc(
                    color = EnergyGold.copy(alpha = 0.72f * burstEnvelope),
                    startAngle = 148f,
                    sweepAngle = 64f,
                    useCenter = false,
                    topLeft = Offset(birdCenter.x - arcRadius, birdCenter.y - arcRadius * 0.78f),
                    size = Size(arcRadius * 2f, arcRadius * 1.56f),
                    style = Stroke(width = arcStroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = EnergyGold.copy(alpha = 0.72f * burstEnvelope),
                    startAngle = -32f,
                    sweepAngle = 64f,
                    useCenter = false,
                    topLeft = Offset(birdCenter.x - arcRadius, birdCenter.y - arcRadius * 0.78f),
                    size = Size(arcRadius * 2f, arcRadius * 1.56f),
                    style = Stroke(width = arcStroke, cap = StrokeCap.Round)
                )
            }

            val dstOrigin = IntOffset(logoLeft.roundToInt(), logoTop.roundToInt())
            val dstFullSize = IntSize(logoSizePx.roundToInt(), logoSizePx.roundToInt())

            // Shield backdrop fades in behind the bird once wings open
            val layerFadeUnderEmblem = (1f - emblemLockAlpha).coerceIn(0f, 1f)
            if (shieldAlpha > 0.005f && layerFadeUnderEmblem > 0.005f) {
                drawImage(
                    image = shieldBitmap,
                    dstOffset = dstOrigin,
                    dstSize = dstFullSize,
                    alpha = shieldAlpha * layerFadeUnderEmblem
                )
            }

            // Bird layers (Scenes 1..5 crossfade into completed emblem)
            val birdAlpha = when {
                t < 500f -> 0.14f + 0.64f * easeOutCubic(s1)
                else -> 0.78f + 0.22f * easeOutCubic(normTime(t, 500f, 760f))
            } * layerFadeUnderEmblem

            val birdBrightness = when {
                t < 500f -> 0.52f + 0.26f * s1
                else -> 0.78f + 0.22f * easeOutCubic(normTime(t, 500f, 840f))
            }
            val birdColorFilter = if (birdBrightness < 0.995f) {
                ColorFilter.colorMatrix(brightnessMatrix(birdBrightness))
            } else {
                null
            }

            val birdSurgeScale = when {
                t < 500f -> 0.85f + 0.07f * easeOutCubic(s1)
                t < 880f -> 0.92f + 0.115f * easeOutCubic(normTime(t, 500f, 880f))
                t < 1140f -> 1.035f - 0.035f * easeOutCubic(normTime(t, 880f, 1140f))
                else -> 1f
            }

            if (birdAlpha > 0.005f) {
                withTransform({
                    scale(birdSurgeScale, birdSurgeScale, pivot = birdCenter)
                }) {
                    // Left wing opening from tucked posture
                    val wingTiltDeg = 35f * (1f - wingOpenP)
                    val wingScaleX = 0.56f + 0.44f * wingOpenP
                    val wingScaleY = 0.88f + 0.12f * wingOpenP

                    withTransform({
                        rotate(-wingTiltDeg, pivot = leftShoulder)
                        scale(wingScaleX, wingScaleY, pivot = leftShoulder)
                    }) {
                        drawImage(
                            image = leftWingBitmap,
                            dstOffset = dstOrigin,
                            dstSize = dstFullSize,
                            alpha = birdAlpha,
                            colorFilter = birdColorFilter
                        )
                    }

                    // Right wing opening symmetrically
                    withTransform({
                        rotate(wingTiltDeg, pivot = rightShoulder)
                        scale(wingScaleX, wingScaleY, pivot = rightShoulder)
                    }) {
                        drawImage(
                            image = rightWingBitmap,
                            dstOffset = dstOrigin,
                            dstSize = dstFullSize,
                            alpha = birdAlpha,
                            colorFilter = birdColorFilter
                        )
                    }

                    // Center bird body + head + tail overlapping wing shoulder roots cleanly
                    drawImage(
                        image = birdBodyBitmap,
                        dstOffset = dstOrigin,
                        dstSize = dstFullSize,
                        alpha = birdAlpha,
                        colorFilter = birdColorFilter
                    )
                }
            }

            // Scene 3: "BLACK SEA" left-to-right handwriting PathMeasure animation + authentic logo text reveal
            if (blackSeaRasterReveal > 0.005f && layerFadeUnderEmblem > 0.005f) {
                val revealRight = logoLeft + (32f + 120f * blackSeaRasterReveal) * s
                clipRect(
                    left = logoLeft,
                    top = logoTop + 115f * s,
                    right = revealRight,
                    bottom = logoTop + 144f * s
                ) {
                    drawImage(
                        image = blackSeaTextBitmap,
                        dstOffset = dstOrigin,
                        dstSize = dstFullSize,
                        alpha = minOf(1f, blackSeaRasterReveal * 1.35f) * layerFadeUnderEmblem
                    )
                }
            }

            if (writeP > 0f && strokeFadeOut > 0.005f && layerFadeUnderEmblem > 0.005f) {
                val targetDist = totalStrokeLength * writeP
                var accumulated = 0f
                var activeTip: Offset? = null
                val strokeAlpha = strokeFadeOut * layerFadeUnderEmblem

                for (i in blackSeaStrokes.indices) {
                    val segLen = strokeLengths[i]
                    if (accumulated >= targetDist) break

                    val localEnd = (targetDist - accumulated).coerceIn(0f, segLen)
                    reusableMeasure.setPath(blackSeaStrokes[i], false)
                    reusableSegmentPath.reset()
                    reusableMeasure.getSegment(0f, localEnd, reusableSegmentPath, true)

                    // Map normalized 194-space path cleanly into current Canvas pixels
                    reusableScaledPath.reset()
                    reusableScaledPath.addPath(reusableSegmentPath)
                    val scaleMatrix = androidx.compose.ui.graphics.Matrix().apply {
                        translate(logoLeft, logoTop)
                        scale(s, s)
                    }
                    reusableScaledPath.transform(scaleMatrix)

                    // Dark outline under-stroke + crisp lavender-white handwritten brush stroke
                    drawPath(
                        path = reusableScaledPath,
                        color = StrokeOutlineDark.copy(alpha = 0.72f * strokeAlpha),
                        style = Stroke(
                            width = 4.6f * s,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    drawPath(
                        path = reusableScaledPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                StrokeLavenderTop.copy(alpha = 0.92f * strokeAlpha),
                                StrokeLavenderBottom.copy(alpha = 0.88f * strokeAlpha)
                            ),
                            startY = logoTop + 120f * s,
                            endY = logoTop + 138f * s
                        ),
                        style = Stroke(
                            width = 2.6f * s,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    if (accumulated + segLen >= targetDist && writeP < 0.995f) {
                        val posNorm = reusableMeasure.getPosition(localEnd)
                        activeTip = Offset(logoLeft + posNorm.x * s, logoTop + posNorm.y * s)
                    }

                    accumulated += segLen
                }

                // Subtle pen-tip light trail while writing
                activeTip?.let { tip ->
                    val glowR = 8.5f * s
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                EnergyWhite.copy(alpha = 0.85f * strokeAlpha),
                                StrokeLavenderBottom.copy(alpha = 0.45f * strokeAlpha),
                                Color.Transparent
                            ),
                            center = tip,
                            radius = glowR
                        ),
                        radius = glowR,
                        center = tip
                    )
                    drawCircle(
                        color = EnergyWhite.copy(alpha = 0.95f * strokeAlpha),
                        radius = 1.9f * s,
                        center = tip
                    )
                }
            }

            // Scene 4: "ROBOTICS" staggered letter-by-letter fade + slide-in reveal (R -> O -> B -> O -> T -> I -> C -> S)
            if (t >= 2100f && layerFadeUnderEmblem > 0.005f) {
                RoboticsSlices.forEachIndexed { idx, slice ->
                    val letterStart = 2100f + idx * 68f
                    val letterP = easeOutCubic(normTime(t, letterStart, letterStart + 220f))
                    if (letterP > 0.005f) {
                        val dstLeft = (logoLeft + (slice.x / SRC_DIM) * logoSizePx).roundToInt()
                        val dstRight = (logoLeft + ((slice.x + slice.width) / SRC_DIM) * logoSizePx).roundToInt()
                        val dstTopBase = logoTop + (ROBOTICS_SRC_Y / SRC_DIM) * logoSizePx
                        val dstBottomBase = logoTop + ((ROBOTICS_SRC_Y + ROBOTICS_SRC_H) / SRC_DIM) * logoSizePx
                        val slidePx = (1f - letterP) * 8.dp.toPx()

                        val letterCenter = Offset(
                            (dstLeft + dstRight) / 2f,
                            (dstTopBase + dstBottomBase) / 2f + slidePx
                        )
                        val letterScale = 0.91f + 0.09f * letterP

                        withTransform({
                            scale(letterScale, letterScale, pivot = letterCenter)
                        }) {
                            drawImage(
                                image = roboticsTextBitmap,
                                srcOffset = IntOffset(slice.x, ROBOTICS_SRC_Y),
                                srcSize = IntSize(slice.width, ROBOTICS_SRC_H),
                                dstOffset = IntOffset(dstLeft, (dstTopBase + slidePx).roundToInt()),
                                dstSize = IntSize(
                                    (dstRight - dstLeft).coerceAtLeast(1),
                                    (dstBottomBase - dstTopBase).roundToInt().coerceAtLeast(1)
                                ),
                                alpha = letterP * layerFadeUnderEmblem
                            )
                        }
                    }
                }
            }

            // Scene 5: Complete official logo lock-in + subtle brief diagonal light shimmer
            if (emblemLockAlpha > 0.005f) {
                drawImage(
                    image = emblemBitmap,
                    dstOffset = dstOrigin,
                    dstSize = dstFullSize,
                    alpha = emblemLockAlpha
                )

                if (sheenP > 0f && sheenP < 1f) {
                    val sheenAlpha = (sin(sheenP * PI).toFloat()).coerceIn(0f, 1f) * 0.24f
                    drawCompletedSheen(
                        logoLeft = logoLeft,
                        logoTop = logoTop,
                        logoSizePx = logoSizePx,
                        sheenProgress = sheenP,
                        sheenAlpha = sheenAlpha
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawCompletedSheen(
    logoLeft: Float,
    logoTop: Float,
    logoSizePx: Float,
    sheenProgress: Float,
    sheenAlpha: Float
) {
    val sweepCenterX = logoLeft - logoSizePx * 0.25f + logoSizePx * 1.5f * sheenProgress
    val bandWidth = logoSizePx * 0.26f
    clipRect(
        left = logoLeft + logoSizePx * 0.14f,
        top = logoTop + logoSizePx * 0.14f,
        right = logoLeft + logoSizePx * 0.86f,
        bottom = logoTop + logoSizePx * 0.86f
    ) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    EnergyWhite.copy(alpha = sheenAlpha),
                    EnergyGold.copy(alpha = sheenAlpha * 0.65f),
                    Color.Transparent
                ),
                start = Offset(sweepCenterX - bandWidth, logoTop),
                end = Offset(sweepCenterX + bandWidth, logoTop + logoSizePx)
            ),
            topLeft = Offset(logoLeft, logoTop),
            size = Size(logoSizePx, logoSizePx)
        )
    }
}
