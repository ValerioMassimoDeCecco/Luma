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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.luma.tapo.model.DeviceProfile
import it.luma.tapo.model.DiscoveredTapoDevice
import it.luma.tapo.model.LumaColorMath
import it.luma.tapo.model.LumaPresets
import it.luma.tapo.model.PublicAccount
import it.luma.tapo.model.Room
import it.luma.tapo.ui.theme.DmSansFontFamily
import it.luma.tapo.ui.theme.LocalLumaColors
import it.luma.tapo.ui.theme.LumaIcon
import it.luma.tapo.ui.theme.LumaIconType
import kotlin.math.roundToInt

@Composable
fun DeviceSettingsDialog(
    editingDevice: DeviceProfile?,
    rooms: List<Room>,
    defaultRoomId: String,
    accounts: List<PublicAccount>,
    isSaving: Boolean,
    errorMessage: String,
    subnetPrefix: String,
    onSubnetPrefixChange: (String) -> Unit,
    isScanningNetwork: Boolean,
    scanProgress: Float,
    hasCompletedScanOnce: Boolean,
    discoveredDevices: List<DiscoveredTapoDevice>,
    selectedBatchIps: Set<String>,
    onStartOrStopScan: () -> Unit,
    onToggleBatchSelection: (String) -> Unit,
    onBatchAddSelected: (
        roomId: String,
        email: String,
        password: String,
        rememberPassword: Boolean,
        vaultPassword: String
    ) -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        roomId: String,
        email: String,
        password: String,
        ip: String,
        model: String,
        rememberPassword: Boolean,
        vaultPassword: String
    ) -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalLumaColors.current
    val initialEmail = editingDevice?.email?.ifEmpty { accounts.firstOrNull()?.email ?: "" }
        ?: (accounts.firstOrNull()?.email ?: "")

    var name by remember(editingDevice) { mutableStateOf(editingDevice?.name ?: "") }
    var selectedRoomId by remember(editingDevice, defaultRoomId) {
        mutableStateOf(editingDevice?.roomId ?: defaultRoomId.ifEmpty { rooms.firstOrNull()?.id ?: "" })
    }
    var email by remember(editingDevice) { mutableStateOf(initialEmail) }
    var password by remember(editingDevice) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var ip by remember(editingDevice) { mutableStateOf(editingDevice?.ip ?: "") }
    var model by remember(editingDevice) { mutableStateOf(editingDevice?.model ?: "l530") }

    val matchedAccount = accounts.find { it.email.equals(email.trim(), ignoreCase = true) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var roomMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    val passwordPlaceholder = when {
        matchedAccount?.sessionAvailable == true -> "Già salvata sul dispositivo"
        editingDevice != null -> "Vuota per mantenere la password salvata"
        else -> "La tua password Tapo"
    }

    val credentialHint = when {
        matchedAccount?.sessionAvailable == true ->
            "Accesso salvato sul dispositivo per questo account: lascia vuoto per riutilizzarlo."
        else ->
            "Accesso e sessione vengono salvati automaticamente sul dispositivo con cifratura Android."
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(25.dp))
                .background(Brush.linearGradient(listOf(colors.dialogStart, colors.dialogEnd)))
                .border(1.dp, colors.dialogBorder, RoundedCornerShape(25.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag("device_settings_dialog")
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
                        .semantics { contentDescription = "Chiudi impostazioni" }
                        .testTag("close_device_dialog_button"),
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
                    "Cerca automaticamente le lampadine sulla rete Wi-Fi o inserisci l’IP manualmente."
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
                placeholder = "Es. Lampada divano",
                testTag = "device_name_input"
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
                        .padding(horizontal = 13.dp)
                        .testTag("device_room_selector"),
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

            // Compatibility hint
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.pillBg)
                    .border(1.dp, colors.pillBorder, RoundedCornerShape(12.dp))
                    .padding(11.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Compatibilità Tapo",
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = colors.ink
                    )
                    Text(
                        text = "Per molti firmware, attiva «Compatibilità con terze parti» nell’app Tapo, in Io → Servizi di terze parti.",
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.5.sp,
                        color = colors.muted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Email
            DialogFieldLabel("Email dell'account Tapo")
            LumaTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = "nome@esempio.it",
                keyboardType = KeyboardType.Email,
                testTag = "device_email_input"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Password with Mostra/Nascondi
            DialogFieldLabel("Password")
            Box(modifier = Modifier.fillMaxWidth()) {
                LumaTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = passwordPlaceholder,
                    isPassword = !showPassword,
                    testTag = "device_password_input"
                )
                Text(
                    text = if (showPassword) "Nascondi" else "Mostra",
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = colors.plumAccent,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .clickable { showPassword = !showPassword }
                        .padding(6.dp)
                        .testTag("toggle_password_visibility")
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = credentialHint,
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                color = colors.muted
            )

            Spacer(modifier = Modifier.height(12.dp))

            // IP + Model row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1.35f)) {
                    DialogFieldLabel("Indirizzo IP")
                    LumaTextField(
                        value = ip,
                        onValueChange = { ip = it },
                        placeholder = "192.168.1.20",
                        keyboardType = KeyboardType.Number,
                        testTag = "device_ip_input"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    DialogFieldLabel("Modello")
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(colors.inputBg)
                                .border(1.dp, colors.inputBorder, RoundedCornerShape(11.dp))
                                .clickable { modelMenuExpanded = true }
                                .padding(horizontal = 12.dp)
                                .testTag("device_model_selector"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = model.uppercase(),
                                fontFamily = DmSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
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

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.errorBg)
                        .border(1.dp, colors.errorBorder, RoundedCornerShape(10.dp))
                        .padding(11.dp)
                        .testTag("device_form_error")
                ) {
                    Text(
                        text = errorMessage,
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        color = colors.errorText
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Batch add button when multiple discovered devices are checked
            if (editingDevice == null && selectedBatchIps.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .alpha(if (isSaving) 0.6f else 1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.secondaryBtnBg)
                        .border(1.dp, colors.plumAccent, RoundedCornerShape(11.dp))
                        .clickable(enabled = !isSaving) {
                            onBatchAddSelected(
                                selectedRoomId,
                                email,
                                password,
                                true,
                                ""
                            )
                        }
                        .padding(horizontal = 12.dp)
                        .testTag("batch_add_devices_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    LumaIcon(type = LumaIconType.PLUS, color = colors.plumAccent, size = 14.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedBatchIps.size == 1) {
                            "Aggiungi 1 lampadina selezionata"
                        } else {
                            "Aggiungi ${selectedBatchIps.size} lampadine selezionate"
                        },
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = colors.ink
                    )
                }
                Spacer(modifier = Modifier.height(9.dp))
            }

            // Primary Submit Action
            val submitText = when {
                isSaving -> "Salvataggio…"
                editingDevice != null -> "Salva modifiche"
                else -> "Aggiungi e collega"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .alpha(if (isSaving) 0.6f else 1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.horizontalGradient(listOf(colors.primaryBtnStart, colors.primaryBtnEnd)))
                    .clickable(enabled = !isSaving) {
                        onSave(
                            name,
                            selectedRoomId,
                            email,
                            password,
                            ip,
                            model,
                            true,
                            ""
                        )
                    }
                    .padding(horizontal = 12.dp)
                    .testTag("save_device_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = submitText,
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                LumaIcon(type = LumaIconType.ARROW, color = Color.White, size = 14.dp)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LumaIcon(type = LumaIconType.LOCK, color = colors.muted, size = 12.dp)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Accesso e sessione salvati automaticamente sul dispositivo (AES-256-GCM).",
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    color = colors.muted
                )
            }

            if (editingDevice != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSaving) { showDeleteConfirm = true }
                        .padding(vertical = 6.dp)
                        .testTag("delete_device_button"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LumaIcon(type = LumaIconType.TRASH, color = colors.errorText, size = 14.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Rimuovi lampadina",
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = colors.errorText
                    )
                }

                if (showDeleteConfirm) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.errorBg)
                            .border(1.dp, colors.errorBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Rimuovere questa lampadina da Luma? Non verrà spenta. Se è l'ultima di questo account, sarà rimosso anche l'eventuale archivio cifrato.",
                            fontFamily = DmSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = colors.errorText
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.secondaryBtnBg)
                                    .border(1.dp, colors.secondaryBtnBorder, RoundedCornerShape(10.dp))
                                    .clickable { showDeleteConfirm = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Annulla",
                                    fontFamily = DmSansFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = colors.secondaryBtnText
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFC9838D))
                                    .clickable(enabled = !isSaving, onClick = onDelete)
                                    .testTag("confirm_delete_device_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sì, rimuovi",
                                    fontFamily = DmSansFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
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
            .padding(13.dp)
            .testTag("network_discovery_panel"),
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
                    onValueChange = { if (it.length <= 11) onSubnetPrefixChange(it) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = colors.ink
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("subnet_prefix_input")
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
                    .padding(horizontal = 12.dp)
                    .testTag("discover_ip_button"),
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
                        "Tocca una luce per compilare il modulo, oppure seleziona più caselle per aggiungerle insieme:"
                    } else {
                        "Tocca una luce trovata per aggiornarne l’indirizzo IP:"
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
                            .background(
                                if (isSelectedIp) colors.pillActiveBg else colors.inputBg
                            )
                            .border(
                                1.dp,
                                if (isSelectedIp || isCheckedForBatch) colors.plumAccent else colors.inputBorder,
                                RoundedCornerShape(11.dp)
                            )
                            .clickable { onSelectForForm(dev) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("discovered_device_${dev.ip}"),
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
                text = "Nessuna lampadina Tapo trovata su $subnetPrefix.1-254. Verifica che il telefono sia connesso al Wi-Fi di casa e che la sottorete sia corretta.",
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
    roomDeviceCount: Int,
    totalRoomsCount: Int,
    errorMessage: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalLumaColors.current
    var name by remember(editingRoom) { mutableStateOf(editingRoom?.name ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val canDelete = editingRoom != null && roomDeviceCount == 0 && totalRoomsCount > 1
    val deleteBlockReason = when {
        editingRoom == null -> ""
        roomDeviceCount > 0 -> "Per eliminare la stanza, sposta o rimuovi prima le sue lampadine."
        totalRoomsCount <= 1 -> "Mantieni almeno una stanza nella tua casa."
        else -> ""
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .clip(RoundedCornerShape(25.dp))
                .background(Brush.linearGradient(listOf(colors.dialogStart, colors.dialogEnd)))
                .border(1.dp, colors.dialogBorder, RoundedCornerShape(25.dp))
                .padding(24.dp)
                .testTag("room_dialog")
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
                        .clickable(onClick = onDismiss)
                        .semantics { contentDescription = "Chiudi" }
                        .testTag("close_room_dialog_button"),
                    contentAlignment = Alignment.Center
                ) {
                    LumaIcon(type = LumaIconType.CLOSE, color = colors.pillText, size = 16.dp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "LA TUA CASA",
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.4.sp,
                color = colors.eyebrow
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = buildAnnotatedString {
                    append(if (editingRoom != null) "Gestisci la stanza" else "Nuova stanza")
                    withStyle(SpanStyle(color = colors.titleDot)) {
                        append(".")
                    }
                },
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                letterSpacing = (-0.8).sp,
                color = colors.ink
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (editingRoom != null) "Rinomina o elimina questa stanza." else "Organizza le tue luci nel modo che preferisci.",
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = colors.muted
            )

            Spacer(modifier = Modifier.height(16.dp))

            DialogFieldLabel("Nome della stanza")
            LumaTextField(
                value = name,
                onValueChange = { if (it.length <= 50) name = it },
                placeholder = "Es. Studio",
                testTag = "room_name_input"
            )

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.errorBg)
                        .border(1.dp, colors.errorBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = errorMessage,
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        color = colors.errorText
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.horizontalGradient(listOf(colors.primaryBtnStart, colors.primaryBtnEnd)))
                    .clickable { onSave(name) }
                    .testTag("save_room_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (editingRoom != null) "Salva modifiche" else "Crea stanza",
                    fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                LumaIcon(type = LumaIconType.ARROW, color = Color.White, size = 14.dp)
            }

            if (editingRoom != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (canDelete) 1f else 0.45f)
                        .clickable(enabled = canDelete) { showDeleteConfirm = true }
                        .padding(vertical = 6.dp)
                        .testTag("delete_room_button"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LumaIcon(type = LumaIconType.TRASH, color = colors.errorText, size = 14.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Elimina stanza",
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = colors.errorText
                    )
                }

                if (deleteBlockReason.isNotEmpty()) {
                    Text(
                        text = deleteBlockReason,
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        color = colors.muted,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                    )
                }

                if (showDeleteConfirm && canDelete) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.errorBg)
                            .border(1.dp, colors.errorBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Eliminare questa stanza? L'operazione non si può annullare.",
                            fontFamily = DmSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = colors.errorText
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.secondaryBtnBg)
                                    .border(1.dp, colors.secondaryBtnBorder, RoundedCornerShape(10.dp))
                                    .clickable { showDeleteConfirm = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Annulla",
                                    fontFamily = DmSansFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = colors.secondaryBtnText
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFC9838D))
                                    .clickable(onClick = onDelete)
                                    .testTag("confirm_delete_room_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sì, elimina",
                                    fontFamily = DmSansFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImportHouseConfirmDialog(
    errorMessage: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = LocalLumaColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .clip(RoundedCornerShape(25.dp))
                .background(Brush.linearGradient(listOf(colors.dialogStart, colors.dialogEnd)))
                .border(1.dp, colors.dialogBorder, RoundedCornerShape(25.dp))
                .padding(24.dp)
                .testTag("import_house_dialog")
        ) {
            Text(
                text = "IMPORTA LA TUA CASA",
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.4.sp,
                color = colors.eyebrow
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = buildAnnotatedString {
                    append("Sostituire la configurazione")
                    withStyle(SpanStyle(color = colors.titleDot)) {
                        append("?")
                    }
                },
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 23.sp,
                color = colors.ink
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Le stanze e le lampadine attuali saranno sostituite. Le password non verranno importate: dovrai inserirle di nuovo.",
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = colors.muted
            )

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.errorBg)
                        .border(1.dp, colors.errorBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = errorMessage,
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        color = colors.errorText
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.secondaryBtnBg)
                        .border(1.dp, colors.secondaryBtnBorder, RoundedCornerShape(11.dp))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Annulla",
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = colors.secondaryBtnText
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Brush.horizontalGradient(listOf(colors.primaryBtnStart, colors.primaryBtnEnd)))
                        .clickable(onClick = onConfirm)
                        .testTag("confirm_import_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Importa casa",
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = Color.White
                    )
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
    onApplyColor: (hue: Int, sat: Int) -> Unit
) {
    val colors = LocalLumaColors.current
    var hue by remember { mutableIntStateOf(initialHue.coerceIn(0, 360)) }
    var sat by remember { mutableIntStateOf(initialSat.coerceIn(1, 100)) }
    val previewColor = LumaColorMath.rgbToColor(LumaColorMath.hsvToRgb(hue, sat))

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(colors.dialogStart, colors.dialogEnd)))
                .border(1.dp, colors.dialogBorder, RoundedCornerShape(24.dp))
                .padding(22.dp)
                .testTag("custom_color_dialog")
        ) {
            Text(
                text = "COLORE PERSONALIZZATO",
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.4.sp,
                color = colors.eyebrow
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Scegli la tua sfumatura.",
                fontFamily = DmSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp,
                color = colors.ink
            )
            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(previewColor)
                    .border(2.dp, Color.White, RoundedCornerShape(14.dp))
            )

            Spacer(modifier = Modifier.height(14.dp))
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

            Spacer(modifier = Modifier.height(10.dp))
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

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.secondaryBtnBg)
                        .border(1.dp, colors.secondaryBtnBorder, RoundedCornerShape(11.dp))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Annulla",
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = colors.secondaryBtnText
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Brush.horizontalGradient(listOf(colors.primaryBtnStart, colors.primaryBtnEnd)))
                        .clickable {
                            onApplyColor(hue, sat)
                            onDismiss()
                        }
                        .testTag("apply_custom_color_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Applica",
                        fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogFieldLabel(text: String) {
    val colors = LocalLumaColors.current
    Text(
        text = text,
        fontFamily = DmSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.5.sp,
        color = colors.ink,
        modifier = Modifier.padding(bottom = 5.dp)
    )
}

@Composable
private fun LumaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    testTag: String
) {
    val colors = LocalLumaColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = {
            Text(
                text = placeholder,
                fontFamily = DmSansFontFamily,
                fontSize = 13.5.sp,
                color = colors.muted.copy(alpha = 0.65f)
            )
        },
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = DmSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = colors.ink
        ),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(11.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.inputBg,
            unfocusedContainerColor = colors.inputBg,
            focusedBorderColor = colors.inputFocusBorder,
            unfocusedBorderColor = colors.inputBorder,
            cursorColor = colors.plumAccent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}
