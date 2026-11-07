package it.luma.tapo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.luma.tapo.ui.theme.LocalLumaColors

/**
 * Vector Canvas recreation of Luma's 3D-style bulb artwork (viewBox 280x360),
 * orbital rings, sparkles, and reactive radial light glow.
 */
@Composable
fun LampStageIllustration(
    lightColor: Color,
    brightnessPercent: Int,
    isLit: Boolean,
    modifier: Modifier = Modifier
) {
    val lumaColors = LocalLumaColors.current
    val animatedTint by animateColorAsState(
        targetValue = if (isLit) lightColor else Color(0xFFD5CBD8),
        animationSpec = tween(420),
        label = "bulbTint"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isLit) {
            val base = 0.45f + (brightnessPercent.coerceIn(1, 100) / 100f) * 0.50f
            if (lumaColors.isDark) base * 0.78f else base
        } else {
            0.07f
        },
        animationSpec = tween(420),
        label = "glowAlpha"
    )
    val bulbAlpha by animateFloatAsState(
        targetValue = if (isLit) 1f else 0.76f,
        animationSpec = tween(420),
        label = "bulbAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(255.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(265.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)

            // Radial ambient glow behind the bulb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedTint.copy(alpha = 0.50f * glowAlpha),
                        animatedTint.copy(alpha = 0.20f * glowAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.56f
                ),
                radius = size.minDimension * 0.56f,
                center = center
            )

            // Orbit rings
            val orbitColor = if (lumaColors.isDark) {
                Color(0x24FFF4FF)
            } else {
                Color(0x70FFFFFF)
            }
            val r1 = size.minDimension * 0.43f
            val r2 = size.minDimension * 0.53f
            drawCircle(
                color = orbitColor,
                radius = r1,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = orbitColor.copy(alpha = orbitColor.alpha * 0.6f),
                radius = r2,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Small orbital glowing nodes
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = 3.dp.toPx(),
                center = Offset(center.x - r1 * 0.86f, center.y - r1 * 0.49f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = 2.4.dp.toPx(),
                center = Offset(center.x + r2 * 0.80f, center.y + r2 * 0.58f)
            )

            // Scale viewBox 280x360 centered in canvas
            val scale = size.height / 360f
            val ox = (size.width - 280f * scale) / 2f
            val oy = 0f
            fun sx(x: Float) = ox + x * scale
            fun sy(y: Float) = oy + y * scale

            // Base drop shadow ellipse
            drawOval(
                color = Color(0xFF80738C).copy(alpha = if (lumaColors.isDark) 0.30f else 0.16f),
                topLeft = Offset(sx(71f), sy(297f)),
                size = Size(138f * scale, 22f * scale)
            )

            // Bulb envelope path:
            // M140 29c-61 0-102 44-102 101 0 40 21 62 39 83 9 11 14 23 16 38h94c2-15 7-27 16-38 18-21 39-43 39-83 0-57-41-101-102-101Z
            val bulbPath = Path().apply {
                moveTo(sx(140f), sy(29f))
                cubicTo(sx(79f), sy(29f), sx(38f), sy(73f), sx(38f), sy(130f))
                cubicTo(sx(38f), sy(170f), sx(59f), sy(192f), sx(77f), sy(213f))
                cubicTo(sx(86f), sy(224f), sx(91f), sy(236f), sx(93f), sy(251f))
                lineTo(sx(187f), sy(251f))
                cubicTo(sx(189f), sy(236f), sx(194f), sy(224f), sx(203f), sy(213f))
                cubicTo(sx(221f), sy(192f), sx(242f), sy(170f), sx(242f), sy(130f))
                cubicTo(sx(242f), sy(73f), sx(201f), sy(29f), sx(140f), sy(29f))
                close()
            }

            // Glass base fill
            drawPath(
                path = bulbPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFEF9).copy(alpha = bulbAlpha),
                        Color(0xFFFFF9E9).copy(alpha = bulbAlpha),
                        Color(0xFFF5E6D6).copy(alpha = bulbAlpha),
                        Color(0xFFD3CBD4).copy(alpha = bulbAlpha)
                    ),
                    center = Offset(sx(118f), sy(108f)),
                    radius = 185f * scale
                )
            )

            // Dynamic bulb color tint overlay
            val tintFactor = if (isLit) (0.35f + (brightnessPercent / 100f) * 0.45f) else 0.08f
            drawPath(
                path = bulbPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedTint.copy(alpha = tintFactor),
                        animatedTint.copy(alpha = tintFactor * 0.42f),
                        animatedTint.copy(alpha = 0.02f)
                    ),
                    center = Offset(sx(140f), sy(148f)),
                    radius = 150f * scale
                )
            )

            // Glass rim stroke
            drawPath(
                path = bulbPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),
                        Color.White.copy(alpha = 0.25f),
                        Color(0xFF9E98A6).copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.80f)
                    ),
                    start = Offset(sx(38f), sy(29f)),
                    end = Offset(sx(242f), sy(251f))
                ),
                style = Stroke(width = 2f * scale)
            )

            // Specular glass highlights
            val highlight1 = Path().apply {
                moveTo(sx(76f), sy(91f))
                cubicTo(sx(84f), sy(70f), sx(98f), sy(56f), sx(118f), sy(50f))
            }
            drawPath(
                path = highlight1,
                color = Color.White.copy(alpha = 0.72f * bulbAlpha),
                style = Stroke(width = 7f * scale, cap = StrokeCap.Round)
            )

            val highlight2 = Path().apply {
                moveTo(sx(67f), sy(108f))
                cubicTo(sx(64f), sy(118f), sx(63f), sy(128f), sx(64f), sy(138f))
            }
            drawPath(
                path = highlight2,
                color = Color.White.copy(alpha = 0.44f * bulbAlpha),
                style = Stroke(width = 4f * scale, cap = StrokeCap.Round)
            )

            val highlight3 = Path().apply {
                moveTo(sx(214f), sy(112f))
                cubicTo(sx(221f), sy(145f), sx(208f), sy(174f), sx(191f), sy(193f))
            }
            drawPath(
                path = highlight3,
                color = Color.White.copy(alpha = 0.35f * bulbAlpha),
                style = Stroke(width = 4f * scale, cap = StrokeCap.Round)
            )

            // Filament stems and coil
            val filamentAlpha = if (isLit) 0.58f else 0.20f
            val filamentStems = Path().apply {
                moveTo(sx(118f), sy(236f))
                cubicTo(sx(115f), sy(213f), sx(111f), sy(190f), sx(106f), sy(175f))
                moveTo(sx(162f), sy(236f))
                cubicTo(sx(165f), sy(213f), sx(169f), sy(190f), sx(174f), sy(175f))
            }
            drawPath(
                path = filamentStems,
                color = Color(0xFFD99E64).copy(alpha = filamentAlpha * 0.5f),
                style = Stroke(width = 2f * scale, cap = StrokeCap.Round)
            )

            val coilPath = Path().apply {
                moveTo(sx(106f), sy(175f))
                cubicTo(sx(113f), sy(178f), sx(115f), sy(178f), sx(118f), sy(172f))
                cubicTo(sx(121f), sy(167f), sx(124f), sy(167f), sx(127f), sy(172f))
                cubicTo(sx(130f), sy(177f), sx(133f), sy(177f), sx(136f), sy(172f))
                cubicTo(sx(139f), sy(167f), sx(142f), sy(167f), sx(145f), sy(172f))
                cubicTo(sx(148f), sy(177f), sx(151f), sy(177f), sx(154f), sy(172f))
                cubicTo(sx(157f), sy(167f), sx(160f), sy(167f), sx(163f), sy(172f))
                cubicTo(sx(166f), sy(178f), sx(168f), sy(178f), sx(174f), sy(175f))
            }
            drawPath(
                path = coilPath,
                color = Color(0xFFD99E64).copy(alpha = filamentAlpha),
                style = Stroke(width = 2.4f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Bulb neck & metallic screw base
            val neckBrush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFFB8ACB4),
                    Color(0xFFF9EEE9),
                    Color(0xFFFFFDF6),
                    Color(0xFFCBCFC2),
                    Color(0xFFAA9CA9)
                ),
                startX = sx(93f),
                endX = sx(187f)
            )
            drawRoundRect(
                brush = neckBrush,
                topLeft = Offset(sx(93f), sy(248f)),
                size = Size(94f * scale, 34f * scale),
                cornerRadius = CornerRadius(10f * scale, 10f * scale)
            )

            val metalBrush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFFA69BA6),
                    Color(0xFFE3DCE0),
                    Color(0xFFFFFAF6),
                    Color(0xFFCCC4C9),
                    Color(0xFF918791)
                ),
                startX = sx(96f),
                endX = sx(184f)
            )
            drawRoundRect(
                brush = metalBrush,
                topLeft = Offset(sx(96f), sy(267f)),
                size = Size(88f * scale, 22f * scale),
                cornerRadius = CornerRadius(7f * scale, 7f * scale)
            )
            drawRoundRect(
                brush = metalBrush,
                topLeft = Offset(sx(99f), sy(283f)),
                size = Size(82f * scale, 43f * scale),
                cornerRadius = CornerRadius(12f * scale, 12f * scale)
            )

            // Screw thread grooves
            val threadYs = listOf(291f, 300f, 309f, 318f)
            for (ty in threadYs) {
                val tp = Path().apply {
                    moveTo(sx(100f), sy(ty))
                    cubicTo(sx(124f), sy(ty + 3f), sx(156f), sy(ty + 3f), sx(180f), sy(ty))
                }
                drawPath(
                    path = tp,
                    color = Color(0xFF877D89).copy(alpha = 0.48f),
                    style = Stroke(width = 2f * scale, cap = StrokeCap.Round)
                )
            }

            // Bottom contact tip
            val tipPath = Path().apply {
                moveTo(sx(116f), sy(326f))
                lineTo(sx(164f), sy(326f))
                cubicTo(sx(162f), sy(335f), sx(155f), sy(341f), sx(146f), sy(341f))
                lineTo(sx(134f), sy(341f))
                cubicTo(sx(125f), sy(341f), sx(118f), sy(335f), sx(116f), sy(326f))
                close()
            }
            drawPath(tipPath, color = Color(0xFF9C929D))

            drawLine(
                color = Color.White.copy(alpha = 0.65f),
                start = Offset(sx(108f), sy(279f)),
                end = Offset(sx(171f), sy(279f)),
                strokeWidth = 2f * scale,
                cap = StrokeCap.Round
            )
        }

        // Decorative stage sparkles
        val sparkColor = Color.White.copy(alpha = if (isLit) 0.90f else 0.45f)
        Text(
            text = "✳",
            color = sparkColor,
            fontSize = 18.sp,
            modifier = Modifier.offset(x = (-78).dp, y = (-58).dp)
        )
        Text(
            text = "✦",
            color = sparkColor,
            fontSize = 13.sp,
            modifier = Modifier.offset(x = 76.dp, y = (-36).dp)
        )
        Text(
            text = "✧",
            color = sparkColor,
            fontSize = 20.sp,
            modifier = Modifier.offset(x = 68.dp, y = 52.dp)
        )
    }
}
