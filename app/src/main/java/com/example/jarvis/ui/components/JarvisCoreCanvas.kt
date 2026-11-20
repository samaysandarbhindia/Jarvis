package com.example.jarvis.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.jarvis.ai.AssistantState
import com.example.jarvis.ai.PendingConfirmation
import com.example.ui.theme.ArcCobalt
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JarvisDeepBlack
import com.example.ui.theme.JarvisMidnightNavy
import com.example.ui.theme.JarvisPanelNavy
import com.example.ui.theme.StarkAmber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JarvisHudBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "hud_particles")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        JarvisDeepBlack,
                        JarvisMidnightNavy,
                        JarvisDeepBlack
                    )
                )
            )
            .drawBehind {
                val w = size.width
                val h = size.height
                // Subtle ambient core glow behind top-center
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.5f, h * 0.30f),
                        radius = w * 0.65f
                    ),
                    center = Offset(w * 0.5f, h * 0.30f),
                    radius = w * 0.65f
                )
                // Faint HUD grid lines
                val step = 48.dp.toPx()
                var x = step
                while (x < w) {
                    drawLine(
                        color = accent.copy(alpha = 0.03f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1f
                    )
                    x += step
                }
                var y = step
                while (y < h) {
                    drawLine(
                        color = accent.copy(alpha = 0.03f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                    y += step
                }
                // Subtle drifting holographic particles
                for (i in 0 until 22) {
                    val px = ((i * 73 + 19) % 100) / 100f * w
                    val baseY = ((i * 41 + 7) % 100) / 100f
                    val py = ((baseY + drift * (0.25f + (i % 3) * 0.1f)) % 1f) * h
                    val r = (1.2f + (i % 3) * 0.7f).dp.toPx()
                    drawCircle(
                        color = accent.copy(alpha = 0.14f + (i % 4) * 0.05f),
                        radius = r,
                        center = Offset(px, py)
                    )
                }
            }
    ) {
        content()
    }
}

@Composable
fun GlassHudCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    containerColor: Color = JarvisPanelNavy.copy(alpha = 0.84f),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

@Composable
fun JarvisCoreVisualizer(
    assistantState: AssistantState,
    audioLevel: Float,
    assistantName: String,
    modifier: Modifier = Modifier,
    coreSize: Dp = 230.dp
) {
    val baseAccent = MaterialTheme.colorScheme.primary
    val targetColor = when (assistantState) {
        AssistantState.IDLE -> baseAccent
        AssistantState.LISTENING -> ElectricCyan
        AssistantState.PROCESSING -> ArcCobalt
        AssistantState.EXECUTING -> StarkAmber
        AssistantState.SPEAKING -> ElectricCyan
        AssistantState.ERROR -> CrimsonAlert
    }
    val activeColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(350),
        label = "core_color"
    )

    val smoothedAudio by animateFloatAsState(
        targetValue = when (assistantState) {
            AssistantState.LISTENING -> audioLevel.coerceIn(0.08f, 1f)
            AssistantState.SPEAKING -> 0.55f
            AssistantState.PROCESSING, AssistantState.EXECUTING -> 0.35f
            else -> 0.08f
        },
        animationSpec = spring(stiffness = 220f),
        label = "audio_smooth"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_core_anim")
    val outerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (assistantState) {
                    AssistantState.PROCESSING -> 3600
                    AssistantState.EXECUTING -> 2800
                    AssistantState.LISTENING -> 6000
                    else -> 14_000
                },
                easing = LinearEasing
            )
        ),
        label = "outer_angle"
    )
    val innerAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (assistantState) {
                    AssistantState.PROCESSING -> 2800
                    AssistantState.EXECUTING -> 2200
                    else -> 10_000
                },
                easing = LinearEasing
            )
        ),
        label = "inner_angle"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier.size(coreSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f

            // Ambient radial halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        activeColor.copy(alpha = 0.28f * pulse),
                        activeColor.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxR
                ),
                radius = maxR,
                center = center
            )

            // Outer calibrated HUD tick ring
            val tickRadius = maxR * 0.93f
            for (deg in 0 until 360 step 6) {
                val rad = (deg * PI / 180.0).toFloat()
                val isMajor = deg % 30 == 0
                val inner = tickRadius - if (isMajor) 8.dp.toPx() else 4.dp.toPx()
                drawLine(
                    color = activeColor.copy(alpha = if (isMajor) 0.7f else 0.25f),
                    start = Offset(center.x + cos(rad) * inner, center.y + sin(rad) * inner),
                    end = Offset(center.x + cos(rad) * tickRadius, center.y + sin(rad) * tickRadius),
                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                )
            }

            // Counter-rotating segmented holographic arcs
            rotate(degrees = outerAngle, pivot = center) {
                val arcR = maxR * 0.80f
                val topLeft = Offset(center.x - arcR, center.y - arcR)
                val arcSize = Size(arcR * 2f, arcR * 2f)
                listOf(0f, 120f, 240f).forEach { start ->
                    drawArc(
                        color = activeColor.copy(alpha = 0.85f),
                        startAngle = start,
                        sweepAngle = 72f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            rotate(degrees = innerAngle, pivot = center) {
                val arcR2 = maxR * 0.67f
                val topLeft2 = Offset(center.x - arcR2, center.y - arcR2)
                val arcSize2 = Size(arcR2 * 2f, arcR2 * 2f)
                listOf(30f, 150f, 270f).forEach { start ->
                    drawArc(
                        color = ArcCobalt.copy(alpha = 0.9f),
                        startAngle = start,
                        sweepAngle = 60f,
                        useCenter = false,
                        topLeft = topLeft2,
                        size = arcSize2,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Circular reactive audio waveform (48 radial bars)
            val waveBaseR = maxR * 0.50f
            val waveCount = 48
            for (i in 0 until waveCount) {
                val angleDeg = i * (360f / waveCount)
                val rad = (angleDeg * PI / 180.0).toFloat()
                val harmonic = 0.55f + 0.45f * sin((i * 0.75f + outerAngle * 0.08f))
                val barLen = (4.dp.toPx() + 22.dp.toPx() * smoothedAudio * harmonic)
                val start = Offset(
                    center.x + cos(rad) * waveBaseR,
                    center.y + sin(rad) * waveBaseR
                )
                val end = Offset(
                    center.x + cos(rad) * (waveBaseR + barLen),
                    center.y + sin(rad) * (waveBaseR + barLen)
                )
                drawLine(
                    color = activeColor.copy(alpha = 0.88f),
                    start = start,
                    end = end,
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Central luminous core orb
            val coreR = maxR * 0.37f * (if (assistantState == AssistantState.IDLE) pulse else 1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f),
                        activeColor.copy(alpha = 0.75f),
                        activeColor.copy(alpha = 0.18f),
                        JarvisDeepBlack
                    ),
                    center = center,
                    radius = coreR
                ),
                radius = coreR,
                center = center
            )
            drawCircle(
                color = activeColor,
                radius = coreR,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = assistantName.uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = assistantState.displayLabel,
                style = MaterialTheme.typography.labelSmall,
                color = activeColor
            )
        }
    }
}

@Composable
fun ConfirmationBannerCard(
    pending: PendingConfirmation,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassHudCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = StarkAmber.copy(alpha = 0.7f),
        containerColor = JarvisPanelNavy.copy(alpha = 0.95f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Confirmation required",
                tint = StarkAmber
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = pending.title,
                style = MaterialTheme.typography.titleMedium,
                color = StarkAmber
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = pending.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = StarkAmber),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Confirm & Execute", color = JarvisDeepBlack)
            }
        }
    }
}
