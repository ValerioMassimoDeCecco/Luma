package it.luma.tapo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.luma.tapo.model.LumaPresets
import kotlin.math.roundToInt

@Composable
private fun LumaDeviceDialog(viewModel: LumaDesktopViewModel, state: LumaUiState) {
    if (!state.showDeviceDialog) return

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("l530") }
    var rememberPassword by remember { mutableStateOf(true) }
    var vaultPassword by remember { mutableStateOf("") }
    var roomId by remember { mutableStateOf("") }

    LaunchedEffect(state.showDeviceDialog) {
        if (state.showDeviceDialog) {
            val dev = state.currentDevice
            name = dev?.name ?: ""
            email = dev?.email ?: ""
            password = ""
            ip = dev?.ip ?: ""
            model = dev?.model?.ifEmpty { "l530" } ?: "l530"
            rememberPassword = true
            vaultPassword = ""
            roomId = dev?.roomId ?: state.house.rooms.firstOrNull()?.id ?: ""
        }
    }

    AlertDialog(
        onDismissRequest = { viewModel.closeDeviceDialog() },
        title = { Text(if (state.deviceDialogEditMode) "Gestisci lampadina" else "Aggiungi lampadina") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome della lampadina") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Stanza", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.house.rooms.forEach { room ->
                            FilterChip(
                                selected = roomId == room.id,
                                onClick = { roomId = room.id },
                                label = { Text(room.name) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email account Tapo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password Tapo (opzionale se salvata)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("IPv4 locale, es. 192.168.1.20") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Modello (l530, l535, l930, …)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = rememberPassword, onCheckedChange = { rememberPassword = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Ricorda password cifrata localmente", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = vaultPassword,
                    onValueChange = { vaultPassword = it },
                    label = { Text("Frase segreta archivio (opzionale)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.deviceFormError.isNotEmpty()) {
                    Text(
                        state.deviceFormError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Network discovery panel
                if (state.isScanningNetwork || state.hasCompletedScanOnce) {
                    Surface(
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Ricerca sulla rete locale", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = state.subnetPrefix,
                                onValueChange = { viewModel.onSubnetPrefixChanged(it) },
                                label = { Text("Sottorete (es. 192.168.1)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (state.isScanningNetwork) {
                                LinearProgressIndicator(
                                    progress = { state.scanProgress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(8.dp)
                                )
                                Text(
                                    "Scansione in corso… trovate ${state.discoveredDevices.size} lampadina.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    "Trovate ${state.discoveredDevices.size} lampadina sulla rete.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            state.discoveredDevices.forEach { device ->
                                val selected = state.selectedBatchIps.contains(device.ip)
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = selected,
                                            onClick = { viewModel.toggleBatchIpSelection(device.ip) }
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(device.ip, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                "${device.protocol} · ${device.model.uppercase()}" +
                                                    (if (device.alreadyInHouse) " · già in casa" else ""),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.saveBatchDiscoveredDevices(
                                        roomId = roomId,
                                        email = email,
                                        password = password,
                                        rememberPassword = rememberPassword,
                                        vaultPassword = vaultPassword
                                    )
                                },
                                enabled = !state.isConnectingOrSaving &&
                                    state.selectedBatchIps.any { ip ->
                                        state.discoveredDevices.any { it.ip == ip && !it.alreadyInHouse }
                                    }
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.width(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Aggiungi selezionate")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.saveDevice(
                        name = name,
                        roomId = roomId,
                        email = email,
                        password = password,
                        ip = ip,
                        model = model,
                        rememberPassword = rememberPassword,
                        vaultPassword = vaultPassword
                    )
                },
                enabled = !state.isConnectingOrSaving
            ) { Text(if (state.deviceDialogEditMode) "Salva" else "Aggiungi") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.closeDeviceDialog() }) { Text("Annulla") }
        }
    )
}

@Composable
private fun LumaRoomDialog(viewModel: LumaDesktopViewModel, state: LumaUiState) {
    if (!state.showRoomDialog) return

    var roomName by remember { mutableStateOf("") }

    LaunchedEffect(state.showRoomDialog) {
        if (state.showRoomDialog) {
            roomName = state.currentRoom?.name ?: ""
        }
    }

    AlertDialog(
        onDismissRequest = { viewModel.closeRoomDialog() },
        title = { Text(if (state.roomDialogEditMode) "Modifica stanza" else "Nuova stanza") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Nome della stanza") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.roomFormError.isNotEmpty()) {
                    Text(
                        state.roomFormError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.saveRoom(roomName) }) { Text("Salva") }
        },
        dismissButton = {
            Column {
                if (state.roomDialogEditMode) {
                    TextButton(onClick = { viewModel.deleteCurrentRoom() }) { Text("Elimina") }
                }
                TextButton(onClick = { viewModel.closeRoomDialog() }) { Text("Annulla") }
            }
        }
    )
}

@Composable
private fun LumaImportDialog(viewModel: LumaDesktopViewModel, state: LumaUiState) {
    val house = state.pendingImportHouse ?: return

    AlertDialog(
        onDismissRequest = { viewModel.dismissImportDialog() },
        title = { Text("Importa backup di Luma") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Il backup contiene ${house.rooms.size} stanza e ${house.devices.size} lampadina.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "L'importazione sostituirà la configurazione corrente. Le password salvate localmente e le sessioni attive saranno ridefinite.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.importFormError.isNotEmpty()) {
                    Text(
                        state.importFormError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.confirmImportHouse() }) { Text("Importa") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissImportDialog() }) { Text("Annulla") }
        }
    )
}

@Composable
private fun LumaCustomColorDialog(viewModel: LumaDesktopViewModel, state: LumaUiState) {
    if (!state.showCustomColorDialog) return

    var hue by remember(state.showCustomColorDialog) { mutableStateOf(state.hue.toFloat()) }
    var sat by remember(state.showCustomColorDialog) { mutableStateOf(state.sat.toFloat()) }

    val previewColor = it.luma.tapo.model.LumaColorMath.rgbToColor(
        it.luma.tapo.model.LumaColorMath.hsvToRgb(hue.roundToInt(), sat.roundToInt())
    )

    AlertDialog(
        onDismissRequest = { viewModel.closeCustomColorDialog() },
        title = { Text("Colore personalizzato") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = previewColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$hue° · ${sat.roundToInt()}%",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f
                )
                Slider(
                    value = sat,
                    onValueChange = { sat = it },
                    valueRange = 1f..100f
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.onColorChanged(hue.roundToInt(), sat.roundToInt(), true)
                    viewModel.closeCustomColorDialog()
                }
            ) { Text("Applica") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.closeCustomColorDialog() }) { Text("Annulla") }
        }
    )
}
