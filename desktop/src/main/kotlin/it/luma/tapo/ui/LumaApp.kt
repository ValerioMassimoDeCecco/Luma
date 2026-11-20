package it.luma.tapo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.luma.tapo.ui.components.ControlsPanelCard
import it.luma.tapo.ui.components.CustomColorPickerDialog
import it.luma.tapo.ui.components.DeviceHeroCard
import it.luma.tapo.ui.components.DeviceSettingsDialog
import it.luma.tapo.ui.components.ImportHouseConfirmDialog
import it.luma.tapo.ui.components.LumaHomeBar
import it.luma.tapo.ui.components.LumaIntroAndNetworkSection
import it.luma.tapo.ui.components.LumaTopBar
import it.luma.tapo.ui.components.RoomDialog
import it.luma.tapo.ui.components.ScenesAndBackupSection

@Composable
fun LumaApp(viewModel: LumaDesktopViewModel) {
    val uiState by viewModel.uiState.collectAsState(initial = viewModel.uiState.value)
    val colors = LocalLumaColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(colors.bgStart, colors.bgMid, colors.bgEnd)
                )
            )
    ) {
        // Atmospheric background soft radial orbs matching the mobile app
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.orbOne, colors.orbOne.copy(alpha = 0f)),
                    center = Offset(size.width * 0.12f, size.height * 0.25f),
                    radius = size.minDimension * 0.65f
                ),
                radius = size.minDimension * 0.65f,
                center = Offset(size.width * 0.12f, size.height * 0.25f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.orbTwo, colors.orbTwo.copy(alpha = 0f)),
                    center = Offset(size.width * 0.88f, size.height * 0.14f),
                    radius = size.minDimension * 0.70f
                ),
                radius = size.minDimension * 0.70f,
                center = Offset(size.width * 0.88f, size.height * 0.14f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.orbThree, colors.orbThree.copy(alpha = 0f)),
                    center = Offset(size.width * 0.75f, size.height * 0.92f),
                    radius = size.minDimension * 0.70f
                ),
                radius = size.minDimension * 0.70f,
                center = Offset(size.width * 0.75f, size.height * 0.92f)
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedScreen = maxWidth >= 760.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (isExpandedScreen) 32.dp else 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 1180.dp)
                ) {
                    LumaTopBar(
                        connectionState = uiState.connectionBadgeState,
                        connectionText = uiState.connectionText,
                        isDark = uiState.isDark,
                        canRefresh = uiState.connected && !uiState.isConnectingOrSaving && !uiState.isRefreshing,
                        onToggleTheme = viewModel::toggleTheme,
                        onRefresh = { viewModel.refreshState(forceToast = true) },
                        onOpenSettings = {
                            viewModel.openDeviceDialog(edit = uiState.currentDevice != null)
                        },
                        onDiscoverNetworkClick = viewModel::openDiscoveryAndScan
                    )

                    LumaIntroAndNetworkSection(
                        onDiscoverNetworkClick = viewModel::openDiscoveryAndScan
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LumaHomeBar(
                        rooms = uiState.house.rooms,
                        devicesInRoom = uiState.devicesInSelectedRoom,
                        allDevices = uiState.house.devices,
                        selectedRoomId = uiState.selectedRoomId,
                        selectedDeviceId = uiState.selectedDeviceId,
                        onSelectRoom = viewModel::selectRoom,
                        onEditRoom = { viewModel.openRoomDialog(edit = true) },
                        onAddRoom = { viewModel.openRoomDialog(edit = false) },
                        onSelectDevice = viewModel::selectDevice,
                        onEditDevice = { viewModel.openDeviceDialog(edit = true) },
                        onAddDevice = { viewModel.openDeviceDialog(edit = false) },
                        onDiscoverNetworkClick = viewModel::openDiscoveryAndScan
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Adaptive layout: side-by-side on wide screens (desktop), stacked if narrow
                    if (isExpandedScreen) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            DeviceHeroCard(
                                profile = uiState.currentDevice,
                                connected = uiState.connected,
                                linkError = uiState.linkError,
                                isOn = uiState.isOn,
                                model = uiState.model,
                                ip = uiState.ip,
                                brightness = uiState.bri,
                                lightColor = uiState.currentLightColor,
                                lightDescription = uiState.lightDescription,
                                onPowerOrConnectClick = viewModel::onPowerOrConnectClicked,
                                modifier = Modifier.weight(1.15f)
                            )

                            ControlsPanelCard(
                                profile = uiState.currentDevice,
                                connected = uiState.connected,
                                isOn = uiState.isOn,
                                caps = uiState.caps,
                                brightness = uiState.bri,
                                ct = uiState.ct,
                                minCt = uiState.minCt,
                                maxCt = uiState.maxCt,
                                hue = uiState.hue,
                                sat = uiState.sat,
                                mode = uiState.mode,
                                editMode = uiState.editMode,
                                onBrightnessChange = viewModel::onBrightnessChanged,
                                onSelectEditMode = viewModel::onSelectEditMode,
                                onWhiteTempChange = viewModel::onWhiteTemperatureChanged,
                                onColorChange = viewModel::onColorChanged,
                                onOpenCustomColorPicker = viewModel::openCustomColorDialog,
                                modifier = Modifier.weight(0.95f)
                            )
                        }
                    } else {
                        DeviceHeroCard(
                            profile = uiState.currentDevice,
                            connected = uiState.connected,
                            linkError = uiState.linkError,
                            isOn = uiState.isOn,
                            model = uiState.model,
                            ip = uiState.ip,
                            brightness = uiState.bri,
                            lightColor = uiState.currentLightColor,
                            lightDescription = uiState.lightDescription,
                            onPowerOrConnectClick = viewModel::onPowerOrConnectClicked
                        )

                        Spacer(modifier = Modifier.height(15.dp))

                        ControlsPanelCard(
                            profile = uiState.currentDevice,
                            connected = uiState.connected,
                            isOn = uiState.isOn,
                            caps = uiState.caps,
                            brightness = uiState.bri,
                            ct = uiState.ct,
                            minCt = uiState.minCt,
                            maxCt = uiState.maxCt,
                            hue = uiState.hue,
                            sat = uiState.sat,
                            mode = uiState.mode,
                            editMode = uiState.editMode,
                            onBrightnessChange = viewModel::onBrightnessChanged,
                            onSelectEditMode = viewModel::onSelectEditMode,
                            onWhiteTempChange = viewModel::onWhiteTemperatureChanged,
                            onColorChange = viewModel::onColorChanged,
                            onOpenCustomColorPicker = viewModel::openCustomColorDialog
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    ScenesAndBackupSection(
                        connected = uiState.connected,
                        caps = uiState.caps,
                        activeSceneKey = uiState.activeSceneKey,
                        onApplyScene = viewModel::applyScene,
                        onImportBackupClick = viewModel::openImportPicker,
                        onExportBackupClick = {
                            viewModel.openExportPicker()
                        },
                        onDiscoverNetworkClick = viewModel::openDiscoveryAndScan
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Footer
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FATTO PER FAR BRILLARE OGNI MOMENTO",
                            fontFamily = DmSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            letterSpacing = 1.3.sp,
                            color = colors.eyebrow
                        )
                        Text(
                            text = "LUMA PER TAPO · v1.02 · WINDOWS NATIVE",
                            fontFamily = DmSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            letterSpacing = 1.3.sp,
                            color = colors.eyebrow
                        )
                    }
                }
            }
        }

        // Floating Toast Banner
        AnimatedVisibility(
            visible = uiState.toastMessage != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            val isErr = uiState.toastIsError
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isErr) colors.errorBg else colors.toastBg)
                    .border(1.dp, if (isErr) colors.errorBorder else colors.toastBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(
                    text = uiState.toastMessage ?: "",
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = if (isErr) colors.errorText else colors.toastText,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // Dialogs
    if (uiState.showDeviceDialog) {
        DeviceSettingsDialog(
            editingDevice = if (uiState.deviceDialogEditMode) uiState.currentDevice else null,
            rooms = uiState.house.rooms,
            defaultRoomId = uiState.selectedRoomId,
            initialError = uiState.deviceFormError,
            isSaving = uiState.isConnectingOrSaving,
            initialAccount = uiState.house.accounts.find { it.email == uiState.currentDevice?.email },
            subnetPrefix = uiState.subnetPrefix,
            isScanningNetwork = uiState.isScanningNetwork,
            scanProgress = uiState.scanProgress,
            hasCompletedScanOnce = uiState.hasCompletedScanOnce,
            discoveredDevices = uiState.discoveredDevices,
            selectedBatchIps = uiState.selectedBatchIps,
            onSubnetPrefixChange = viewModel::onSubnetPrefixChanged,
            onStartOrStopScan = viewModel::toggleNetworkDiscovery,
            onToggleBatchSelection = viewModel::toggleBatchIpSelection,
            onSaveBatchDiscovered = { rId, email, pass, rem, vaultPass ->
                viewModel.saveBatchDiscoveredDevices(rId, email, pass, rem, vaultPass)
            },
            onDismiss = viewModel::closeDeviceDialog,
            onSave = { name, roomId, model, ip, email, pass, rem, vaultPass ->
                viewModel.saveDevice(name, roomId, model, ip, email, pass, rem, vaultPass)
            },
            onDelete = if (uiState.deviceDialogEditMode) {
                { viewModel.deleteCurrentDevice() }
            } else null
        )
    }

    if (uiState.showRoomDialog) {
        RoomDialog(
            editingRoom = if (uiState.roomDialogEditMode) uiState.currentRoom else null,
            initialError = uiState.roomFormError,
            onDismiss = viewModel::closeRoomDialog,
            onSave = viewModel::saveRoom,
            onDelete = if (uiState.roomDialogEditMode && uiState.house.rooms.size > 1) {
                { viewModel.deleteCurrentRoom() }
            } else null
        )
    }

    if (uiState.showCustomColorDialog) {
        CustomColorPickerDialog(
            initialHue = uiState.hue,
            initialSat = uiState.sat,
            onDismiss = viewModel::closeCustomColorDialog,
            onColorSelected = { h, s ->
                viewModel.onColorChanged(h, s, true)
                viewModel.closeCustomColorDialog()
            }
        )
    }

    val pendingImport = uiState.pendingImportHouse
    if (pendingImport != null) {
        ImportHouseConfirmDialog(
            roomsCount = pendingImport.rooms.size,
            devicesCount = pendingImport.devices.size,
            errorMessage = uiState.importFormError,
            onDismiss = viewModel::dismissImportDialog,
            onConfirm = viewModel::confirmImportHouse
        )
    }
}
