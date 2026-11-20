package it.luma.tapo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import it.luma.tapo.model.DeviceProfile
import it.luma.tapo.model.DiscoveredTapoDevice
import it.luma.tapo.model.LumaColorMath
import it.luma.tapo.model.LumaPresets
import it.luma.tapo.model.PublicAccount
import it.luma.tapo.model.Room
import it.luma.tapo.ui.DmSansFontFamily
import it.luma.tapo.ui.LocalLumaColors
import it.luma.tapo.ui.LumaIcon
import it.luma.tapo.ui.LumaIconType
import kotlin.math.roundToInt

@Composable
fun DeviceSettingsDialog(
    editingDevice: DeviceProfile?,
    rooms: List<Room>,
    defaultRoomId: String,
    initialError: String = "",
    isSaving: Boolean = false,
    initialAccount: PublicAccount? = null,
    subnetPrefix: String = "192.168.1",
    isScanningNetwork: Boolean = false,
    scanProgress: Float = 0f,
    hasCompletedScanOnce: Boolean = false,
    discoveredDevices: List<DiscoveredTapoDevice> = emptyList(),
    selectedBatchIps: Set<String> = emptySet(),
    onSubnetPrefixChange: (String) -> Unit = {},
    onStartOrStopScan: () -> Unit = {},
    onToggleBatchSelection: (String) -> Unit = {},
    onSaveBatchDiscovered: (roomId: String, email: String, pass: String, remember: Boolean, vaultPass: String) -> Unit = { _, _, _, _, _ -> },
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        roomId: String,
        model: String,
        ip: String,
        email: String,
        pass: String,
        remember: Boolean,
        vaultPass: String
    ) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val colors = LocalLumaColors.current

    var name by remember(editingDevice) { mutableStateOf(editingDevice?.name ?: "") }
    var selectedRoomId by remember(editingDevice, defaultRoomId) {
        mutableStateOf(editingDevice?.roomId ?: defaultRoomId.ifBlank { rooms.firstOrNull()?.id ?: "" })
    }
    var model by remember(editingDevice) { mutableStateOf(editingDevice?.model ?: "l530") }
    var ip by remember(editingDevice) { mutableStateOf(editingDevice?.ip ?: "") }
    var email by remember(editingDevice, initialAccount) {
        mutableStateOf(editingDevice?.email ?: initialAccount?.email ?: "")
    }
    var password by remember(editingDevice) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var rememberCredentials by remember(editingDevice, initialAccount) {
        mutableStateOf(initialAccount?.rememberPassword ?: true)
    }
    var vaultPassword by remember { mutableStateOf("") }

    var roomMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(colors.dialogStart, colors.dialogEnd)))
                .border(1.dp, colors.dialogBorder, RoundedCornerShape(26.dp))
                .padding(22.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Top bar with icon and close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.iconBoxBg)
                        .border(1.dp, colors.iconBoxBorder, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    LumaIcon(type = LumaIconType.BULB, color = colors.iconTint, size = 20.dp)
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.pillBg)
                        .border(1.dp, colors.pillBorder, CircleShape)
                        .clickable(enabled = !isSaving, onClick = onDismiss)
                        .semantics { contentDescription = "Chiudi impostazioni" },
                    contentAlignment = Alignment.Center
                ) {
                    LumaIcon(type = LumaIconType.CLOSE, color = colors.pillText, size = 16.dp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (editingDevice != null) "LA TUA LAMPADINA" else "UNA LUCE IN PIÙ",
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.4.sp,
                color = colors.eyebrow
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = buildAnnotatedString {
                    append(if (editingDevice != null) "Gestisci la tua luce" else "Aggiungi una lampadina")
                    withStyle(SpanStyle(color = colors.titleDot)) {
                        append(".")
                    }
                },
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 25.sp,
                letterSpacing = (-0.8).sp,
                color = colors.ink
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (editingDevice != null) {
                    "Modifica il nome, la stanza o i dati di connessione."
                } else {
                    "Cerca automaticamente le lampadine sulla rete locale o inserisci l'IP manualmente."
                },
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = colors.muted
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Local Network Discovery Panel
            NetworkDiscoveryPanel(
                editingDevice = editingDevice,
                subnetPrefix = subnetPrefix,
                onSubnetPrefixChange = onSubnetPrefixChange,
                isScanningNetwork = isScanningNetwork,
                scanProgress = scanProgress,
                hasCompletedScanOnce = hasCompletedScanOnce,
                discoveredDevices = discoveredDevices,
                selectedBatchIps = selectedBatchIps,
                currentIp = ip,
                isSaving = isSaving,
                onStartOrStopScan = onStartOrStopScan,
                onSelectForForm = { found ->
                    ip = found.ip
                    model = found.model
                    if (name.isBlank()) {
                        name = found.suggestedName
                    }
                },
                onToggleBatchSelection = onToggleBatchSelection
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Device Name
            DialogFieldLabel("Nome della lampadina")
            LumaTextField(
                value = name,
                onValueChange = { if (it.length <= 60) name = it },
                placeholder = "Es. Lampada scrivania"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Room selector
            DialogFieldLabel("Stanza")
            Box(modifier = Modifier.fillMaxWidth()) {
                val roomName = rooms.find { it.id == selectedRoomId }?.name ?: "Seleziona stanza"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.inputBg)
                        .border(1.dp, colors.inputBorder, RoundedCornerShape(11.dp))
                        .clickable { roomMenuExpanded = true }
                        .padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = roomName,
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = colors.ink
                    )
                    Text(
                        text = "▾",
                        fontSize = 14.sp,
                        color = colors.muted
                    )
                }
                DropdownMenu(
                    expanded = roomMenuExpanded,
                    onDismissRequest = { roomMenuExpanded = false }
                ) {
                    rooms.forEach { r ->
                        DropdownMenuItem(
                            text = { Text(r.name, fontFamily = DmSansFontFamily) },
                            onClick = {
                                selectedRoomId = r.id
                                roomMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // IP Address & Model
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1.35f)) {
                    DialogFieldLabel("Indirizzo IP locale")
                    LumaTextField(
                        value = ip,
                        onValueChange = { ip = it.trim() },
                        placeholder = "192.168.1.100"
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    DialogFieldLabel("Modello")
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val currentModelLabel = model.uppercase()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(colors.inputBg)
                                .border(1.dp, colors.inputBorder, RoundedCornerShape(11.dp))
                                .clickable { modelMenuExpanded = true }
                                .padding(horizontal = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = currentModelLabel,
                                fontFamily = DmSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = colors.ink
                            )
                            Text(text = "▾", fontSize = 14.sp, color = colors.muted)
                        }
                        DropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false }
                        ) {
                            LumaPresets.SUPPORTED_MODELS.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.uppercase(), fontFamily = DmSansFontFamily) },
                                    onClick = {
                                        model = m
                                        modelMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tapo Credentials
            DialogFieldLabel("Email account TP-Link Tapo")
            LumaTextField(
                value = email,
                onValueChange = { email = it.trim() },
                placeholder = "nome@esempio.com"
            )

            Spacer(modifier = Modifier.height(12.dp))

            DialogFieldLabel(
                if (editingDevice != null) "Nuova password (lascia vuoto per non modificare)"
                else "Password account Tapo"
            )
            LumaTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "••••••••",
                isPassword = !showPassword,
                trailingIcon = {
                    Text(
                        text = if (showPassword) "Nascondi" else "Mostra",
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = colors.plumAccent,
                        modifier = Modifier
                            .clickable { showPassword = !showPassword }
                            .padding(end = 12.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Error display
            if (initialError.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.errorBg)
                        .border(1.dp, colors.errorBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = initialError,
                        fontFamily = DmSansFontFamily,
                        fontSize = 12.sp,
                        color = colors.errorText
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Save / Batch Add / Delete actions
            if (editingDevice == null && selectedBatchIps.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(colors.primaryBtnStart, colors.primaryBtnEnd)))
                        .clickable(enabled = !isSaving) {
                            onSaveBatchDiscovered(
                                selectedRoomId,
                                email,
                                password,
                                rememberCredentials,
                                vaultPassword
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Aggiungi ${selectedBatchIps.size} lampadine selezionate",
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(colors.primaryBtnStart, colors.primaryBtnEnd)))
                        .clickable(enabled = !isSaving) {
                            onSave(
                                name,
                                selectedRoomId,
                                model,
                                ip,
                                email,
                                password,
                                rememberCredentials,
                                vaultPassword
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isSaving) "Salvataggio…" else "Salva lampadina",
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }

            if (editingDevice != null && onDelete != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.secondaryBtnBg)
                        .border(1.dp, colors.secondaryBtnBorder, RoundedCornerShape(10.dp))
                        .clickable(enabled = !isSaving, onClick = onDelete),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    LumaIcon(type = LumaIconType.TRASH, color = colors.secondaryBtnText, size = 15.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Rimuovi questa lampadina",
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = colors.secondaryBtnText
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkDiscoveryPanel(
    editingDevice: DeviceProfile?,
    subnetPrefix: String,
    onSubnetPrefixChange: (String) -> Unit,
    isScanningNetwork: Boolean,
    scanProgress: Float,
    hasCompletedScanOnce: Boolean,
    discoveredDevices: List<DiscoveredTapoDevice>,
    selectedBatchIps: Set<String>,
    currentIp: String,
    isSaving: Boolean,
    onStartOrStopScan: () -> Unit,
    onSelectForForm: (DiscoveredTapoDevice) -> Unit,
    onToggleBatchSelection: (String) -> Unit
) {
    val colors = LocalLumaColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(colors.pillBg)
            .border(1.dp, colors.pillBorder, RoundedCornerShape(15.dp))
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SCOPRI SULLA RETE LOCALE",
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    color = colors.eyebrow
                )
                Text(
                    text = "Broadcast UDP + scansione sottorete KLAP/TPAP",
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = colors.muted
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.inputBg)
                    .border(1.dp, colors.inputBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rete:",
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.width(6.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = subnetPrefix,
                    onValueChange = { if (it.length <= 15) onSubnetPrefixChange(it) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = colors.ink
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = ".1-254",
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = colors.muted
                )
            }

            val scanBtnModifier = if (isScanningNetwork) {
                Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.secondaryBtnBg)
                    .border(1.dp, colors.secondaryBtnBorder, RoundedCornerShape(10.dp))
            } else {
                Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.horizontalGradient(listOf(colors.primaryBtnStart, colors.primaryBtnEnd)))
            }

            Row(
                modifier = scanBtnModifier
                    .clickable(enabled = !isSaving, onClick = onStartOrStopScan)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LumaIcon(
                    type = if (isScanningNetwork) LumaIconType.CLOSE else LumaIconType.SEARCH,
                    color = if (isScanningNetwork) colors.secondaryBtnText else Color.White,
                    size = 13.dp
                )
                Text(
                    text = if (isScanningNetwork) "Interrompi" else "Cerca luci",
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = if (isScanningNetwork) colors.secondaryBtnText else Color.White
                )
            }
        }

        if (isScanningNetwork) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Ricerca lampadine Tapo in corso…",
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.5.sp,
                        color = colors.muted
                    )
                    Text(
                        text = "${(scanProgress * 100).roundToInt()}%",
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        color = colors.plumAccent
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(colors.modeSwitchBg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(scanProgress.coerceIn(0.04f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(colors.primaryBtnStart, colors.primaryBtnEnd)))
                    )
                }
            }
        }

        if (discoveredDevices.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (editingDevice == null) {
                        "Clicca una luce per compilare il modulo, oppure seleziona le caselle per aggiungerle insieme:"
                    } else {
                        "Clicca una luce trovata per aggiornarne l'indirizzo IP:"
                    },
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.5.sp,
                    color = colors.muted
                )

                discoveredDevices.forEach { dev ->
                    val isSelectedIp = currentIp.trim() == dev.ip
                    val isCheckedForBatch = selectedBatchIps.contains(dev.ip)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (isSelectedIp) colors.pillActiveBg else colors.inputBg)
                            .border(
                                1.dp,
                                if (isSelectedIp || isCheckedForBatch) colors.plumAccent else colors.inputBorder,
                                RoundedCornerShape(11.dp)
                            )
                            .clickable { onSelectForForm(dev) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (editingDevice == null) {
                            Checkbox(
                                checked = isCheckedForBatch,
                                enabled = !dev.alreadyInHouse,
                                onCheckedChange = { onToggleBatchSelection(dev.ip) },
                                colors = CheckboxDefaults.colors(checkedColor = colors.plumAccent),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = dev.suggestedName,
                                    fontFamily = DmSansFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = colors.ink
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(colors.plumAccent.copy(alpha = 0.16f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = dev.protocol,
                                        fontFamily = DmSansFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = colors.ink
                                    )
                                }
                            }
                            Text(
                                text = buildString {
                                    append("IP ${dev.ip} · ${dev.model.uppercase()}")
                                    if (!dev.mac.isNullOrBlank()) {
                                        append(" · ${dev.mac}")
                                    }
                                },
                                fontFamily = DmSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.5.sp,
                                color = colors.muted
                            )
                        }

                        if (dev.alreadyInHouse) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(colors.pillBg)
                                    .border(1.dp, colors.pillBorder, CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Già in casa",
                                    fontFamily = DmSansFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 9.5.sp,
                                    color = colors.muted
                                )
                            }
                        } else {
                            Text(
                                text = if (isSelectedIp) "Scelta ✓" else "Usa IP",
                                fontFamily = DmSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = colors.plumAccent
                            )
                        }
                    }
                }
            }
        } else if (hasCompletedScanOnce && !isScanningNetwork) {
            Text(
                text = "Nessuna lampadina Tapo trovata su $subnetPrefix.1-254. Verifica che il PC sia connesso alla stessa rete Wi-Fi/LAN.",
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.5.sp,
                color = colors.muted
            )
        }
    }
}

@Composable
fun RoomDialog(
    editingRoom: Room?,
    initialError: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val colors = LocalLumaColors.current
    var name by remember(editingRoom) { mutableStateOf(editingRoom?.name ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(colors.dialogStart, colors.dialogEnd)))
                .border(1.dp, colors.dialogBorder, RoundedCornerShape(26.dp))
                .padding(22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.iconBoxBg)
                        .border(1.dp, colors.iconBoxBorder, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    LumaIcon(type = LumaIconType.HOME, color = colors.iconTint, size = 20.dp)
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.pillBg)
                        .border(1.dp, colors.pillBorder, CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    LumaIcon(type = LumaIconType.CLOSE, color = colors.pillText, size = 16.dp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (editingRoom != null) "LA TUA STANZA" else "NUOVO SPAZIO",
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.4.sp,
                color = colors.eyebrow
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (editingRoom != null) "Modifica stanza" else "Aggiungi una stanza",
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                color = colors.ink
            )

            Spacer(modifier = Modifier.height(14.dp))

            DialogFieldLabel("Nome della stanza")
            LumaTextField(
                value = name,
                onValueChange = { if (it.length <= 40) name = it },
                placeholder = "Es. Soggiorno, Studio, Camera"
            )

            if (initialError.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = initialError, fontFamily = DmSansFontFamily, fontSize = 12.sp, color = colors.errorText)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(colors.primaryBtnStart, colors.primaryBtnEnd)))
                    .clickable { onSave(name) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(text = "Salva", fontFamily = DmSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            }

            if (editingRoom != null && onDelete != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.secondaryBtnBg)
                        .border(1.dp, colors.secondaryBtnBorder, RoundedCornerShape(10.dp))
                        .clickable(onClick = onDelete),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    LumaIcon(type = LumaIconType.TRASH, color = colors.secondaryBtnText, size = 15.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Elimina stanza", fontFamily = DmSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.secondaryBtnText)
                }
            }
        }
    }
}

@Composable
fun CustomColorPickerDialog(
    initialHue: Int,
    initialSat: Int,
    onDismiss: () -> Unit,
    onColorSelected: (hue: Int, sat: Int) -> Unit
) {
    val colors = LocalLumaColors.current
    var hue by remember { mutableStateOf(initialHue.coerceIn(0, 360)) }
    var sat by remember { mutableStateOf(initialSat.coerceIn(1, 100)) }

    val previewRgb = LumaColorMath.hsvToRgb(hue, sat)
    val previewColor = LumaColorMath.rgbToColor(previewRgb)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(colors.dialogStart, colors.dialogEnd)))
                .border(1.dp, colors.dialogBorder, RoundedCornerShape(26.dp))
                .padding(22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "COLORE PERSONALIZZATO",
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.4.sp,
                    color = colors.eyebrow
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colors.pillBg)
                        .border(1.dp, colors.pillBorder, CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    LumaIcon(type = LumaIconType.CLOSE, color = colors.pillText, size = 15.dp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Color swatch preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(previewColor)
                    .border(2.dp, Color.White, RoundedCornerShape(14.dp))
            )

            Spacer(modifier = Modifier.height(16.dp))

            DialogFieldLabel("Tonalità ($hue°)")
            LumaGradientSlider(
                valueFraction = hue / 360f,
                enabled = true,
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
                onFractionChanged = { frac, _ -> hue = (frac * 360f).roundToInt().coerceIn(0, 360) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            DialogFieldLabel("Saturazione ($sat%)")
            val pureHue = LumaColorMath.rgbToColor(LumaColorMath.hsvToRgb(hue, 100))
            LumaGradientSlider(
                valueFraction = (sat - 1) / 99f,
                enabled = true,
                trackBrush = Brush.horizontalGradient(listOf(Color(0xFFF9F7F6), pureHue)),
                isPartialFill = false,
                onFractionChanged = { frac, _ -> sat = (1 + frac * 99f).roundToInt().coerceIn(1, 100) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(colors.primaryBtnStart, colors.primaryBtnEnd)))
                    .clickable { onColorSelected(hue, sat) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(text = "Applica colore", fontFamily = DmSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            }
        }
    }
}

@Composable
fun ImportHouseConfirmDialog(
    roomsCount: Int,
    devicesCount: Int,
    errorMessage: String = "",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = LocalLumaColors.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(colors.dialogStart, colors.dialogEnd)))
                .border(1.dp, colors.dialogBorder, RoundedCornerShape(26.dp))
                .padding(22.dp)
        ) {
            Text(
                text = "IMPORTA CONFIGURAZIONE",
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.4.sp,
                color = colors.eyebrow
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Sostituire la configurazione?",
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                color = colors.ink
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Il file contiene $roomsCount stanze e $devicesCount lampadine. I dati attuali della casa verranno sovrascritti.",
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = colors.muted
            )

            if (errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = errorMessage, fontFamily = DmSansFontFamily, fontSize = 12.sp, color = colors.errorText)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.secondaryBtnBg)
                        .border(1.dp, colors.secondaryBtnBorder, RoundedCornerShape(11.dp))
                        .clickable(onClick = onDismiss),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = "Annulla", fontFamily = DmSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = colors.secondaryBtnText)
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Brush.horizontalGradient(listOf(colors.primaryBtnStart, colors.primaryBtnEnd)))
                        .clickable(onClick = onConfirm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = "Importa", fontFamily = DmSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DialogFieldLabel(label: String) {
    val colors = LocalLumaColors.current
    Text(
        text = label,
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        color = colors.muted,
        modifier = Modifier.padding(bottom = 5.dp)
    )
}

@Composable
private fun LumaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val colors = LocalLumaColors.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                fontFamily = DmSansFontFamily,
                fontSize = 13.sp,
                color = colors.muted.copy(alpha = 0.6f)
            )
        },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = trailingIcon,
        singleLine = true,
        shape = RoundedCornerShape(11.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.inputBg,
            unfocusedContainerColor = colors.inputBg,
            focusedBorderColor = colors.inputFocusBorder,
            unfocusedBorderColor = colors.inputBorder,
            focusedTextColor = colors.ink,
            unfocusedTextColor = colors.ink
        ),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = DmSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
