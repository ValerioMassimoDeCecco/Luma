package it.luma.tapo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.luma.tapo.R
import it.luma.tapo.model.DeviceCaps
import it.luma.tapo.model.DeviceProfile
import it.luma.tapo.model.LightMode
import it.luma.tapo.model.LumaColorMath
import it.luma.tapo.model.LumaPresets
import it.luma.tapo.model.ScenePreset
import it.luma.tapo.ui.theme.DmSansFontFamily
import it.luma.tapo.ui.theme.LocalLumaColors
import it.luma.tapo.ui.theme.LumaIcon
import it.luma.tapo.ui.theme.LumaIconType
import kotlin.math.roundToInt

@Composable
fun DeviceHeroCard(
    profile: DeviceProfile?,
    connected: Boolean,
    linkError: Boolean,
    isOn: Boolean,
    model: String,
    ip: String,
    brightness: Int,
    lightColor: Color,
    lightDescription: String,
    onPowerOrConnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLumaColors.current
    val isLit = connected && isOn

    val modelSubtitle = when {
        connected -> "${model.uppercase()} · $ip"
        profile != null -> "In attesa di connessione (${profile.ip})"
        else -> "Aggiungi o cerca una luce sulla rete"
    }

    val stateLabel = when {
        linkError -> "Non raggiungibile"
        connected -> if (isOn) "Accesa" else "Spenta"
        else -> "Non connessa"
    }

    val stateDotColor = when {
        linkError -> colors.statusError
        connected && isOn -> colors.statusOnline
        else -> colors.statusIdle
    }

    val captionText = when {
        profile == null -> "Aggiungi o cerca la tua prima luce"
        !connected -> "Tocca per collegare la lampadina"
        linkError -> "Controlla la connessione"
        isOn -> lightDescription
        else -> "Spenta · pronta quando vuoi"
    }

    val powerLabel = when {
        profile == null -> stringResource(R.string.power_add)
        !connected -> stringResource(R.string.power_connect)
        isOn -> stringResource(R.string.power_turn_off)
        else -> stringResource(R.string.power_turn_on)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(colors.glassStart, colors.glassEnd)))
            .border(1.dp, colors.glassBorder, RoundedCornerShape(26.dp))
            .padding(22.dp)
            .testTag("device_hero_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Card top row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(43.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(colors.iconBoxBg)
                            .border(1.dp, colors.iconBoxBorder, RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        LumaIcon(type = LumaIconType.BULB, color = colors.iconTint, size = 20.dp)
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.card_device_eyebrow),
                            fontFamily = DmSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.5.sp,
                            letterSpacing = 1.4.sp,
                            color = colors.eyebrow
                        )
                        Text(
                            text = profile?.name ?: "La tua lampadina",
                            fontFamily = DmSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = modelSubtitle,
                            fontFamily = DmSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.5.sp,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // State Pill
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.pillBg)
                        .border(1.dp, colors.pillBorder, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(stateDotColor)
                    )
                    Text(
                        text = stateLabel,
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = colors.pillText
                    )
                }
            }

            // Lamp stage artwork
            LampStageIllustration(
                lightColor = lightColor,
                brightnessPercent = brightness,
                isLit = isLit
            )

            // Bottom readout + Power button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.card_brightness_eyebrow),
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.5.sp,
                        letterSpacing = 1.4.sp,
                        color = colors.eyebrow
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (isLit) brightness.toString() else "—",
                            fontFamily = DmSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 48.sp,
                            lineHeight = 46.sp,
                            letterSpacing = (-2.5).sp,
                            color = colors.ink,
                            modifier = Modifier.testTag("big_brightness_value")
                        )
                        if (isLit) {
                            Text(
                                text = "%",
                                fontFamily = DmSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 22.sp,
                                color = colors.plumAccent,
                                modifier = Modifier.padding(start = 2.dp, bottom = 5.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDBB19A))
                        )
                        Text(
                            text = captionText,
                            fontFamily = DmSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.5.sp,
                            color = colors.muted
                        )
                    }
                }

                // Power button block
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(
                                if (isLit) {
                                    Brush.linearGradient(listOf(colors.powerBtnStart, colors.powerBtnEnd))
                                } else {
                                    Brush.linearGradient(listOf(colors.powerBtnOffBg, colors.powerBtnOffBg))
                                }
                            )
                            .border(1.5.dp, colors.glassBorder, CircleShape)
                            .clickable(onClick = onPowerOrConnectClick)
                            .semantics { contentDescription = powerLabel }
                            .testTag("power_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        LumaIcon(
                            type = LumaIconType.POWER,
                            color = colors.powerBtnIcon,
                            size = 23.dp,
                            strokeWidthDp = 2.dp
                        )
                    }
                    Text(
                        text = powerLabel,
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = colors.muted
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlsPanelCard(
    profile: DeviceProfile?,
    connected: Boolean,
    isOn: Boolean,
    caps: DeviceCaps,
    brightness: Int,
    ct: Int,
    minCt: Int,
    maxCt: Int,
    hue: Int,
    sat: Int,
    mode: LightMode,
    editMode: LightMode,
    onBrightnessChange: (Int, Boolean) -> Unit,
    onSelectEditMode: (LightMode) -> Unit,
    onWhiteTempChange: (Int, Boolean) -> Unit,
    onColorChange: (Int, Int, Boolean) -> Unit,
    onOpenCustomColorPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLumaColors.current
    val ctAvailable = connected && caps.ct
    val colorAvailable = connected && caps.color

    val panelHint = when {
        !connected -> if (profile != null) "Collega una lampadina per iniziare." else "Aggiungi o cerca una lampadina sulla rete per iniziare."
        !isOn -> "Regolando la luce, la lampadina si accende."
        editMode != mode -> "Scegli una tonalità per applicarla."
        else -> "Regola la luce come preferisci."
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(colors.glassStart, colors.glassEnd)))
            .border(1.dp, colors.glassBorder, RoundedCornerShape(26.dp))
            .padding(22.dp)
            .testTag("controls_panel_card")
    ) {
        // Heading
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.controls_eyebrow),
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp,
                    letterSpacing = 1.4.sp,
                    color = colors.eyebrow
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(R.string.controls_title))
                        withStyle(SpanStyle(color = colors.titleDot)) {
                            append(".")
                        }
                    },
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.7).sp,
                    color = colors.ink
                )
                Text(
                    text = stringResource(R.string.controls_subtitle),
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp,
                    color = colors.muted
                )
            }
            Box(
                modifier = Modifier
                    .size(39.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.iconBoxBg)
                    .border(1.dp, colors.iconBoxBorder, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                LumaIcon(type = LumaIconType.SLIDERS, color = colors.iconTint, size = 18.dp)
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Brightness slider block
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = stringResource(R.string.label_brightness),
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = colors.ink
            )
            Text(
                text = buildAnnotatedString {
                    append(if (connected) brightness.toString() else "—")
                    withStyle(SpanStyle(fontSize = 11.sp, color = colors.muted)) {
                        append(" %")
                    }
                },
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = colors.plumAccent
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LumaIcon(type = LumaIconType.SUN, color = colors.muted, size = 14.dp)
            val fillFraction = ((brightness - 1).coerceIn(0, 99)) / 99f
            LumaGradientSlider(
                valueFraction = fillFraction,
                enabled = connected && caps.brightness,
                trackBrush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFE4B5B1), Color(0xFFBF9BC1))
                ),
                isPartialFill = true,
                onFractionChanged = { frac, finished ->
                    val nextBri = (1 + frac * 99f).roundToInt().coerceIn(1, 100)
                    onBrightnessChange(nextBri, finished)
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("brightness_slider")
            )
            LumaIcon(type = LumaIconType.SUN, color = colors.muted, size = 20.dp)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.caption_dim),
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                color = colors.muted
            )
            Text(
                text = stringResource(R.string.caption_bright),
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                color = colors.muted
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = colors.divider, thickness = 1.dp)
        Spacer(modifier = Modifier.height(15.dp))

        // Mode heading
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_light_color),
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = colors.ink
            )
            Text(
                text = stringResource(R.string.label_customize),
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                letterSpacing = 1.2.sp,
                color = colors.eyebrow
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Show mode switch unless connected to a single-capability device
        val showModeTabs = !connected || (caps.ct && caps.color)
        if (showModeTabs) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(colors.modeSwitchBg)
                    .border(1.dp, colors.pillBorder, RoundedCornerShape(13.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val whiteSelected = editMode == LightMode.WHITE
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .alpha(if (ctAvailable) 1f else 0.42f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (whiteSelected) colors.modeTabActiveBg else Color.Transparent)
                        .clickable(enabled = ctAvailable) { onSelectEditMode(LightMode.WHITE) }
                        .testTag("white_mode_tab"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    LumaIcon(
                        type = LumaIconType.SUN,
                        color = if (whiteSelected) colors.modeTabActiveText else colors.modeTabInactiveText,
                        size = 14.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.tab_white),
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (whiteSelected) colors.modeTabActiveText else colors.modeTabInactiveText
                    )
                }

                val colorSelected = editMode == LightMode.COLOR
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .alpha(if (colorAvailable) 1f else 0.42f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (colorSelected) colors.modeTabActiveBg else Color.Transparent)
                        .clickable(enabled = colorAvailable) { onSelectEditMode(LightMode.COLOR) }
                        .testTag("color_mode_tab"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        Color(0xFFF2929C),
                                        Color(0xFFF3C685),
                                        Color(0xFFEADB96),
                                        Color(0xFF9BCDAD),
                                        Color(0xFF91B8DF),
                                        Color(0xFFC49BCF),
                                        Color(0xFFF2929C)
                                    )
                                )
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.tab_color),
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (colorSelected) colors.modeTabActiveText else colors.modeTabInactiveText
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // White Pane vs Color Pane vs Unsupported message
        val showWhitePane = !connected || (caps.ct && editMode == LightMode.WHITE)
        val showColorPane = connected && caps.color && editMode == LightMode.COLOR
        val showNoColorSupport = connected && !caps.ct && !caps.color

        if (showWhitePane) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = stringResource(R.string.label_temperature),
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = colors.ink
                )
                Text(
                    text = buildAnnotatedString {
                        append(if (connected) ct.toString() else "—")
                        if (connected) {
                            withStyle(SpanStyle(fontSize = 11.sp, color = colors.muted)) {
                                append(" K")
                            }
                        }
                    },
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = colors.plumAccent
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val tempSpan = (maxCt - minCt).coerceAtLeast(1)
            val tempFrac = ((ct.coerceIn(minCt, maxCt) - minCt).toFloat() / tempSpan).coerceIn(0f, 1f)
            LumaGradientSlider(
                valueFraction = tempFrac,
                enabled = ctAvailable,
                trackBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFEBAE7A),
                        Color(0xFFF6D4B2),
                        Color(0xFFF7EFED),
                        Color(0xFFC3D5EC)
                    )
                ),
                isPartialFill = false,
                onFractionChanged = { frac, finished ->
                    val rawK = minCt + frac * tempSpan
                    val stepped = ((rawK / 50f).roundToInt() * 50).coerceIn(minCt, maxCt)
                    onWhiteTempChange(stepped, finished)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("temperature_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.caption_warm),
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    color = colors.muted
                )
                Text(
                    text = stringResource(R.string.caption_cool),
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    color = colors.muted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick white presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LumaPresets.WHITE_PRESETS.forEach { preset ->
                    val targetK = preset.kelvin.coerceIn(minCt, maxCt)
                    val isPresetActive = isOn && mode == LightMode.WHITE && ct == targetK
                    Row(
                        modifier = Modifier
                            .height(35.dp)
                            .alpha(if (ctAvailable) 1f else 0.42f)
                            .clip(CircleShape)
                            .background(if (isPresetActive) colors.pillActiveBg else colors.pillBg)
                            .border(
                                1.dp,
                                if (isPresetActive) colors.pillActiveBorder else colors.pillBorder,
                                CircleShape
                            )
                            .clickable(enabled = ctAvailable) { onWhiteTempChange(targetK, true) }
                            .padding(horizontal = 11.dp)
                            .testTag("white_preset_${preset.kelvin}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(preset.dotColor)
                                .border(1.dp, Color(0x228C787D), CircleShape)
                        )
                        Text(
                            text = preset.label,
                            fontFamily = DmSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = if (isPresetActive) colors.pillActiveText else colors.pillText
                        )
                    }
                }
            }
        }

        if (showColorPane) {
            // Hue slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = stringResource(R.string.label_hue),
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = colors.ink
                )
                Text(
                    text = buildAnnotatedString {
                        append(hue.toString())
                        withStyle(SpanStyle(fontSize = 11.sp, color = colors.muted)) {
                            append("°")
                        }
                    },
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = colors.plumAccent
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LumaGradientSlider(
                valueFraction = (hue.coerceIn(0, 360) / 360f),
                enabled = colorAvailable,
                trackBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFEE8F91),
                        Color(0xFFEDBD73),
                        Color(0xFFE7DD8B),
                        Color(0xFF94CEAC),
                        Color(0xFF87CDDD),
                        Color(0xFF8EA9DD),
                        Color(0xFFC49BDD),
                        Color(0xFFEE8F91)
                    )
                ),
                isPartialFill = false,
                onFractionChanged = { frac, finished ->
                    val nextHue = (frac * 360f).roundToInt().coerceIn(0, 360)
                    onColorChange(nextHue, sat, finished)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hue_slider")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Saturation slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = stringResource(R.string.label_saturation),
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = colors.ink
                )
                Text(
                    text = buildAnnotatedString {
                        append(sat.toString())
                        withStyle(SpanStyle(fontSize = 11.sp, color = colors.muted)) {
                            append(" %")
                        }
                    },
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = colors.plumAccent
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val pureHueColor = LumaColorMath.rgbToColor(LumaColorMath.hsvToRgb(hue, 100))
            LumaGradientSlider(
                valueFraction = ((sat.coerceIn(1, 100) - 1) / 99f),
                enabled = colorAvailable,
                trackBrush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFF9F7F6), pureHueColor)
                ),
                isPartialFill = false,
                onFractionChanged = { frac, finished ->
                    val nextSat = (1 + frac * 99f).roundToInt().coerceIn(1, 100)
                    onColorChange(hue, nextSat, finished)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("saturation_slider")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick color swatches + custom picker
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LumaPresets.QUICK_SWATCHES.forEach { swatch ->
                    val isActive = isOn && mode == LightMode.COLOR && hue == swatch.hue && sat == swatch.sat
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .alpha(if (colorAvailable) 1f else 0.42f)
                            .clip(CircleShape)
                            .background(swatch.displayColor)
                            .border(
                                width = if (isActive) 3.dp else 2.dp,
                                color = if (isActive) colors.plumAccent else Color.White,
                                shape = CircleShape
                            )
                            .clickable(enabled = colorAvailable) {
                                onColorChange(swatch.hue, swatch.sat, true)
                            }
                            .semantics { contentDescription = swatch.label }
                            .testTag("color_swatch_${swatch.hue}"),
                        contentAlignment = Alignment.Center
                    ) {}
                }

                // Custom color button (+)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .alpha(if (colorAvailable) 1f else 0.42f)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFF0969E),
                                    Color(0xFFF4CF87),
                                    Color(0xFF9BD1AD),
                                    Color(0xFF93BCE3),
                                    Color(0xFFC8A0D6),
                                    Color(0xFFF0969E)
                                )
                            )
                        )
                        .border(2.dp, Color.White, CircleShape)
                        .clickable(enabled = colorAvailable, onClick = onOpenCustomColorPicker)
                        .semantics { contentDescription = "Scegli un colore personalizzato" }
                        .testTag("custom_color_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }
        }

        if (showNoColorSupport) {
            Text(
                text = stringResource(R.string.no_color_support),
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = colors.muted,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(color = colors.divider, thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))

        // Panel footer hint
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = "✦",
                fontSize = 13.sp,
                color = colors.plumAccent
            )
            Text(
                text = panelHint,
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = colors.muted
            )
        }
    }
}

@Composable
fun LumaGradientSlider(
    valueFraction: Float,
    enabled: Boolean,
    trackBrush: Brush,
    isPartialFill: Boolean,
    onFractionChanged: (Float, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLumaColors.current
    val coerced = valueFraction.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(34.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onFractionChanged(frac, true)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var lastFrac = coerced
                detectHorizontalDragGestures(
                    onDragStart = { startOffset ->
                        lastFrac = (startOffset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onFractionChanged(lastFrac, false)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        lastFrac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onFractionChanged(lastFrac, false)
                    },
                    onDragEnd = {
                        onFractionChanged(lastFrac, true)
                    },
                    onDragCancel = {
                        onFractionChanged(lastFrac, true)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(34.dp)) {
            val trackHeight = 8.dp.toPx()
            val thumbRadius = 10.dp.toPx()
            val yCenter = size.height / 2f
            val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)

            if (isPartialFill) {
                // Background track
                drawRoundRect(
                    color = colors.plumAccent.copy(alpha = 0.20f),
                    topLeft = Offset(0f, yCenter - trackHeight / 2f),
                    size = Size(size.width, trackHeight),
                    cornerRadius = corner
                )
                // Active filled track
                val fillW = (size.width * coerced).coerceAtLeast(trackHeight)
                drawRoundRect(
                    brush = trackBrush,
                    topLeft = Offset(0f, yCenter - trackHeight / 2f),
                    size = Size(fillW, trackHeight),
                    cornerRadius = corner
                )
            } else {
                drawRoundRect(
                    brush = trackBrush,
                    topLeft = Offset(0f, yCenter - trackHeight / 2f),
                    size = Size(size.width, trackHeight),
                    cornerRadius = corner
                )
            }

            val thumbX = thumbRadius + coerced * (size.width - 2 * thumbRadius).coerceAtLeast(0f)
            // Outer halo
            drawCircle(
                color = Color.White.copy(alpha = 0.32f),
                radius = thumbRadius + 3.dp.toPx(),
                center = Offset(thumbX, yCenter)
            )
            // White thumb knob
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(thumbX, yCenter)
            )
            drawCircle(
                color = Color(0xFFB79FAE).copy(alpha = 0.55f),
                radius = thumbRadius,
                center = Offset(thumbX, yCenter),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
            )
        }
    }
}

@Composable
fun ScenesAndBackupSection(
    connected: Boolean,
    caps: DeviceCaps,
    activeSceneKey: String?,
    onApplyScene: (ScenePreset) -> Unit,
    onAddWidgetClick: () -> Unit,
    onImportBackupClick: () -> Unit,
    onExportBackupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLumaColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        // Section header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = stringResource(R.string.scenes_eyebrow),
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp,
                    letterSpacing = 1.4.sp,
                    color = colors.eyebrow
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(R.string.scenes_title))
                        withStyle(SpanStyle(color = colors.titleDot)) {
                            append(".")
                        }
                    },
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
                    letterSpacing = (-0.5).sp,
                    color = colors.ink
                )
            }
            Text(
                text = "${stringResource(R.string.scenes_inspire)} ↘",
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp,
                color = colors.muted
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2x2 grid of scenes
        val rows = LumaPresets.SCENES.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { rowScenes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowScenes.forEach { scene ->
                        val sceneSupported = connected && caps.brightness &&
                            (if (scene.mode == LightMode.WHITE) caps.ct else caps.color)
                        val isActive = connected && activeSceneKey == scene.key
                        SceneCard(
                            scene = scene,
                            enabled = sceneSupported,
                            isActive = isActive,
                            onClick = { onApplyScene(scene) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Home Screen Widget Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))
                .background(Brush.linearGradient(listOf(colors.glassStart, colors.glassEnd)))
                .border(1.dp, colors.glassBorder, RoundedCornerShape(17.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    text = "CONTROLLO RAPIDO SULLA HOME",
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp,
                    letterSpacing = 1.2.sp,
                    color = colors.eyebrow
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Controlla accensione, luminosità e scene dalla Home senza aprire l’app.",
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = colors.muted
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.horizontalGradient(listOf(colors.primaryBtnStart, colors.primaryBtnEnd)))
                    .clickable(onClick = onAddWidgetClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("pin_home_widget_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                LumaIcon(type = LumaIconType.PLUS, color = Color.White, size = 12.dp)
                Text(
                    text = stringResource(R.string.btn_pin_widget),
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Backup Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))
                .background(colors.pillBg)
                .border(1.dp, colors.pillBorder, RoundedCornerShape(17.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.backup_label),
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 8.5.sp,
                letterSpacing = 1.2.sp,
                color = colors.eyebrow,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.secondaryBtnBg)
                        .border(1.dp, colors.secondaryBtnBorder, RoundedCornerShape(10.dp))
                        .clickable(onClick = onImportBackupClick)
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                        .testTag("import_backup_button")
                ) {
                    Text(
                        text = stringResource(R.string.btn_import_house),
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        color = colors.secondaryBtnText
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.secondaryBtnBg)
                        .border(1.dp, colors.secondaryBtnBorder, RoundedCornerShape(10.dp))
                        .clickable(onClick = onExportBackupClick)
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                        .testTag("export_backup_button")
                ) {
                    Text(
                        text = stringResource(R.string.btn_export_backup),
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        color = colors.secondaryBtnText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${stringResource(R.string.footer_left)} ✳",
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                letterSpacing = 1.1.sp,
                color = colors.eyebrow
            )
            Text(
                text = stringResource(R.string.footer_right),
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                letterSpacing = 1.1.sp,
                color = colors.eyebrow
            )
        }
    }
}

@Composable
private fun SceneCard(
    scene: ScenePreset,
    enabled: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLumaColors.current

    val (orbGradient, orbIconColor, orbIcon) = when (scene.key) {
        "relax" -> Triple(
            listOf(Color(0xFFF8DFC9), Color(0xFFF2C5A9)),
            Color(0xFFAF806E),
            LumaIconType.MOON
        )
        "focus" -> Triple(
            listOf(Color(0xFFF7ECCC), Color(0xFFEBD8AB)),
            Color(0xFFAE9568),
            LumaIconType.SUN
        )
        "sunset" -> Triple(
            listOf(Color(0xFFF9D6CD), Color(0xFFEFB5A4)),
            Color(0xFFBD8584),
            LumaIconType.SPARK
        )
        else -> Triple(
            listOf(Color(0xFFE7DBF3), Color(0xFFCDB8DF)),
            Color(0xFF9072A5),
            LumaIconType.SPARK
        )
    }

    Row(
        modifier = modifier
            .height(74.dp)
            .alpha(if (enabled) 1f else 0.48f)
            .clip(RoundedCornerShape(17.dp))
            .background(Brush.linearGradient(listOf(colors.glassStart, colors.glassEnd)))
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) colors.plumAccent else colors.glassBorder,
                shape = RoundedCornerShape(17.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("scene_button_${scene.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(orbGradient))
                .border(1.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            LumaIcon(type = orbIcon, color = orbIconColor, size = 18.dp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scene.title,
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = scene.subtitle,
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(colors.pillBg),
            contentAlignment = Alignment.Center
        ) {
            LumaIcon(type = LumaIconType.ARROW, color = colors.muted, size = 12.dp)
        }
    }
}
