package it.luma.tapo.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.luma.tapo.R

val DmSansFontFamily = FontFamily(
    Font(R.font.dm_sans, FontWeight.Normal),
    Font(R.font.dm_sans, FontWeight.Medium),
    Font(R.font.dm_sans, FontWeight.SemiBold),
    Font(R.font.dm_sans, FontWeight.Bold)
)

val DmSerifFontFamily = FontFamily(
    Font(R.font.dm_serif_display, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.dm_serif_display, FontWeight.Normal, FontStyle.Italic)
)

@Immutable
data class LumaColors(
    val isDark: Boolean,
    val bgStart: Color,
    val bgMid: Color,
    val bgEnd: Color,
    val orbOne: Color,
    val orbTwo: Color,
    val orbThree: Color,
    val ink: Color,
    val muted: Color,
    val eyebrow: Color,
    val plumAccent: Color,
    val titleDot: Color,
    val divider: Color,
    val glassStart: Color,
    val glassEnd: Color,
    val glassBorder: Color,
    val pillBg: Color,
    val pillBorder: Color,
    val pillText: Color,
    val pillActiveBg: Color,
    val pillActiveBorder: Color,
    val pillActiveText: Color,
    val iconBoxBg: Color,
    val iconBoxBorder: Color,
    val iconTint: Color,
    val powerBtnStart: Color,
    val powerBtnEnd: Color,
    val powerBtnOffBg: Color,
    val powerBtnIcon: Color,
    val modeSwitchBg: Color,
    val modeTabActiveBg: Color,
    val modeTabActiveText: Color,
    val modeTabInactiveText: Color,
    val dialogStart: Color,
    val dialogEnd: Color,
    val dialogBorder: Color,
    val inputBg: Color,
    val inputBorder: Color,
    val inputFocusBorder: Color,
    val primaryBtnStart: Color,
    val primaryBtnEnd: Color,
    val secondaryBtnBg: Color,
    val secondaryBtnBorder: Color,
    val secondaryBtnText: Color,
    val errorBg: Color,
    val errorBorder: Color,
    val errorText: Color,
    val toastBg: Color,
    val toastBorder: Color,
    val toastText: Color,
    val statusOnline: Color,
    val statusDemo: Color,
    val statusBusy: Color,
    val statusError: Color,
    val statusIdle: Color
)

val LightLumaColors = LumaColors(
    isDark = false,
    bgStart = Color(0xFFEEEDF4),
    bgMid = Color(0xFFF7F2EF),
    bgEnd = Color(0xFFE9EDF6),
    orbOne = Color(0x7AF4C8D6),
    orbTwo = Color(0x9EFADAD0),
    orbThree = Color(0x7AC4CFF4),
    ink = Color(0xFF302B3A),
    muted = Color(0xFF7F7482),
    eyebrow = Color(0xFF9991A1),
    plumAccent = Color(0xFFA27EAC),
    titleDot = Color(0xFFB18ABD),
    divider = Color(0x24948096),
    glassStart = Color(0xC4FFFFFF),
    glassEnd = Color(0x85FFFFFF),
    glassBorder = Color(0xD8FFFFFF),
    pillBg = Color(0x80FFFFFF),
    pillBorder = Color(0xB8FFFFFF),
    pillText = Color(0xFF777083),
    pillActiveBg = Color(0xEBFFFFFF),
    pillActiveBorder = Color(0x9EBF9EC3),
    pillActiveText = Color(0xFF735A7B),
    iconBoxBg = Color(0x96FFFFFF),
    iconBoxBorder = Color(0xD8FFFFFF),
    iconTint = Color(0xFF9E83A5),
    powerBtnStart = Color(0xF5FFFFFF),
    powerBtnEnd = Color(0xBFF5ECE8),
    powerBtnOffBg = Color(0xB0FFFFFF),
    powerBtnIcon = Color(0xFF8E6F93),
    modeSwitchBg = Color(0x66DDD4DF),
    modeTabActiveBg = Color(0xDEFFFFFF),
    modeTabActiveText = Color(0xFF78667D),
    modeTabInactiveText = Color(0xFF9E93A2),
    dialogStart = Color(0xFAFFFFFF),
    dialogEnd = Color(0xF7F6F0F6),
    dialogBorder = Color(0xF2FFFFFF),
    inputBg = Color(0xD8FFFFFF),
    inputBorder = Color(0xFFE5DCE5),
    inputFocusBorder = Color(0xFFBDA1C2),
    primaryBtnStart = Color(0xFFA783B3),
    primaryBtnEnd = Color(0xFFBC95BA),
    secondaryBtnBg = Color(0xC8FFFFFF),
    secondaryBtnBorder = Color(0xFFE9E0EA),
    secondaryBtnText = Color(0xFF7A6B7D),
    errorBg = Color(0xFFFAEDEF),
    errorBorder = Color(0xFFF1D6DA),
    errorText = Color(0xFFA75D69),
    toastBg = Color(0xF5FFFCFF),
    toastBorder = Color(0xE0FFFFFF),
    toastText = Color(0xFF6D5B72),
    statusOnline = Color(0xFF76B895),
    statusDemo = Color(0xFFAE8BC5),
    statusBusy = Color(0xFFD6A87F),
    statusError = Color(0xFFDF8F8E),
    statusIdle = Color(0xFFB2AAB8)
)

val DarkLumaColors = LumaColors(
    isDark = true,
    bgStart = Color(0xFF181622),
    bgMid = Color(0xFF221C27),
    bgEnd = Color(0xFF181925),
    orbOne = Color(0x45874F70),
    orbTwo = Color(0x428B5154),
    orbThree = Color(0x45464D82),
    ink = Color(0xFFF0EAF3),
    muted = Color(0xFFB6A9B8),
    eyebrow = Color(0xFFA79AAE),
    plumAccent = Color(0xFFD4AFD5),
    titleDot = Color(0xFFD4AFD5),
    divider = Color(0x1FEED6EE),
    glassStart = Color(0xCC40384B),
    glassEnd = Color(0xBF292533),
    glassBorder = Color(0x29E9DAEC),
    pillBg = Color(0x75483F4F),
    pillBorder = Color(0x28F1E0F2),
    pillText = Color(0xFFD3C7D6),
    pillActiveBg = Color(0xCC7C6282),
    pillActiveBorder = Color(0x6BDBEBBF),
    pillActiveText = Color(0xFFF8ECF8),
    iconBoxBg = Color(0x785B4D60),
    iconBoxBorder = Color(0x26F5E5F6),
    iconTint = Color(0xFFD0AED1),
    powerBtnStart = Color(0xEB725B75),
    powerBtnEnd = Color(0xEB4C3E54),
    powerBtnOffBg = Color(0xCC514657),
    powerBtnIcon = Color(0xFFE5C6E4),
    modeSwitchBg = Color(0x6B17151E),
    modeTabActiveBg = Color(0xCC6D5971),
    modeTabActiveText = Color(0xFFF7EAF5),
    modeTabInactiveText = Color(0xFFA99AAC),
    dialogStart = Color(0xFF302A37),
    dialogEnd = Color(0xFF25212D),
    dialogBorder = Color(0x33F5E5F6),
    inputBg = Color(0x99141219),
    inputBorder = Color(0x2EE8D3EA),
    inputFocusBorder = Color(0xFFC19BC6),
    primaryBtnStart = Color(0xFFA783B3),
    primaryBtnEnd = Color(0xFFBC95BA),
    secondaryBtnBg = Color(0x78614F66),
    secondaryBtnBorder = Color(0x29EFDCF0),
    secondaryBtnText = Color(0xFFDFCDDD),
    errorBg = Color(0xFF55343D),
    errorBorder = Color(0xFF83525D),
    errorText = Color(0xFFF2C3CC),
    toastBg = Color(0xF8322A37),
    toastBorder = Color(0x33F5E5F6),
    toastText = Color(0xFFF3E2F2),
    statusOnline = Color(0xFF83B696),
    statusDemo = Color(0xFFB596C7),
    statusBusy = Color(0xFFD6A87F),
    statusError = Color(0xFFDF8F8E),
    statusIdle = Color(0xFFB2AAB8)
)

val LocalLumaColors = staticCompositionLocalOf { LightLumaColors }

val LumaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 42.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1.8).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.8).sp
    ),
    titleMedium = TextStyle(
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.4).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelSmall = TextStyle(
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.5.sp
    )
)

@Composable
fun LumaTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val lumaColors = if (darkTheme) DarkLumaColors else LightLumaColors
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = lumaColors.plumAccent,
            background = lumaColors.bgStart,
            surface = lumaColors.dialogStart,
            onPrimary = Color.White,
            onBackground = lumaColors.ink,
            onSurface = lumaColors.ink
        )
    } else {
        lightColorScheme(
            primary = lumaColors.plumAccent,
            background = lumaColors.bgStart,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = lumaColors.ink,
            onSurface = lumaColors.ink
        )
    }

    CompositionLocalProvider(LocalLumaColors provides lumaColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LumaTypography,
            content = content
        )
    }
}

enum class LumaIconType {
    SPARK, BULB, GEAR, REFRESH, POWER, SUN, MOON, ARROW, CLOSE, SEARCH, LOCK, SLIDERS, HOME, PLUS, EDIT, TRASH
}

@Composable
fun LumaIcon(
    type: LumaIconType,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    strokeWidthDp: Dp = 1.8.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val stroke = Stroke(
            width = strokeWidthDp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        when (type) {
            LumaIconType.SPARK -> drawSpark(s, color)
            LumaIconType.BULB -> drawBulb(s, color, stroke)
            LumaIconType.GEAR -> drawGear(s, color, stroke)
            LumaIconType.REFRESH -> drawRefresh(s, color, stroke)
            LumaIconType.POWER -> drawPower(s, color, stroke)
            LumaIconType.SUN -> drawSun(s, color, stroke)
            LumaIconType.MOON -> drawMoon(s, color, stroke)
            LumaIconType.ARROW -> drawArrow(s, color, stroke)
            LumaIconType.CLOSE -> drawClose(s, color, stroke)
            LumaIconType.SEARCH -> drawSearch(s, color, stroke)
            LumaIconType.LOCK -> drawLock(s, color, stroke)
            LumaIconType.SLIDERS -> drawSliders(s, color, stroke)
            LumaIconType.HOME -> drawHome(s, color, stroke)
            LumaIconType.PLUS -> drawPlus(s, color, stroke)
            LumaIconType.EDIT -> drawEdit(s, color, stroke)
            LumaIconType.TRASH -> drawTrash(s, color, stroke)
        }
    }
}

private fun DrawScope.drawSpark(s: Float, color: Color) {
    val p1 = Path().apply {
        moveTo(12f * s, 2.5f * s)
        cubicTo(12.7f * s, 7.3f * s, 14.7f * s, 9.3f * s, 19.5f * s, 10f * s)
        cubicTo(14.7f * s, 10.7f * s, 12.7f * s, 12.7f * s, 12f * s, 17.5f * s)
        cubicTo(11.3f * s, 12.7f * s, 9.3f * s, 10.7f * s, 4.5f * s, 10f * s)
        cubicTo(9.3f * s, 9.3f * s, 11.3f * s, 7.3f * s, 12f * s, 2.5f * s)
        close()
    }
    drawPath(p1, color = color)
    val p2 = Path().apply {
        moveTo(19f * s, 15.5f * s)
        cubicTo(19.3f * s, 17.4f * s, 20.1f * s, 18.2f * s, 22f * s, 18.5f * s)
        cubicTo(20.1f * s, 18.8f * s, 19.3f * s, 19.6f * s, 19f * s, 21.5f * s)
        cubicTo(18.7f * s, 19.6f * s, 17.9f * s, 18.8f * s, 16f * s, 18.5f * s)
        cubicTo(17.9f * s, 18.2f * s, 18.7f * s, 17.4f * s, 19f * s, 15.5f * s)
        close()
    }
    drawPath(p2, color = color)
}

private fun DrawScope.drawBulb(s: Float, color: Color, stroke: Stroke) {
    drawLine(color, Offset(9f * s, 18f * s), Offset(15f * s, 18f * s), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(9.5f * s, 21f * s), Offset(14.5f * s, 21f * s), stroke.width, StrokeCap.Round)
    val p = Path().apply {
        moveTo(8.2f * s, 15.7f * s)
        cubicTo(7.7f * s, 14.2f * s, 5f * s, 12.7f * s, 5f * s, 9f * s)
        cubicTo(5f * s, 5.13f * s, 8.13f * s, 2f * s, 12f * s, 2f * s)
        cubicTo(15.87f * s, 2f * s, 19f * s, 5.13f * s, 19f * s, 9f * s)
        cubicTo(19f * s, 12.7f * s, 16.3f * s, 14.2f * s, 15.8f * s, 15.7f * s)
        cubicTo(15.6f * s, 16.3f * s, 15.2f * s, 16.5f * s, 14.6f * s, 16.5f * s)
        lineTo(9.4f * s, 16.5f * s)
        cubicTo(8.8f * s, 16.5f * s, 8.4f * s, 16.3f * s, 8.2f * s, 15.7f * s)
        close()
    }
    drawPath(p, color = color, style = stroke)
}

private fun DrawScope.drawGear(s: Float, color: Color, stroke: Stroke) {
    drawCircle(color, radius = 3.2f * s, center = Offset(12f * s, 12f * s), style = stroke)
    drawCircle(color, radius = 7.5f * s, center = Offset(12f * s, 12f * s), style = Stroke(width = stroke.width * 0.85f))
    val dirs = listOf(
        0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f,
        -0.7f to -0.7f, 0.7f to -0.7f, -0.7f to 0.7f, 0.7f to 0.7f
    )
    for ((dx, dy) in dirs) {
        drawLine(
            color = color,
            start = Offset((12f + dx * 7.2f) * s, (12f + dy * 7.2f) * s),
            end = Offset((12f + dx * 9.5f) * s, (12f + dy * 9.5f) * s),
            strokeWidth = stroke.width * 1.15f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawRefresh(s: Float, color: Color, stroke: Stroke) {
    drawArc(
        color = color,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(5f * s, 5f * s),
        size = Size(14f * s, 14f * s),
        style = stroke
    )
    drawArc(
        color = color,
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(5f * s, 5f * s),
        size = Size(14f * s, 14f * s),
        style = stroke
    )
    val p1 = Path().apply {
        moveTo(19f * s, 6.5f * s)
        lineTo(19f * s, 11f * s)
        lineTo(14.5f * s, 11f * s)
    }
    drawPath(p1, color, style = stroke)
    val p2 = Path().apply {
        moveTo(5f * s, 17.5f * s)
        lineTo(5f * s, 13f * s)
        lineTo(9.5f * s, 13f * s)
    }
    drawPath(p2, color, style = stroke)
}

private fun DrawScope.drawPower(s: Float, color: Color, stroke: Stroke) {
    drawLine(color, Offset(12f * s, 3f * s), Offset(12f * s, 12f * s), stroke.width, StrokeCap.Round)
    drawArc(
        color = color,
        startAngle = -50f,
        sweepAngle = 280f,
        useCenter = false,
        topLeft = Offset(4f * s, 4.5f * s),
        size = Size(16f * s, 16f * s),
        style = stroke
    )
}

private fun DrawScope.drawSun(s: Float, color: Color, stroke: Stroke) {
    drawCircle(color, radius = 3.6f * s, center = Offset(12f * s, 12f * s), style = stroke)
    val rays = listOf(
        12f to 2f to (12f to 4.2f),
        12f to 19.8f to (12f to 22f),
        2f to 12f to (4.2f to 12f),
        19.8f to 12f to (22f to 12f),
        4.9f to 4.9f to (6.4f to 6.4f),
        17.6f to 17.6f to (19.1f to 19.1f),
        4.9f to 19.1f to (6.4f to 17.6f),
        17.6f to 6.4f to (19.1f to 4.9f)
    )
    for ((start, end) in rays) {
        drawLine(
            color,
            Offset(start.first * s, start.second * s),
            Offset(end.first * s, end.second * s),
            stroke.width,
            StrokeCap.Round
        )
    }
}

private fun DrawScope.drawMoon(s: Float, color: Color, stroke: Stroke) {
    val p = Path().apply {
        moveTo(19.5f * s, 15.2f * s)
        cubicTo(15.2f * s, 16.5f * s, 9.5f * s, 13.2f * s, 8.8f * s, 4.5f * s)
        cubicTo(4.2f * s, 6.2f * s, 2.8f * s, 11.8f * s, 5.2f * s, 16.2f * s)
        cubicTo(7.8f * s, 20.5f * s, 14.5f * s, 21.2f * s, 19.5f * s, 15.2f * s)
        close()
    }
    drawPath(p, color = color, style = stroke)
}

private fun DrawScope.drawArrow(s: Float, color: Color, stroke: Stroke) {
    drawLine(color, Offset(4.5f * s, 12f * s), Offset(19.5f * s, 12f * s), stroke.width, StrokeCap.Round)
    val head = Path().apply {
        moveTo(14f * s, 6.5f * s)
        lineTo(19.5f * s, 12f * s)
        lineTo(14f * s, 17.5f * s)
    }
    drawPath(head, color, style = stroke)
}

private fun DrawScope.drawClose(s: Float, color: Color, stroke: Stroke) {
    drawLine(color, Offset(6f * s, 6f * s), Offset(18f * s, 18f * s), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(18f * s, 6f * s), Offset(6f * s, 18f * s), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawSearch(s: Float, color: Color, stroke: Stroke) {
    drawCircle(color, radius = 6f * s, center = Offset(10.5f * s, 10.5f * s), style = stroke)
    drawLine(color, Offset(15f * s, 15f * s), Offset(19.5f * s, 19.5f * s), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawLock(s: Float, color: Color, stroke: Stroke) {
    drawRoundRect(
        color = color,
        topLeft = Offset(5.5f * s, 10f * s),
        size = Size(13f * s, 10f * s),
        cornerRadius = CornerRadius(2f * s, 2f * s),
        style = stroke
    )
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(8.5f * s, 3.5f * s),
        size = Size(7f * s, 7f * s),
        style = stroke
    )
    drawLine(color, Offset(8.5f * s, 7f * s), Offset(8.5f * s, 10f * s), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(15.5f * s, 7f * s), Offset(15.5f * s, 10f * s), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(12f * s, 14f * s), Offset(12f * s, 16.2f * s), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawSliders(s: Float, color: Color, stroke: Stroke) {
    drawLine(color, Offset(5f * s, 4f * s), Offset(5f * s, 11f * s), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(5f * s, 15f * s), Offset(5f * s, 20f * s), stroke.width, StrokeCap.Round)
    drawCircle(color, radius = 2f * s, center = Offset(5f * s, 13f * s), style = stroke)

    drawLine(color, Offset(12f * s, 4f * s), Offset(12f * s, 7f * s), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(12f * s, 11f * s), Offset(12f * s, 20f * s), stroke.width, StrokeCap.Round)
    drawCircle(color, radius = 2f * s, center = Offset(12f * s, 9f * s), style = stroke)

    drawLine(color, Offset(19f * s, 4f * s), Offset(19f * s, 13f * s), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(19f * s, 17f * s), Offset(19f * s, 20f * s), stroke.width, StrokeCap.Round)
    drawCircle(color, radius = 2f * s, center = Offset(19f * s, 15f * s), style = stroke)
}

private fun DrawScope.drawHome(s: Float, color: Color, stroke: Stroke) {
    val house = Path().apply {
        moveTo(3.5f * s, 10f * s)
        lineTo(12f * s, 3f * s)
        lineTo(20.5f * s, 10f * s)
        lineTo(20.5f * s, 19.5f * s)
        lineTo(3.5f * s, 19.5f * s)
        close()
    }
    drawPath(house, color, style = stroke)
    val door = Path().apply {
        moveTo(9.5f * s, 19.5f * s)
        lineTo(9.5f * s, 14f * s)
        lineTo(14.5f * s, 14f * s)
        lineTo(14.5f * s, 19.5f * s)
    }
    drawPath(door, color, style = stroke)
}

private fun DrawScope.drawPlus(s: Float, color: Color, stroke: Stroke) {
    drawLine(color, Offset(12f * s, 5f * s), Offset(12f * s, 19f * s), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(5f * s, 12f * s), Offset(19f * s, 12f * s), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawEdit(s: Float, color: Color, stroke: Stroke) {
    val p = Path().apply {
        moveTo(4.5f * s, 19.5f * s)
        lineTo(8.3f * s, 18.7f * s)
        lineTo(19f * s, 8f * s)
        lineTo(16f * s, 5f * s)
        lineTo(5.3f * s, 15.7f * s)
        close()
    }
    drawPath(p, color, style = stroke)
    drawLine(color, Offset(14.2f * s, 6.8f * s), Offset(17.2f * s, 9.8f * s), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawTrash(s: Float, color: Color, stroke: Stroke) {
    drawLine(color, Offset(4f * s, 7f * s), Offset(20f * s, 7f * s), stroke.width, StrokeCap.Round)
    val top = Path().apply {
        moveTo(9f * s, 7f * s)
        lineTo(9f * s, 4.5f * s)
        lineTo(15f * s, 4.5f * s)
        lineTo(15f * s, 7f * s)
    }
    drawPath(top, color, style = stroke)
    val bin = Path().apply {
        moveTo(18.5f * s, 7f * s)
        lineTo(17.5f * s, 19.5f * s)
        lineTo(6.5f * s, 19.5f * s)
        lineTo(5.5f * s, 7f * s)
    }
    drawPath(bin, color, style = stroke)
    drawLine(color, Offset(10f * s, 11f * s), Offset(10f * s, 16.5f * s), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(14f * s, 11f * s), Offset(14f * s, 16.5f * s), stroke.width, StrokeCap.Round)
}
