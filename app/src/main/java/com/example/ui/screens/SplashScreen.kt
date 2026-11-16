package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.ui.components.BlackseaLogo
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.PhoenixAmber
import com.example.ui.theme.blackseaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BlackseaSplashScreen(
    modifier: Modifier = Modifier
) {
    val ext = MaterialTheme.blackseaColors

    val logoScale = remember { Animatable(0.68f) }
    val logoAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetDp = remember { Animatable(18f) }
    val mottoAlpha = remember { Animatable(0f) }
    val footerAlpha = remember { Animatable(0f) }
    val loadingProgress = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "splash_orbit")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_angle"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    LaunchedEffect(Unit) {
        launch {
            logoAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
            )
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            delay(130)
            launch {
                titleAlpha.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
            }
            launch {
                titleOffsetDp.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
            }
        }
        launch {
            delay(250)
            mottoAlpha.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        }
        launch {
            delay(200)
            footerAlpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        }
        launch {
            delay(120)
            loadingProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1120, easing = FastOutSlowInEasing)
            )
        }
    }

    val backgroundBrush = remember(ext.isDark) {
        if (ext.isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF070C17),
                    Color(0xFF0E182D),
                    Color(0xFF13223F),
                    Color(0xFF090F1E)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFE6EFFC),
                    Color(0xFFF1F5F9),
                    Color(0xFFEFF6FF),
                    Color(0xFFDEEAFB)
                )
            )
        }
    }

    val ringPrimaryColor = if (ext.isDark) Color(0xFF38BDF8) else ElectricBlue
    val ringSecondaryColor = if (ext.isDark) PhoenixAmber else Color(0xFFD97706)
    val gridLineColor = if (ext.isDark) Color(0xFF38BDF8).copy(alpha = 0.06f) else Color(0xFF0F2B5C).copy(alpha = 0.05f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .testTag("splash_screen")
    ) {
        // Subtle technical blueprint grid + radial glow + orbital robotics rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.44f)
            val minDim = size.minDimension

            // Fine robotics telemetry grid
            val step = 42.dp.toPx()
            var x = (size.width % step) / 2f
            while (x < size.width) {
                drawLine(
                    color = gridLineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                x += step
            }
            var y = (size.height % step) / 2f
            while (y < size.height) {
                drawLine(
                    color = gridLineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
                y += step
            }

            // Atmospheric center aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ringPrimaryColor.copy(alpha = if (ext.isDark) 0.22f else 0.14f),
                        ringSecondaryColor.copy(alpha = if (ext.isDark) 0.08f else 0.05f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = minDim * 0.56f
                ),
                radius = minDim * 0.56f,
                center = center
            )

            // Outer orbital arc ring
            val outerRadius = 112.dp.toPx() * pulseScale
            drawCircle(
                color = ringPrimaryColor.copy(alpha = if (ext.isDark) 0.16f else 0.12f),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawArc(
                color = ringPrimaryColor.copy(alpha = 0.72f),
                startAngle = orbitAngle,
                sweepAngle = 72f,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = androidx.compose.ui.geometry.Size(outerRadius * 2f, outerRadius * 2f),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = ringSecondaryColor.copy(alpha = 0.78f),
                startAngle = orbitAngle + 180f,
                sweepAngle = 56f,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = androidx.compose.ui.geometry.Size(outerRadius * 2f, outerRadius * 2f),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Hero Crest + Brand Typography
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (ext.isDark) Color(0xFF151E38) else Color.White,
                    border = BorderStroke(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                ElectricBlue.copy(alpha = 0.7f),
                                PhoenixAmber.copy(alpha = 0.7f)
                            )
                        )
                    ),
                    shadowElevation = if (ext.isDark) 12.dp else 8.dp,
                    modifier = Modifier
                        .size(144.dp)
                        .scale(logoScale.value)
                        .alpha(logoAlpha.value)
                        .testTag("splash_logo")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        BlackseaLogo(size = 112)
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer {
                        alpha = titleAlpha.value
                        translationY = titleOffsetDp.value.dp.toPx()
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.6.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("splash_title")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = stringResource(id = R.string.splash_subtitle),
                        color = ext.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PhoenixAmber.copy(alpha = if (ext.isDark) 0.16f else 0.18f),
                    border = BorderStroke(1.dp, ext.amberText.copy(alpha = 0.30f)),
                    modifier = Modifier.alpha(mottoAlpha.value)
                ) {
                    Text(
                        text = stringResource(id = R.string.splash_motto),
                        color = ext.amberText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }

            // Bottom Loading Sweep & Version Indicator
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 320.dp)
                    .padding(bottom = 12.dp)
                    .alpha(footerAlpha.value),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(ext.cardBorder.copy(alpha = 0.6f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(loadingProgress.value.coerceIn(0.08f, 1f))
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(ElectricBlue, PhoenixAmber)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(id = R.string.splash_loading_label),
                    color = ext.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    color = ext.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}
