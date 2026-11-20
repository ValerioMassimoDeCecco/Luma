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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.luma.tapo.model.DeviceCaps
import it.luma.tapo.model.DeviceProfile
import it.luma.tapo.model.LightMode
import it.luma.tapo.model.LumaColorMath
import it.luma.tapo.model.LumaPresets
import it.luma.tapo.model.ScenePreset
import it.luma.tapo.ui.DmSansFontFamily
import it.luma.tapo.ui.LocalLumaColors
import it.luma.tapo.ui.LumaIcon
import it.luma.tapo.ui.LumaIconType
import it.luma.tapo.ui.LumaStrings
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
        !connected -> "Clicca per collegare la lampadina"
        linkError -> "Controlla la connessione di rete"
        isOn -> lightDescription
        else -> "Spenta · pronta quando vuoi"
    }

    val powerLabel = when {
        profile == null -> LumaStrings.power_add
        !connected -> LumaStrings.power_connect
        isOn -> LumaStrings.power_turn_off
        else -> LumaStrings.power_turn_on
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(colors.glassStart, colors.glassEnd)))
            .border(1.dp, colors.glassBorder, RoundedCornerShape(26.dp))
            .padding(22.dp)
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
                            text = LumaStrings.card_device_eyebrow,
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
                        text = LumaStrings.card_brightness_eyebrow,
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
                            color = colors.ink
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
                            .semantics { contentDescription = powerLabel },
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
    ) {
        // Heading
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = LumaStrings.controls_eyebrow,
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp,
                    letterSpacing = 1.4.sp,
                    color = colors.eyebrow
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = LumaStrings.controls_title,
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
                    letterSpacing = (-0.5).sp,
                    color = colors.ink
                )
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.iconBoxBg)
                    .border(1.dp, colors.iconBoxBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                LumaIcon(type = LumaIconType.SLIDERS, color = colors.iconTint, size = 18.dp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Brightness row label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = LumaStrings.label_brightness,
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = colors.ink
            )
            Text(
                text = buildAnnotatedString {
                    append(brightness.toString())
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

        // Interactive Brightness Slider
        LumaGradientSlider(
            valueFraction = (brightness.coerceIn(1, 100) - 1) / 99f,
            enabled = connected && caps.brightness,
            trackBrush = Brush.horizontalGradient(listOf(colors.primaryBtnStart, colors.primaryBtnEnd)),
            isPartialFill = true,
            onFractionChanged = { frac, finished ->
                val nextBri = (1 + frac * 99f).roundToInt().coerceIn(1, 100)
                onBrightnessChange(nextBri, finished)
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Quick -20% / +20% buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = LumaStrings.caption_dim,
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                color = colors.muted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickBrightnessPill("-20%", enabled = connected && caps.brightness) {
                    onBrightnessChange((brightness - 20).coerceAtLeast(1), true)
                }
                QuickBrightnessPill("+20%", enabled = connected && caps.brightness) {
                    onBrightnessChange((brightness + 20).coerceAtMost(100), true)
                }
            }
            Text(
                text = LumaStrings.caption_bright,
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                color = colors.muted
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(color = colors.divider, thickness = 1.dp)
        Spacer(modifier = Modifier.height(16.dp))

        // Mode switch tabs (Bianco / Colore)
        val showTabs = ctAvailable && colorAvailable
        val showColorPane = colorAvailable && (editMode == LightMode.COLOR || !ctAvailable)
        val showWhitePane = ctAvailable && (editMode == LightMode.WHITE || !colorAvailable)
        val showNoColorSupport = !ctAvailable && !colorAvailable

        if (showTabs) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.modeSwitchBg)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModeTabButton(
                    title = LumaStrings.tab_white,
                    active = editMode == LightMode.WHITE,
                    onClick = { onSelectEditMode(LightMode.WHITE) },
                    modifier = Modifier.weight(1f)
                )
                ModeTabButton(
                    title = LumaStrings.tab_color,
                    active = editMode == LightMode.COLOR,
                    onClick = { onSelectEditMode(LightMode.COLOR) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (showWhitePane) {
            // White temperature slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = LumaStrings.label_temperature,
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = colors.ink
                )
                Text(
                    text = buildAnnotatedString {
                        append(ct.toString())
                        withStyle(SpanStyle(fontSize = 11.sp, color = colors.muted)) {
                            append(" K")
                        }
                    },
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = colors.plumAccent
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val ctRange = (maxCt - minCt).coerceAtLeast(1)
            val ctFraction = ((ct - minCt).toFloat() / ctRange).coerceIn(0f, 1f)

            LumaGradientSlider(
                valueFraction = ctFraction,
                enabled = ctAvailable,
                trackBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFFF9E59),
                        Color(0xFFFFD49E),
                        Color(0xFFFFF7EB),
                        Color(0xFFE0F0FF),
                        Color(0xFFC0E0FF)
                    )
                ),
                isPartialFill = false,
                onFractionChanged = { frac, finished ->
                    val nextCt = (minCt + frac * ctRange).roundToInt().coerceIn(minCt, maxCt)
                    onWhiteTempChange(nextCt, finished)
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${minCt}K · ${LumaStrings.caption_warm}",
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    color = colors.muted
                )
                Text(
                    text = "${maxCt}K · ${LumaStrings.caption_cool}",
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    color = colors.muted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Kelvin presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                LumaPresets.WHITE_PRESETS.forEach { preset ->
                    val isPresetActive = isOn && mode == LightMode.WHITE && ct == preset.kelvin.coerceIn(minCt, maxCt)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isPresetActive) colors.pillActiveBg else colors.pillBg)
                            .border(
                                1.dp,
                                if (isPresetActive) colors.pillActiveBorder else colors.pillBorder,
                                RoundedCornerShape(9.dp)
                            )
                            .clickable(enabled = ctAvailable) {
                                onWhiteTempChange(preset.kelvin.coerceIn(minCt, maxCt), true)
                            }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
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
                    text = LumaStrings.label_hue,
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
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Saturation slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = LumaStrings.label_saturation,
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
                modifier = Modifier.fillMaxWidth()
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
                            .semantics { contentDescription = swatch.label },
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
                        .semantics { contentDescription = "Scegli un colore personalizzato" },
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
                text = LumaStrings.no_color_support,
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
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.plumAccent.copy(alpha = 0.7f))
            )
            Text(
                text = panelHint,
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp,
                color = colors.muted
            )
        }
    }
}

@Composable
private fun QuickBrightnessPill(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalLumaColors.current
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.pillBg)
            .border(1.dp, colors.pillBorder, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = DmSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = if (enabled) colors.pillText else colors.muted
        )
    }
}

@Composable
private fun ModeTabButton(
    title: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLumaColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) colors.modeTabActiveBg else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontFamily = DmSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (active) colors.modeTabActiveText else colors.modeTabInactiveText
        )
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
                var lastFrac = coerced
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onFractionChanged(frac, true)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var lastFrac = coerced
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        lastFrac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
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
    onImportBackupClick: () -> Unit,
    onExportBackupClick: () -> Unit,
    onDiscoverNetworkClick: () -> Unit,
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
                    text = LumaStrings.scenes_eyebrow,
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp,
                    letterSpacing = 1.4.sp,
                    color = colors.eyebrow
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildAnnotatedString {
                        append(LumaStrings.scenes_title)
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
                text = "${LumaStrings.scenes_inspire} ↘",
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

        // Backup & Local Network discovery strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.pillBg)
                .border(1.dp, colors.pillBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LumaIcon(type = LumaIconType.LOCK, color = colors.pillText, size = 15.dp)
                Text(
                    text = LumaStrings.backup_label,
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    color = colors.pillText
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.pillActiveBg)
                        .border(1.dp, colors.pillActiveBorder, RoundedCornerShape(9.dp))
                        .clickable(onClick = onImportBackupClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = LumaStrings.btn_import_house,
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = colors.pillActiveText
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.pillActiveBg)
                        .border(1.dp, colors.pillActiveBorder, RoundedCornerShape(9.dp))
                        .clickable(onClick = onExportBackupClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = LumaStrings.btn_export_backup,
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = colors.pillActiveText
                    )
                }
            }
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
    val (iconType, cardGradient, previewColor) = when (scene.key) {
        "relax" -> Triple(
            LumaIconType.SUN,
            listOf(Color(0xFFF9EAE1), Color(0xFFF0DFDA)),
            Color(0xFFE89A6A)
        )
        "focus" -> Triple(
            LumaIconType.SPARK,
            listOf(Color(0xFFE8EEF8), Color(0xFFDFE6F4)),
            Color(0xFF86A8D8)
        )
        "sunset" -> Triple(
            LumaIconType.SUN,
            listOf(Color(0xFFFDECE8), Color(0xFFF4D8D2)),
            Color(0xFFE57D65)
        )
        else -> Triple( // dream
            LumaIconType.MOON,
            listOf(Color(0xFFEFE8F6), Color(0xFFE5DCEF)),
            Color(0xFFAC87C7)
        )
    }

    val darkGradient = when (scene.key) {
        "relax" -> listOf(Color(0xFF382928), Color(0xFF2B2123))
        "focus" -> listOf(Color(0xFF262C3D), Color(0xFF1E2333))
        "sunset" -> listOf(Color(0xFF3B2625), Color(0xFF2C1E20))
        else -> listOf(Color(0xFF33273E), Color(0xFF271F32))
    }

    val bgGradient = if (colors.isDark) darkGradient else cardGradient

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .alpha(if (enabled) 1f else 0.42f)
            .background(Brush.linearGradient(bgGradient))
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) colors.plumAccent else colors.glassBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scene.title,
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = scene.subtitle,
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.5.sp,
                    color = colors.muted
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(previewColor.copy(alpha = 0.22f))
                    .border(1.dp, previewColor.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                LumaIcon(type = iconType, color = previewColor, size = 16.dp)
            }
        }
    }
}
