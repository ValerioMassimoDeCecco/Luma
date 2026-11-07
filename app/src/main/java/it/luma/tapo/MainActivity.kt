package it.luma.tapo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.luma.tapo.ui.LumaUiState
import it.luma.tapo.ui.LumaViewModel
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
import it.luma.tapo.ui.theme.DmSansFontFamily
import it.luma.tapo.ui.theme.LocalLumaColors
import it.luma.tapo.ui.theme.LumaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val lumaViewModel: LumaViewModel = viewModel()
            val uiState by lumaViewModel.uiState.collectAsStateWithLifecycle()

            LumaTheme(darkTheme = uiState.isDark) {
                LumaAppScreen(
                    uiState = uiState,
                    viewModel = lumaViewModel
                )
            }
        }
    }
}

@Composable
fun LumaAppScreen(
    uiState: LumaUiState,
    viewModel: LumaViewModel
) {
    val colors = LocalLumaColors.current
    val context = LocalContext.current
    var pendingExportContent by remember { mutableStateOf<String?>(null) }

    // File picker for importing JSON backup
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    if (bytes.size > 1024 * 1024) {
                        viewModel.showToast("Il file è troppo grande.", isError = true)
                    } else {
                        viewModel.onImportJsonSelected(String(bytes, Charsets.UTF_8))
                    }
                }
            } catch (_: Exception) {
                viewModel.showToast("Impossibile leggere il file selezionato.", isError = true)
            }
        }
    }

    // File creator for exporting JSON backup
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportContent
        pendingExportContent = null
        if (uri != null && json != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                viewModel.showToast("Backup esportato senza password.")
            } catch (_: Exception) {
                viewModel.showToast("Errore durante il salvataggio del backup.", isError = true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(colors.bgStart, colors.bgMid, colors.bgEnd)
                )
            )
    ) {
        // Atmospheric background soft radial orbs matching style.css
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

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
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
                        }
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
                        onAddDevice = { viewModel.openDeviceDialog(edit = false) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Adaptive canonical layout: side-by-side on tablets/expanded screens, stacked on phones
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
                        onImportBackupClick = {
                            importLauncher.launch("application/json")
                        },
                        onExportBackupClick = {
                            viewModel.exportHouseBackup { json ->
                                pendingExportContent = json
                                exportLauncher.launch("luma-casa-backup.json")
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
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
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            val isErr = uiState.toastIsError
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.toastBg)
                    .border(
                        1.dp,
                        if (isErr) colors.errorBorder else colors.toastBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("toast_banner")
            ) {
                Text(
                    text = uiState.toastMessage ?: "",
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = if (isErr) colors.errorText else colors.toastText,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // Modal Dialogs
    if (uiState.showDeviceDialog) {
        DeviceSettingsDialog(
            editingDevice = if (uiState.deviceDialogEditMode) uiState.currentDevice else null,
            rooms = uiState.house.rooms,
            defaultRoomId = uiState.selectedRoomId,
            accounts = uiState.house.accounts,
            isSaving = uiState.isConnectingOrSaving,
            errorMessage = uiState.deviceFormError,
            subnetPrefix = uiState.subnetPrefix,
            onSubnetPrefixChange = viewModel::onSubnetPrefixChanged,
            isScanningNetwork = uiState.isScanningNetwork,
            scanProgress = uiState.scanProgress,
            hasCompletedScanOnce = uiState.hasCompletedScanOnce,
            discoveredDevices = uiState.discoveredDevices,
            selectedBatchIps = uiState.selectedBatchIps,
            onStartOrStopScan = viewModel::toggleNetworkDiscovery,
            onToggleBatchSelection = viewModel::toggleBatchIpSelection,
            onBatchAddSelected = viewModel::saveBatchDiscoveredDevices,
            onDismiss = viewModel::closeDeviceDialog,
            onSave = viewModel::saveDevice,
            onDelete = viewModel::deleteCurrentDevice
        )
    }

    if (uiState.showRoomDialog) {
        val editingRoom = if (uiState.roomDialogEditMode) uiState.currentRoom else null
        RoomDialog(
            editingRoom = editingRoom,
            roomDeviceCount = if (editingRoom != null) uiState.devicesInSelectedRoom.size else 0,
            totalRoomsCount = uiState.house.rooms.size,
            errorMessage = uiState.roomFormError,
            onDismiss = viewModel::closeRoomDialog,
            onSave = viewModel::saveRoom,
            onDelete = viewModel::deleteCurrentRoom
        )
    }

    if (uiState.pendingImportHouse != null) {
        ImportHouseConfirmDialog(
            errorMessage = uiState.importFormError,
            onDismiss = viewModel::dismissImportDialog,
            onConfirm = viewModel::confirmImportHouse
        )
    }

    if (uiState.showCustomColorDialog) {
        CustomColorPickerDialog(
            initialHue = uiState.hue,
            initialSat = uiState.sat,
            onDismiss = viewModel::closeCustomColorDialog,
            onApplyColor = { h, s -> viewModel.onColorChanged(h, s, true) }
        )
    }
}
