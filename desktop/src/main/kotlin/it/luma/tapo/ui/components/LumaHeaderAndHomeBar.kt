package it.luma.tapo.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.luma.tapo.model.ConnectionBadgeState
import it.luma.tapo.model.DeviceProfile
import it.luma.tapo.model.Room
import it.luma.tapo.ui.DmSansFontFamily
import it.luma.tapo.ui.DmSerifFontFamily
import it.luma.tapo.ui.LocalLumaColors
import it.luma.tapo.ui.LumaIcon
import it.luma.tapo.ui.LumaIconType
import it.luma.tapo.ui.LumaStrings

@Composable
fun LumaTopBar(
    connectionState: ConnectionBadgeState,
    connectionText: String,
    isDark: Boolean,
    canRefresh: Boolean,
    onToggleTheme: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onDiscoverNetworkClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLumaColors.current
    val pulseTransition = rememberInfiniteTransition(label = "statusPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    val dotColor = when (connectionState) {
        ConnectionBadgeState.ONLINE -> colors.statusOnline
        ConnectionBadgeState.BUSY -> colors.statusBusy
        ConnectionBadgeState.ERROR -> colors.statusError
        ConnectionBadgeState.IDLE -> colors.statusIdle
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand logo + text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.iconBoxBg)
                        .border(1.dp, colors.iconBoxBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    LumaIcon(type = LumaIconType.BULB, color = colors.plumAccent, size = 18.dp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        text = LumaStrings.brand_title,
                        fontFamily = DmSerifFontFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        color = colors.ink
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.pillBg,
                        border = BorderStroke(1.dp, colors.pillBorder)
                    ) {
                        Text(
                            text = LumaStrings.brand_subtitle,
                            fontFamily = DmSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.5.sp,
                            letterSpacing = 1.3.sp,
                            color = colors.pillText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Connection status + Action buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.pillBg,
                    border = BorderStroke(1.dp, colors.pillBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .alpha(if (connectionState == ConnectionBadgeState.BUSY) pulseAlpha else 1f)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Text(
                            text = connectionText,
                            fontFamily = DmSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            color = colors.pillText,
                            maxLines = 1
                        )
                    }
                }

                RoundActionButton(
                    icon = LumaIconType.SEARCH,
                    contentDesc = "Trova lampadine nella rete locale",
                    testTag = "topbar_search_button",
                    onClick = onDiscoverNetworkClick
                )

                RoundActionButton(
                    icon = if (isDark) LumaIconType.SUN else LumaIconType.MOON,
                    contentDesc = if (isDark) LumaStrings.cd_switch_to_light else LumaStrings.cd_switch_to_dark,
                    testTag = "theme_toggle_button",
                    onClick = onToggleTheme
                )

                RoundActionButton(
                    icon = LumaIconType.REFRESH,
                    contentDesc = LumaStrings.cd_refresh_state,
                    testTag = "refresh_button",
                    enabled = canRefresh,
                    onClick = onRefresh
                )

                RoundActionButton(
                    icon = LumaIconType.GEAR,
                    contentDesc = LumaStrings.cd_open_settings,
                    testTag = "settings_button",
                    onClick = onOpenSettings
                )
            }
        }
        HorizontalDivider(color = colors.divider, thickness = 1.dp)
    }
}

@Composable
private fun RoundActionButton(
    icon: LumaIconType,
    contentDesc: String,
    testTag: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = LocalLumaColors.current
    Box(
        modifier = Modifier
            .size(38.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .clip(CircleShape)
            .background(colors.pillBg)
            .border(1.dp, colors.pillBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = contentDesc },
        contentAlignment = Alignment.Center
    ) {
        LumaIcon(type = icon, color = colors.pillText, size = 16.dp)
    }
}

@Composable
fun LumaIntroAndNetworkSection(
    onDiscoverNetworkClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLumaColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(18.dp))

        // Eyebrow
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(1.dp)
                    .background(colors.plumAccent.copy(alpha = 0.65f))
            )
            Text(
                text = "${LumaStrings.intro_eyebrow} ✳",
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                color = colors.eyebrow
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main Headline: "La luce, a modo tuo."
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink
                    )
                ) {
                    append(LumaStrings.intro_title_prefix)
                }
                withStyle(
                    SpanStyle(
                        fontFamily = DmSerifFontFamily,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Normal,
                        color = colors.plumAccent
                    )
                ) {
                    append(LumaStrings.intro_title_italic)
                }
            },
            fontSize = 38.sp,
            lineHeight = 40.sp,
            letterSpacing = (-1.5).sp
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = LumaStrings.intro_subtitle,
            fontFamily = DmSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = colors.muted
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Local network strip with quick "Cerca luci sulla rete" button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(colors.pillBg)
                .border(1.dp, colors.pillBorder, RoundedCornerShape(15.dp))
                .padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(colors.statusOnline)
                )
                Text(
                    text = LumaStrings.mode_real_banner,
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.5.sp,
                    letterSpacing = 1.1.sp,
                    color = colors.pillText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Tasto per trovare la lampadina nella rete locale automaticamente
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.pillActiveBg)
                    .border(1.dp, colors.pillActiveBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onDiscoverNetworkClick)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LumaIcon(
                    type = LumaIconType.SEARCH,
                    color = colors.pillActiveText,
                    size = 13.dp
                )
                Text(
                    text = LumaStrings.btn_discover_network,
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = colors.pillActiveText
                )
            }
        }
    }
}

@Composable
fun LumaHomeBar(
    rooms: List<Room>,
    devicesInRoom: List<DeviceProfile>,
    allDevices: List<DeviceProfile>,
    selectedRoomId: String,
    selectedDeviceId: String,
    onSelectRoom: (String) -> Unit,
    onEditRoom: () -> Unit,
    onAddRoom: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onEditDevice: () -> Unit,
    onAddDevice: () -> Unit,
    onDiscoverNetworkClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLumaColors.current
    val hasRoom = rooms.any { it.id == selectedRoomId }
    val hasDevice = devicesInRoom.any { it.id == selectedDeviceId }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(colors = listOf(colors.glassStart, colors.glassEnd))
            )
            .border(1.dp, colors.glassBorder, RoundedCornerShape(22.dp))
            .padding(horizontal = 15.dp, vertical = 14.dp)
    ) {
        // Top header row: LA TUA CASA + Edit Room + Nuova Stanza
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.iconBoxBg)
                        .border(1.dp, colors.iconBoxBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    LumaIcon(type = LumaIconType.HOME, color = colors.iconTint, size = 17.dp)
                }
                Column {
                    Text(
                        text = LumaStrings.home_eyebrow,
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        letterSpacing = 1.4.sp,
                        color = colors.eyebrow
                    )
                    Text(
                        text = LumaStrings.home_rooms_title,
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = colors.ink
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .alpha(if (hasRoom) 1f else 0.35f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.pillBg)
                        .border(1.dp, colors.pillBorder, RoundedCornerShape(10.dp))
                        .clickable(enabled = hasRoom, onClick = onEditRoom),
                    contentAlignment = Alignment.Center
                ) {
                    LumaIcon(type = LumaIconType.EDIT, color = colors.pillText, size = 15.dp)
                }

                Row(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.pillBg.copy(alpha = 0.45f))
                        .border(1.dp, colors.plumAccent.copy(alpha = 0.45f), RoundedCornerShape(11.dp))
                        .clickable(onClick = onAddRoom)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    LumaIcon(type = LumaIconType.PLUS, color = colors.plumAccent, size = 13.dp)
                    Text(
                        text = LumaStrings.btn_new_room,
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        color = colors.plumAccent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Scrollable Room Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            rooms.forEach { room ->
                val isSelected = room.id == selectedRoomId
                val roomCount = allDevices.count { it.roomId == room.id }
                Row(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (isSelected) colors.pillActiveBg else colors.pillBg)
                        .border(
                            1.dp,
                            if (isSelected) colors.pillActiveBorder else colors.pillBorder,
                            RoundedCornerShape(11.dp)
                        )
                        .clickable { onSelectRoom(room.id) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        text = room.name,
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = if (isSelected) colors.pillActiveText else colors.pillText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .size(19.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.plumAccent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = roomCount.toString(),
                            fontFamily = DmSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp,
                            color = if (isSelected) colors.pillActiveText else colors.pillText
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(11.dp))
        HorizontalDivider(color = colors.divider, thickness = 1.dp)
        Spacer(modifier = Modifier.height(10.dp))

        // Lights row: LE TUE LUCI + Device Pills + Edit + Add Light + Discover Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = LumaStrings.home_lights_label,
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                letterSpacing = 1.3.sp,
                color = colors.eyebrow
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (devicesInRoom.isEmpty()) {
                    Text(
                        text = LumaStrings.empty_room_lights,
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp,
                        color = colors.muted
                    )
                } else {
                    devicesInRoom.forEach { device ->
                        val isSelected = device.id == selectedDeviceId
                        Row(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (isSelected) colors.pillActiveBg else colors.pillBg)
                                .border(
                                    1.dp,
                                    if (isSelected) colors.pillActiveBorder else colors.pillBorder,
                                    RoundedCornerShape(11.dp)
                                )
                                .clickable { onSelectDevice(device.id) }
                                .padding(horizontal = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LumaIcon(
                                type = LumaIconType.BULB,
                                color = if (isSelected) colors.plumAccent else colors.pillText,
                                size = 14.dp
                            )
                            Text(
                                text = device.name,
                                fontFamily = DmSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = if (isSelected) colors.pillActiveText else colors.pillText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Quick search / discover button
            Row(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.pillBg.copy(alpha = 0.70f))
                    .border(1.dp, colors.pillBorder, RoundedCornerShape(11.dp))
                    .clickable(onClick = onDiscoverNetworkClick)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                LumaIcon(type = LumaIconType.SEARCH, color = colors.pillText, size = 13.dp)
                Text(
                    text = "Cerca",
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    color = colors.pillText
                )
            }

            // Edit device button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .alpha(if (hasDevice) 1f else 0.35f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.pillBg)
                    .border(1.dp, colors.pillBorder, RoundedCornerShape(10.dp))
                    .clickable(enabled = hasDevice, onClick = onEditDevice),
                contentAlignment = Alignment.Center
            ) {
                LumaIcon(type = LumaIconType.EDIT, color = colors.pillText, size = 15.dp)
            }

            // Add device button
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .alpha(if (hasRoom) 1f else 0.35f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.pillBg.copy(alpha = 0.45f))
                    .border(1.dp, colors.plumAccent.copy(alpha = 0.45f), RoundedCornerShape(11.dp))
                    .clickable(enabled = hasRoom, onClick = onAddDevice)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LumaIcon(type = LumaIconType.PLUS, color = colors.plumAccent, size = 14.dp)
                    Text(
                        text = "+",
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = colors.plumAccent
                    )
                }
            }
        }
    }
}
