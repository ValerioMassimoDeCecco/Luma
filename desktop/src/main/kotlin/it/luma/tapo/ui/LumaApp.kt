package it.luma.tapo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.luma.tapo.model.ConnectionBadgeState
import it.luma.tapo.model.LumaPresets
import kotlin.math.roundToInt

@Composable
fun LumaApp(viewModel: LumaDesktopViewModel) {
    val state by viewModel.uiState.collectAsState(initial = viewModel.uiState.value)

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LumaRoomsRail(viewModel, state)
        LumaMainPanel(viewModel, state)
    }

    LumaToast(state)

    LumaDeviceDialog(viewModel, state)
    LumaRoomDialog(viewModel, state)
    LumaImportDialog(viewModel, state)
    LumaCustomColorDialog(viewModel, state)
}

@Composable
private fun LumaRoomsRail(viewModel: LumaDesktopViewModel, state: LumaUiState) {
    Surface(
        modifier = Modifier
            .width(272.dp)
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Luma",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "per Tapo",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.house.rooms.forEach { room ->
                    val selected = room.id == state.selectedRoomId
                    Card(
                        onClick = { viewModel.selectRoom(room.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Text(
                                room.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${state.house.devices.count { it.roomId == room.id }} lampadine",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = { viewModel.openRoomDialog(edit = false) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Nuova stanza")
            }
            Spacer(Modifier.height(12.dp))

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { viewModel.openImportPicker() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Importa backup")
                }
                TextButton(
                    onClick = { viewModel.openExportPicker() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Esporta backup")
                }
            }
            Spacer(Modifier.height(8.dp))
            IconButton(onClick = { viewModel.toggleTheme() }) {
                Icon(
                    if (state.isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = "Cambia tema"
                )
            }
        }
    }
}

@Composable
private fun LumaMainPanel(viewModel: LumaDesktopViewModel, state: LumaUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        LumaHeader(viewModel, state)
        Spacer(Modifier.height(18.dp))

        val device = state.currentDevice
        if (device == null || !state.connected) {
            LumaEmptyState(viewModel, state)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LumaDashboard(viewModel, state)
            }
        }
    }
}

@Composable
private fun LumaHeader(viewModel: LumaDesktopViewModel, state: LumaUiState) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.currentDevice?.name?.takeIf { it.isNotEmpty() } ?: "Nessuna lampadina",
                    style = MaterialTheme.typography.titleLarge
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val badgeColor = when (state.connectionBadgeState) {
                        ConnectionBadgeState.ONLINE -> MaterialTheme.colorScheme.primary
                        ConnectionBadgeState.BUSY -> MaterialTheme.colorScheme.tertiary
                        ConnectionBadgeState.ERROR -> MaterialTheme.colorScheme.error
                        ConnectionBadgeState.IDLE -> MaterialTheme.colorScheme.outline
                    }
                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(
                            state.connectionText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.connectionBadgeState == ConnectionBadgeState.IDLE)
                                MaterialTheme.colorScheme.onSurface else Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                    if (state.ip.isNotEmpty()) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${state.model.ifEmpty { "Tapo" }} · ${state.ip}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(
                onClick = { viewModel.refreshState(forceToast = true) },
                enabled = state.connected && !state.isRefreshing
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Aggiorna stato")
            }
            IconButton(onClick = { viewModel.openDeviceDialog(edit = state.currentDevice != null) }) {
                Icon(Icons.Filled.Add, contentDescription = "Aggiungi lampadina")
            }
            if (state.selectedDeviceId.isNotEmpty()) {
                IconButton(onClick = { viewModel.deleteCurrentDevice() }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Rimuovi lampadina")
                }
            }
        }
    }
}

@Composable
private fun LumaEmptyState(viewModel: LumaDesktopViewModel, state: LumaUiState) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Text(
                if (state.currentDevice == null) "Aggiungi la tua prima lampadina" else "Collegamento in corso…",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                "Scansiona la rete di casa o inserisci manualmente IP, email e modello della lampadina Tapo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { viewModel.openDiscoveryAndScan() }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Aggiungi lampadina")
            }
        }
    }
}

@Composable
private fun LumaDashboard(viewModel: LumaDesktopViewModel, state: LumaUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LumaBulbHero(state, onPowerClick = { viewModel.onPowerOrConnectClicked() })
            Column(Modifier.weight(1f)) {
                Text(
                    "${state.currentDevice?.name ?: "Lampadina"}",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    if (state.isOn) "Accesa" else "Spenta",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    state.lightDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Sessione: ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (state.session.isEmpty()) "—" else "attiva",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Brightness
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Luminosità", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${state.bri}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = state.bri.toFloat(),
                    onValueChange = { viewModel.onBrightnessChanged(it.roundToInt(), false) },
                    onValueChangeFinished = { viewModel.onBrightnessChanged(state.bri, true) },
                    valueRange = 1f..100f
                )
            }
        }

        // Edit mode segmented control (white / color)
        if (state.caps.ct || state.caps.color) {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.caps.ct) {
                        LumaModeButton(
                            selected = state.editMode == it.luma.tapo.model.LightMode.WHITE,
                            label = "Bianco",
                            onClick = { viewModel.onSelectEditMode(it.luma.tapo.model.LightMode.WHITE) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (state.caps.color) {
                        LumaModeButton(
                            selected = state.editMode == it.luma.tapo.model.LightMode.COLOR,
                            label = "Colore",
                            onClick = { viewModel.onSelectEditMode(it.luma.tapo.model.LightMode.COLOR) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // White temperature
        if (state.connected && state.editMode == it.luma.tapo.model.LightMode.WHITE && state.caps.ct) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Temperatura", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${state.ct} K", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = state.ct.toFloat(),
                        onValueChange = { viewModel.onWhiteTemperatureChanged(it.roundToInt(), false) },
                        onValueChangeFinished = { viewModel.onWhiteTemperatureChanged(state.ct, true) },
                        valueRange = state.minCt.toFloat()..state.maxCt.toFloat()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LumaPresets.WHITE_PRESETS.forEach { preset ->
                            FilterChip(
                                selected = state.ct == preset.kelvin.coerceIn(state.minCt, state.maxCt),
                                onClick = { viewModel.onWhiteTemperatureChanged(preset.kelvin, true) },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                }
            }
        }

        // Color controls
        if (state.connected && state.editMode == it.luma.tapo.model.LightMode.COLOR && state.caps.color) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Colore", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tonalità", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Text("$state.hue°", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(
                        value = state.hue.toFloat(),
                        onValueChange = { viewModel.onColorChanged(it.roundToInt(), state.sat, false) },
                        onValueChangeFinished = { viewModel.onColorChanged(state.hue, state.sat, true) },
                        valueRange = 0f..360f
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Saturazione", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Text("$state.sat%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(
                        value = state.sat.toFloat(),
                        onValueChange = { viewModel.onColorChanged(state.hue, it.roundToInt(), false) },
                        onValueChangeFinished = { viewModel.onColorChanged(state.hue, state.sat, true) },
                        valueRange = 1f..100f
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LumaPresets.QUICK_SWATCHES.forEach { swatch ->
                            FilterChip(
                                selected = state.hue == swatch.hue && state.sat == swatch.sat,
                                onClick = { viewModel.onColorChanged(swatch.hue, swatch.sat, true) },
                                label = { Text(swatch.label) }
                            )
                        }
                    }
                    OutlinedButton(onClick = { viewModel.openCustomColorDialog() }) {
                        Text("Colore personalizzato")
                    }
                }
            }
        }

        // Scenes
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Scene rapide", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LumaPresets.SCENES.forEach { scene ->
                        FilterChip(
                            selected = state.activeSceneKey == scene.key,
                            onClick = { viewModel.applyScene(scene) },
                            label = { Text(scene.title) }
                        )
                    }
                }
            }
        }

        // Device info
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Informazioni dispositivo", style = MaterialTheme.typography.titleMedium)
                Text("Modello: ${state.model.ifEmpty { "—" }}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("IP locale: ${state.ip.ifEmpty { "—" }}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Capacità: ${buildCapsText(state)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun buildCapsText(state: LumaUiState): String {
    val parts = mutableListOf<String>()
    if (state.caps.brightness) parts.add("luminosità")
    if (state.caps.ct) parts.add("temperatura")
    if (state.caps.color) parts.add("colore")
    return if (parts.isEmpty()) "—" else parts.joinToString(", ")
}

@Composable
private fun LumaModeButton(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(40.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize().padding()
        )
    }
}

@Composable
private fun LumaBulbHero(state: LumaUiState, onPowerClick: () -> Unit) {
    val glow = state.currentLightColor
    val on = state.isOn
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(210.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            glow.copy(alpha = if (on) 0.60f else 0.10f),
                            glow.copy(alpha = 0f)
                        )
                    )
                )
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(118.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                glow.copy(alpha = if (on) 1f else 0.22f),
                                glow.copy(alpha = if (on) 0.30f else 0.06f)
                            )
                        )
                    )
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = if (on) 0.95f else 0.40f),
                    modifier = Modifier.size(54.dp)
                )
            }
            IconButton(
                onClick = onPowerClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(44.dp)
            ) {
                Icon(
                    Icons.Filled.PowerSettingsNew,
                    contentDescription = "Accendi / Spegni",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (on) "Accesa" else "Spenta",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LumaToast(state: LumaUiState) {
    val message = state.toastMessage ?: return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            color = if (state.toastIsError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (state.toastIsError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 3.dp
        ) {
            Text(
                message,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}
