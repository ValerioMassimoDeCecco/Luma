package it.luma.tapo.ui

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.luma.tapo.data.LumaService
import it.luma.tapo.model.ConnectionBadgeState
import it.luma.tapo.model.DeviceCaps
import it.luma.tapo.model.DeviceProfile
import it.luma.tapo.model.DeviceSnapshot
import it.luma.tapo.model.DiscoveredTapoDevice
import it.luma.tapo.model.House
import it.luma.tapo.model.LightMode
import it.luma.tapo.model.LumaColorMath
import it.luma.tapo.model.LumaException
import it.luma.tapo.model.LumaPresets
import it.luma.tapo.model.PublicHouse
import it.luma.tapo.model.Room
import it.luma.tapo.model.ScenePreset
import it.luma.tapo.protocol.TapoDiscoveryEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LumaUiState(
    val isDark: Boolean = false,
    val house: PublicHouse = PublicHouse(rooms = listOf(Room("default", "Casa")), devices = emptyList()),
    val selectedRoomId: String = "",
    val selectedDeviceId: String = "",
    val connected: Boolean = false,
    val linkError: Boolean = false,
    val isOn: Boolean = false,
    val model: String = "",
    val ip: String = "",
    val session: String = "",
    val bri: Int = 72,
    val ct: Int = 3200,
    val minCt: Int = 2500,
    val maxCt: Int = 6500,
    val hue: Int = 30,
    val sat: Int = 85,
    val mode: LightMode = LightMode.WHITE,
    val editMode: LightMode = LightMode.WHITE,
    val caps: DeviceCaps = DeviceCaps(),
    val connectionBadgeState: ConnectionBadgeState = ConnectionBadgeState.IDLE,
    val connectionText: String = "Non connessa",
    val isConnectingOrSaving: Boolean = false,
    val isRefreshing: Boolean = false,
    val showDeviceDialog: Boolean = false,
    val deviceDialogEditMode: Boolean = false,
    val deviceFormError: String = "",
    val subnetPrefix: String = "192.168.1",
    val isScanningNetwork: Boolean = false,
    val scanProgress: Float = 0f,
    val hasCompletedScanOnce: Boolean = false,
    val discoveredDevices: List<DiscoveredTapoDevice> = emptyList(),
    val selectedBatchIps: Set<String> = emptySet(),
    val showRoomDialog: Boolean = false,
    val roomDialogEditMode: Boolean = false,
    val roomFormError: String = "",
    val pendingImportHouse: House? = null,
    val importFormError: String = "",
    val showCustomColorDialog: Boolean = false,
    val toastMessage: String? = null,
    val toastIsError: Boolean = false
) {
    val currentRoom: Room?
        get() = house.rooms.find { it.id == selectedRoomId }

    val devicesInSelectedRoom: List<DeviceProfile>
        get() = house.devices.filter { it.roomId == selectedRoomId }

    val currentDevice: DeviceProfile?
        get() = house.devices.find { it.id == selectedDeviceId }

    val currentLightColor: Color
        get() {
            val rgb = if (mode == LightMode.COLOR && caps.color) {
                LumaColorMath.hsvToRgb(hue, sat)
            } else {
                LumaColorMath.kelvinToRgb(ct)
            }
            return LumaColorMath.rgbToColor(rgb)
        }

    val lightDescription: String
        get() {
            if (mode == LightMode.COLOR && caps.color) return "Luce colorata"
            if (caps.ct) {
                return when {
                    ct < 3500 -> "Bianco caldo"
                    ct <= 5000 -> "Bianco naturale"
                    else -> "Bianco freddo"
                }
            }
            return "Luce bianca"
        }

    val activeSceneKey: String?
        get() {
            if (!connected || !isOn) return null
            return LumaPresets.SCENES.firstOrNull { scene ->
                if (bri != scene.bri || mode != scene.mode) return@firstOrNull false
                if (scene.mode == LightMode.WHITE) {
                    ct == scene.ct.coerceIn(minCt, maxCt)
                } else {
                    hue == scene.hue && sat == scene.sat
                }
            }?.key
        }
}

class LumaViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val service = LumaService(appContext)
    private val initialSubnet = TapoDiscoveryEngine.detectLocalSubnet().subnetPrefix
    private val _uiState = MutableStateFlow(
        LumaUiState(
            isDark = service.isDarkTheme(),
            subnetPrefix = initialSubnet
        )
    )
    val uiState: StateFlow<LumaUiState> = _uiState.asStateFlow()

    private var navigationGeneration = 0
    private var revision = 0
    private var sending = false
    private var dragging = false
    private var sendTimerJob: Job? = null
    private var toastJob: Job? = null
    private var scanJob: Job? = null

    private data class QueuedCommand(
        val key: String,
        val path: String,
        val deviceId: String,
        val session: String,
        val revision: Int,
        val powerState: Boolean? = null,
        val brightness: Int? = null,
        val colorTemp: Int? = null,
        val hue: Int? = null,
        val saturation: Int? = null
    )

    private val pendingCommands = linkedMapOf<String, QueuedCommand>()

    init {
        bootstrap()
        startPeriodicRefresh()
    }

    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                refreshState(forceToast = false)
            }
        }
    }

    fun showToast(message: String, isError: Boolean = false, durationMs: Long = 4000L) {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = message, toastIsError = isError) }
        toastJob = viewModelScope.launch {
            delay(durationMs)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }

    fun toggleTheme() {
        val nextDark = !_uiState.value.isDark
        service.setDarkTheme(nextDark)
        _uiState.update { it.copy(isDark = nextDark) }
    }

    private fun bootstrap() {
        viewModelScope.launch {
            try {
                val house = service.bootstrap()
                val savedDevice = house.devices.find { it.id == service.getSavedDeviceId() }
                val savedRoom = house.rooms.find { it.id == service.getSavedRoomId() }
                val initialRoomId = savedDevice?.roomId ?: savedRoom?.id ?: house.rooms.firstOrNull()?.id ?: ""
                val initialDeviceId = savedDevice?.id
                    ?: house.devices.firstOrNull { it.roomId == initialRoomId }?.id
                    ?: ""

                _uiState.update {
                    it.copy(
                        house = house,
                        selectedRoomId = initialRoomId,
                        selectedDeviceId = initialDeviceId
                    )
                }
                if (initialDeviceId.isNotEmpty()) {
                    selectDevice(initialDeviceId)
                } else {
                    setIdleConnectionStatus()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        connectionBadgeState = ConnectionBadgeState.ERROR,
                        connectionText = "Errore di avvio"
                    )
                }
                showToast(e.message ?: "Errore durante il caricamento della casa.", isError = true)
            }
        }
    }

    private fun setIdleConnectionStatus() {
        _uiState.update { state ->
            state.copy(
                connectionBadgeState = ConnectionBadgeState.IDLE,
                connectionText = "Non connessa"
            )
        }
    }

    private fun setHealthyConnectionStatus() {
        _uiState.update { state ->
            state.copy(
                connectionBadgeState = ConnectionBadgeState.ONLINE,
                connectionText = "Connessa"
            )
        }
    }

    private fun resetSelectedDeviceState(deviceId: String) {
        _uiState.update {
            it.copy(
                selectedDeviceId = deviceId,
                connected = false,
                linkError = false,
                isOn = false,
                session = "",
                model = "",
                ip = "",
                bri = 72,
                ct = 3200,
                minCt = 2500,
                maxCt = 6500,
                hue = 30,
                sat = 85,
                mode = LightMode.WHITE,
                editMode = LightMode.WHITE,
                caps = DeviceCaps()
            )
        }
    }

    private fun applySnapshot(snapshot: DeviceSnapshot, followMode: Boolean = false) {
        val current = _uiState.value
        val info = snapshot.info
        if (!snapshot.connected || info == null || snapshot.deviceId != current.selectedDeviceId) return

        val minK = info.colorTempRange.first
        val maxK = info.colorTempRange.second
        val resolvedMode: LightMode
        val resolvedCt: Int
        if (info.colorTemp > 0 && snapshot.caps.ct) {
            resolvedCt = info.colorTemp.coerceIn(minK, maxK)
            resolvedMode = LightMode.WHITE
        } else if (snapshot.caps.color) {
            resolvedCt = current.ct.coerceIn(minK, maxK)
            resolvedMode = LightMode.COLOR
        } else {
            resolvedCt = current.ct.coerceIn(minK, maxK)
            resolvedMode = LightMode.WHITE
        }

        val currentEdit = current.editMode
        val resolvedEditMode = if (followMode ||
            (currentEdit == LightMode.WHITE && !snapshot.caps.ct) ||
            (currentEdit == LightMode.COLOR && !snapshot.caps.color)
        ) {
            if (snapshot.caps.ct || snapshot.caps.color) resolvedMode else LightMode.WHITE
        } else {
            currentEdit
        }

        _uiState.update {
            it.copy(
                connected = true,
                linkError = false,
                model = snapshot.model.ifEmpty { "l530" },
                ip = snapshot.ip,
                session = snapshot.session,
                caps = snapshot.caps,
                isOn = info.deviceOn,
                bri = info.brightness.coerceIn(1, 100),
                minCt = minK,
                maxCt = maxK,
                ct = resolvedCt,
                hue = info.hue.coerceIn(0, 360),
                sat = info.saturation.coerceIn(1, 100),
                mode = resolvedMode,
                editMode = resolvedEditMode
            )
        }
        setHealthyConnectionStatus()
    }

    fun selectRoom(roomId: String) {
        val state = _uiState.value
        if (state.house.rooms.none { it.id == roomId }) return
        val devices = state.house.devices.filter { it.roomId == roomId }
        val remembered = service.getSavedDeviceForRoom(roomId)
        val chosen = devices.find { it.id == remembered } ?: devices.firstOrNull()
        if (chosen != null) {
            selectDevice(chosen.id)
        } else {
            navigationGeneration++
            revision++
            _uiState.update { it.copy(selectedRoomId = roomId) }
            resetSelectedDeviceState("")
            service.saveSelection(roomId, "")
            setIdleConnectionStatus()
        }
    }

    fun selectDevice(deviceId: String) {
        val state = _uiState.value
        val profile = state.house.devices.find { it.id == deviceId } ?: return
        val generation = ++navigationGeneration
        revision++
        service.saveSelection(profile.roomId, deviceId)

        viewModelScope.launch {
            val cached = runCatching { service.getCachedSnapshot(deviceId) }.getOrNull()
            if (generation != navigationGeneration) return@launch

            if (cached != null && cached.connected) {
                _uiState.update {
                    it.copy(
                        selectedRoomId = profile.roomId,
                        selectedDeviceId = deviceId
                    )
                }
                applySnapshot(cached, followMode = true)
            } else {
                _uiState.update {
                    it.copy(
                        selectedRoomId = profile.roomId,
                        connectionBadgeState = ConnectionBadgeState.BUSY,
                        connectionText = "Connessione…"
                    )
                }
                resetSelectedDeviceState(deviceId)
            }

            try {
                val res = service.activateDevice(deviceId)
                if (generation != navigationGeneration || _uiState.value.selectedDeviceId != deviceId) return@launch
                _uiState.update { it.copy(house = res.house) }
                applySnapshot(res.snapshot, followMode = cached == null)
            } catch (err: Exception) {
                if (generation != navigationGeneration || _uiState.value.selectedDeviceId != deviceId) return@launch
                val code = (err as? LumaException)?.code ?: "error"
                val isPasswordMissing = code == "password_required" || code == "credentials"
                if (cached != null && cached.connected && !isPasswordMissing) {
                    // Keep the restored state visible even if the bulb is temporarily unreachable
                    _uiState.update {
                        it.copy(
                            linkError = true,
                            connectionBadgeState = ConnectionBadgeState.ERROR,
                            connectionText = "Non raggiungibile"
                        )
                    }
                } else {
                    val isLinkErr = !isPasswordMissing
                    _uiState.update {
                        it.copy(
                            linkError = isLinkErr,
                            connectionBadgeState = if (isLinkErr) ConnectionBadgeState.ERROR else ConnectionBadgeState.IDLE,
                            connectionText = if (isLinkErr) "Non raggiungibile" else "Da collegare",
                            showDeviceDialog = true,
                            deviceDialogEditMode = true,
                            deviceFormError = err.message ?: "Impossibile collegare la lampadina."
                        )
                    }
                }
            }
        }
    }

    fun refreshState(forceToast: Boolean = false) {
        val state = _uiState.value
        if (!state.connected || state.isConnectingOrSaving || state.isRefreshing || sending || pendingCommands.isNotEmpty() || dragging) {
            return
        }
        val oldRevision = revision
        val oldSession = state.session
        val deviceId = state.selectedDeviceId
        _uiState.update { it.copy(isRefreshing = true) }

        viewModelScope.launch {
            try {
                val snap = service.deviceState(deviceId)
                val current = _uiState.value
                if (deviceId == current.selectedDeviceId &&
                    oldRevision == revision &&
                    oldSession == current.session &&
                    snap.session == current.session &&
                    !sending && pendingCommands.isEmpty()
                ) {
                    applySnapshot(snap)
                    if (forceToast) showToast("Stato aggiornato")
                }
            } catch (e: Exception) {
                val current = _uiState.value
                if (deviceId == current.selectedDeviceId && oldRevision == revision && oldSession == current.session) {
                    _uiState.update {
                        it.copy(
                            linkError = true,
                            connectionBadgeState = ConnectionBadgeState.ERROR,
                            connectionText = "Connessione persa"
                        )
                    }
                    if (forceToast) showToast(e.message ?: "Connessione persa", isError = true, durationMs = 5500L)
                }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    // Coalescing command queue matching app.js
    private fun queueCommand(
        key: String,
        path: String,
        immediate: Boolean = false,
        powerState: Boolean? = null,
        brightness: Int? = null,
        colorTemp: Int? = null,
        hue: Int? = null,
        saturation: Int? = null
    ) {
        val state = _uiState.value
        if (!state.connected || state.selectedDeviceId.isEmpty()) return
        val deviceId = state.selectedDeviceId
        val queueKey = "$deviceId:$key"
        val cmd = QueuedCommand(
            key = queueKey,
            path = path,
            deviceId = deviceId,
            session = state.session,
            revision = ++revision,
            powerState = powerState,
            brightness = brightness,
            colorTemp = colorTemp,
            hue = hue,
            saturation = saturation
        )
        pendingCommands.remove(queueKey)
        pendingCommands[queueKey] = cmd

        if (!sending) {
            if (immediate && sendTimerJob != null) {
                sendTimerJob?.cancel()
                sendTimerJob = null
            }
            if (sendTimerJob == null) {
                sendTimerJob = viewModelScope.launch {
                    if (!immediate) delay(140L)
                    drainQueue()
                }
            }
        }
    }

    private fun flushQueue() {
        if (!sending && pendingCommands.isNotEmpty()) {
            sendTimerJob?.cancel()
            sendTimerJob = viewModelScope.launch {
                drainQueue()
            }
        }
    }

    private suspend fun drainQueue() {
        sendTimerJob?.cancel()
        sendTimerJob = null
        if (sending || pendingCommands.isEmpty()) return
        sending = true
        var mustReconcile = false

        while (pendingCommands.isNotEmpty()) {
            val firstKey = pendingCommands.keys.first()
            val cmd = pendingCommands.remove(firstKey) ?: continue
            val started = System.currentTimeMillis()
            try {
                val result = service.sendCommand(
                    path = cmd.path,
                    deviceId = cmd.deviceId,
                    session = cmd.session,
                    powerState = cmd.powerState,
                    brightness = cmd.brightness,
                    colorTemp = cmd.colorTemp,
                    hue = cmd.hue,
                    saturation = cmd.saturation
                )
                val current = _uiState.value
                if (cmd.deviceId == current.selectedDeviceId && cmd.session == current.session) {
                    if (cmd.revision == revision && pendingCommands.isEmpty()) {
                        applySnapshot(result)
                        mustReconcile = false
                    } else {
                        mustReconcile = true
                    }
                }
            } catch (e: Exception) {
                val current = _uiState.value
                val devName = current.house.devices.find { it.id == cmd.deviceId }?.name
                if (cmd.deviceId == current.selectedDeviceId && cmd.session == current.session) {
                    mustReconcile = true
                }
                val msg = e.message ?: "Errore nell'invio del comando."
                showToast(
                    if (devName != null && cmd.deviceId != current.selectedDeviceId) "$devName: $msg" else msg,
                    isError = true,
                    durationMs = 5500L
                )
            }
            if (pendingCommands.isNotEmpty()) {
                val elapsed = System.currentTimeMillis() - started
                val waitMs = (160L - elapsed).coerceAtLeast(0L)
                if (waitMs > 0L) delay(waitMs)
            }
        }
        sending = false
        if (mustReconcile) {
            refreshState(forceToast = false)
        }
    }

    private fun discardPending(deviceId: String) {
        val toRemove = pendingCommands.filterValues { it.deviceId == deviceId }.keys.toList()
        for (k in toRemove) pendingCommands.remove(k)
    }

    fun onPowerOrConnectClicked() {
        val state = _uiState.value
        if (state.currentDevice == null) {
            openDeviceDialog(edit = false)
            return
        }
        if (!state.connected) {
            selectDevice(state.selectedDeviceId)
            return
        }
        val nextOn = !state.isOn
        _uiState.update { it.copy(isOn = nextOn) }
        queueCommand(key = "power", path = "/api/power", immediate = true, powerState = nextOn)
    }

    fun onSelectEditMode(mode: LightMode) {
        val state = _uiState.value
        if (!state.connected) return
        if (mode == LightMode.WHITE && !state.caps.ct) return
        if (mode == LightMode.COLOR && !state.caps.color) return
        _uiState.update { it.copy(editMode = mode) }
    }

    fun onBrightnessChanged(newBri: Int, finished: Boolean) {
        dragging = !finished
        val clamped = newBri.coerceIn(1, 100)
        _uiState.update { it.copy(bri = clamped, isOn = true) }
        queueCommand(key = "brightness", path = "/api/brightness", brightness = clamped)
        if (finished) flushQueue()
    }

    fun onWhiteTemperatureChanged(newCt: Int, finished: Boolean) {
        dragging = !finished
        val state = _uiState.value
        val clamped = newCt.coerceIn(state.minCt, state.maxCt)
        _uiState.update {
            it.copy(ct = clamped, mode = LightMode.WHITE, editMode = LightMode.WHITE, isOn = true)
        }
        queueCommand(key = "ct", path = "/api/color_temp", colorTemp = clamped)
        if (finished) flushQueue()
    }

    fun onColorChanged(newHue: Int, newSat: Int, finished: Boolean) {
        dragging = !finished
        val h = newHue.coerceIn(0, 360)
        val s = newSat.coerceIn(1, 100)
        _uiState.update {
            it.copy(hue = h, sat = s, mode = LightMode.COLOR, editMode = LightMode.COLOR, isOn = true)
        }
        queueCommand(key = "color", path = "/api/color_hsv", hue = h, saturation = s)
        if (finished) flushQueue()
    }

    fun applyScene(scene: ScenePreset) {
        val state = _uiState.value
        if (!state.connected) return
        val clampedCt = scene.ct.coerceIn(state.minCt, state.maxCt)
        _uiState.update {
            it.copy(
                bri = scene.bri,
                isOn = true,
                mode = scene.mode,
                editMode = scene.mode,
                ct = if (scene.mode == LightMode.WHITE) clampedCt else it.ct,
                hue = if (scene.mode == LightMode.COLOR) scene.hue else it.hue,
                sat = if (scene.mode == LightMode.COLOR) scene.sat else it.sat
            )
        }
        queueCommand(key = "brightness", path = "/api/brightness", brightness = scene.bri)
        if (scene.mode == LightMode.WHITE) {
            queueCommand(key = "ct", path = "/api/color_temp", colorTemp = clampedCt)
        } else {
            queueCommand(key = "color", path = "/api/color_hsv", hue = scene.hue, saturation = scene.sat)
        }
        flushQueue()
    }

    // Local Network Discovery actions
    fun openDiscoveryAndScan() {
        val detected = TapoDiscoveryEngine.detectLocalSubnet()
        _uiState.update {
            it.copy(
                showDeviceDialog = true,
                deviceDialogEditMode = false,
                deviceFormError = "",
                subnetPrefix = if (LumaColorMath.isValidSubnetPrefix(it.subnetPrefix)) it.subnetPrefix else detected.subnetPrefix
            )
        }
        startNetworkDiscovery()
    }

    fun onSubnetPrefixChanged(newPrefix: String) {
        _uiState.update { it.copy(subnetPrefix = newPrefix, deviceFormError = "") }
    }

    fun toggleNetworkDiscovery() {
        if (_uiState.value.isScanningNetwork) {
            stopNetworkDiscovery()
        } else {
            startNetworkDiscovery()
        }
    }

    fun startNetworkDiscovery() {
        val prefix = _uiState.value.subnetPrefix.trim()
        if (!LumaColorMath.isValidSubnetPrefix(prefix)) {
            _uiState.update {
                it.copy(deviceFormError = "Inserisci una sottorete privata valida, es. 192.168.1 o 10.0.0.")
            }
            return
        }

        scanJob?.cancel()
        val existingIps = _uiState.value.house.devices.map { it.ip.trim() }.toSet()
        _uiState.update {
            it.copy(
                isScanningNetwork = true,
                scanProgress = 0.04f,
                deviceFormError = "",
                discoveredDevices = emptyList(),
                selectedBatchIps = emptySet()
            )
        }

        scanJob = viewModelScope.launch {
            try {
                val results = TapoDiscoveryEngine.discoverTapoBulbs(
                    subnetPrefixInput = prefix,
                    existingHouseIps = existingIps,
                    onProgress = { progress, list ->
                        _uiState.update { state ->
                            val currentExisting = state.house.devices.map { d -> d.ip.trim() }.toSet()
                            val refreshed = list.map { d -> d.copy(alreadyInHouse = currentExisting.contains(d.ip)) }
                            state.copy(
                                scanProgress = progress,
                                discoveredDevices = refreshed
                            )
                        }
                    }
                )
                val currentExisting = _uiState.value.house.devices.map { it.ip.trim() }.toSet()
                val finalDevices = results.map { it.copy(alreadyInHouse = currentExisting.contains(it.ip)) }
                val autoSelectedBatch = finalDevices.filter { !it.alreadyInHouse }.map { it.ip }.toSet()
                _uiState.update {
                    it.copy(
                        isScanningNetwork = false,
                        scanProgress = 1f,
                        hasCompletedScanOnce = true,
                        discoveredDevices = finalDevices,
                        selectedBatchIps = if (finalDevices.size > 1) autoSelectedBatch else emptySet()
                    )
                }
            } catch (_: CancellationException) {
                _uiState.update { it.copy(isScanningNetwork = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanningNetwork = false,
                        hasCompletedScanOnce = true,
                        deviceFormError = e.message ?: "Errore durante la scansione della rete locale."
                    )
                }
            }
        }
    }

    fun stopNetworkDiscovery() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanningNetwork = false) }
    }

    fun toggleBatchIpSelection(ip: String) {
        _uiState.update { state ->
            val current = state.selectedBatchIps.toMutableSet()
            if (current.contains(ip)) current.remove(ip) else current.add(ip)
            state.copy(selectedBatchIps = current)
        }
    }

    fun saveBatchDiscoveredDevices(
        roomId: String,
        email: String,
        password: String,
        rememberPassword: Boolean,
        vaultPassword: String
    ) {
        val state = _uiState.value
        if (state.isConnectingOrSaving) return
        val selectedDevices = state.discoveredDevices.filter {
            state.selectedBatchIps.contains(it.ip) && !it.alreadyInHouse
        }
        if (selectedDevices.isEmpty()) {
            _uiState.update { it.copy(deviceFormError = "Seleziona almeno una nuova lampadina trovata.") }
            return
        }
        val trimmedEmail = email.trim()
        if (roomId.isEmpty() || trimmedEmail.isEmpty() || !trimmedEmail.contains("@")) {
            _uiState.update {
                it.copy(deviceFormError = "Inserisci l’email dell’account Tapo e scegli una stanza per aggiungere le luci selezionate.")
            }
            return
        }
        val account = state.house.accounts.find { it.email.equals(trimmedEmail, ignoreCase = true) }
        if (password.isEmpty() &&
            account?.sessionAvailable != true &&
            !(account?.rememberPassword == true && vaultPassword.isNotEmpty())
        ) {
            _uiState.update {
                it.copy(deviceFormError = "Inserisci la password Tapo o sblocca l’archivio cifrato.")
            }
            return
        }

        stopNetworkDiscovery()
        _uiState.update { it.copy(isConnectingOrSaving = true, deviceFormError = "") }

        viewModelScope.launch {
            try {
                val res = service.saveMultipleDevices(
                    discoveredList = selectedDevices,
                    roomId = roomId,
                    emailInput = trimmedEmail,
                    passwordInput = password,
                    rememberPassword = rememberPassword,
                    vaultPassword = vaultPassword
                )
                navigationGeneration++
                revision++
                val snap = res.firstSnapshot
                val newSelectedId = snap?.deviceId ?: ""
                _uiState.update {
                    it.copy(
                        house = res.house,
                        selectedRoomId = roomId,
                        selectedDeviceId = newSelectedId,
                        isConnectingOrSaving = false,
                        showDeviceDialog = false,
                        deviceFormError = "",
                        selectedBatchIps = emptySet()
                    )
                }
                service.saveSelection(roomId, newSelectedId)
                if (snap != null && snap.connected) {
                    applySnapshot(snap, followMode = true)
                } else {
                    resetSelectedDeviceState(newSelectedId)
                    setIdleConnectionStatus()
                }
                val summaryMsg = if (res.addedCount == 1) {
                    "1 lampadina aggiunta alla stanza."
                } else {
                    "${res.addedCount} lampadine aggiunte alla stanza."
                }
                showToast(summaryMsg)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isConnectingOrSaving = false,
                        deviceFormError = e.message ?: "Impossibile aggiungere le lampadine selezionate."
                    )
                }
            }
        }
    }

    // Device Dialog actions
    fun openDeviceDialog(edit: Boolean, initialError: String = "") {
        val detected = TapoDiscoveryEngine.detectLocalSubnet()
        _uiState.update {
            val existingIps = it.house.devices.map { d -> d.ip.trim() }.toSet()
            it.copy(
                showDeviceDialog = true,
                deviceDialogEditMode = edit && it.currentDevice != null,
                deviceFormError = initialError,
                subnetPrefix = if (LumaColorMath.isValidSubnetPrefix(it.subnetPrefix)) it.subnetPrefix else detected.subnetPrefix,
                discoveredDevices = it.discoveredDevices.map { d -> d.copy(alreadyInHouse = existingIps.contains(d.ip)) }
            )
        }
    }

    fun closeDeviceDialog() {
        if (_uiState.value.isConnectingOrSaving) return
        stopNetworkDiscovery()
        _uiState.update { it.copy(showDeviceDialog = false, deviceFormError = "") }
    }

    fun saveDevice(
        name: String,
        roomId: String,
        email: String,
        password: String,
        ip: String,
        model: String,
        rememberPassword: Boolean,
        vaultPassword: String
    ) {
        val state = _uiState.value
        if (state.isConnectingOrSaving) return
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()
        val trimmedIp = ip.trim()

        if (trimmedName.isEmpty() || roomId.isEmpty()) {
            _uiState.update { it.copy(deviceFormError = "Inserisci il nome della lampadina e scegli una stanza.") }
            return
        }
        if (trimmedEmail.isEmpty() || !trimmedEmail.contains("@") || trimmedIp.isEmpty()) {
            _uiState.update { it.copy(deviceFormError = "Inserisci email, indirizzo IP e modello della lampadina.") }
            return
        }
        if (!LumaColorMath.isPrivateIPv4(trimmedIp)) {
            _uiState.update { it.copy(deviceFormError = "Inserisci un IPv4 privato, ad esempio 192.168.1.20.") }
            return
        }
        val editingId = if (state.deviceDialogEditMode) state.selectedDeviceId else null
        val account = state.house.accounts.find { it.email.equals(trimmedEmail, ignoreCase = true) }
        if (editingId == null && password.isEmpty() &&
            account?.sessionAvailable != true &&
            !(account?.rememberPassword == true && vaultPassword.isNotEmpty())
        ) {
            _uiState.update { it.copy(deviceFormError = "Inserisci la password Tapo o sblocca l’archivio cifrato.") }
            return
        }

        stopNetworkDiscovery()
        val oldSelectedId = state.selectedDeviceId
        val oldSession = state.session
        _uiState.update { it.copy(isConnectingOrSaving = true, deviceFormError = "") }

        viewModelScope.launch {
            try {
                val res = service.saveDevice(
                    nameInput = trimmedName,
                    roomId = roomId,
                    emailInput = trimmedEmail,
                    passwordInput = password,
                    ipInput = trimmedIp,
                    modelInput = model,
                    rememberPassword = rememberPassword,
                    vaultPassword = vaultPassword,
                    editingId = editingId
                )
                navigationGeneration++
                revision++
                val newDeviceId = res.snapshot.deviceId
                _uiState.update {
                    it.copy(
                        house = res.house,
                        selectedRoomId = roomId,
                        selectedDeviceId = newDeviceId,
                        isConnectingOrSaving = false,
                        showDeviceDialog = false,
                        deviceFormError = ""
                    )
                }
                service.saveSelection(roomId, newDeviceId)
                if (editingId != null && oldSelectedId == editingId && res.snapshot.connected && oldSession != res.snapshot.session) {
                    discardPending(editingId)
                }
                if (res.snapshot.connected) {
                    applySnapshot(res.snapshot, followMode = true)
                } else {
                    resetSelectedDeviceState(newDeviceId)
                    setIdleConnectionStatus()
                }
                showToast(if (editingId != null) "Lampadina aggiornata." else "La tua luce è stata aggiunta.")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isConnectingOrSaving = false,
                        deviceFormError = e.message ?: "Impossibile salvare la lampadina."
                    )
                }
            }
        }
    }

    fun deleteCurrentDevice() {
        val state = _uiState.value
        val deviceId = state.selectedDeviceId
        if (deviceId.isEmpty() || state.isConnectingOrSaving) return
        stopNetworkDiscovery()
        _uiState.update { it.copy(isConnectingOrSaving = true) }

        viewModelScope.launch {
            try {
                val updatedHouse = service.deleteDevice(deviceId)
                navigationGeneration++
                revision++
                discardPending(deviceId)
                val nextInRoom = updatedHouse.devices.firstOrNull { it.roomId == state.selectedRoomId }
                _uiState.update {
                    it.copy(
                        house = updatedHouse,
                        isConnectingOrSaving = false,
                        showDeviceDialog = false,
                        deviceFormError = ""
                    )
                }
                resetSelectedDeviceState("")
                if (nextInRoom != null) {
                    selectDevice(nextInRoom.id)
                } else {
                    service.saveSelection(state.selectedRoomId, "")
                    setIdleConnectionStatus()
                }
                showToast("Lampadina rimossa da Luma.")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isConnectingOrSaving = false,
                        deviceFormError = e.message ?: "Impossibile rimuovere la lampadina."
                    )
                }
            }
        }
    }

    // Room Dialog actions
    fun openRoomDialog(edit: Boolean) {
        _uiState.update {
            it.copy(
                showRoomDialog = true,
                roomDialogEditMode = edit && it.currentRoom != null,
                roomFormError = ""
            )
        }
    }

    fun closeRoomDialog() {
        _uiState.update { it.copy(showRoomDialog = false, roomFormError = "") }
    }

    fun saveRoom(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(roomFormError = "Inserisci il nome della stanza.") }
            return
        }
        val state = _uiState.value
        val editingRoomId = if (state.roomDialogEditMode) state.selectedRoomId else null
        viewModelScope.launch {
            try {
                val res = service.saveRoom(trimmed, editingRoomId)
                _uiState.update {
                    it.copy(
                        house = res.house,
                        showRoomDialog = false,
                        roomFormError = ""
                    )
                }
                if (editingRoomId == null) {
                    selectRoom(res.roomId)
                }
                showToast(if (editingRoomId != null) "Stanza aggiornata." else "Stanza creata. Aggiungi una luce quando vuoi.")
            } catch (e: Exception) {
                _uiState.update { it.copy(roomFormError = e.message ?: "Impossibile salvare la stanza.") }
            }
        }
    }

    fun deleteCurrentRoom() {
        val state = _uiState.value
        val roomId = state.selectedRoomId
        if (roomId.isEmpty()) return
        viewModelScope.launch {
            try {
                val updatedHouse = service.deleteRoom(roomId)
                _uiState.update {
                    it.copy(
                        house = updatedHouse,
                        showRoomDialog = false,
                        roomFormError = ""
                    )
                }
                val firstRoom = updatedHouse.rooms.firstOrNull()
                if (firstRoom != null) {
                    selectRoom(firstRoom.id)
                }
                showToast("Stanza eliminata.")
            } catch (e: Exception) {
                _uiState.update { it.copy(roomFormError = e.message ?: "Impossibile eliminare la stanza.") }
            }
        }
    }

    // Custom Color Dialog
    fun openCustomColorDialog() {
        _uiState.update { it.copy(showCustomColorDialog = true) }
    }

    fun closeCustomColorDialog() {
        _uiState.update { it.copy(showCustomColorDialog = false) }
    }

    // Backup Import & Export
    fun onImportJsonSelected(rawJson: String) {
        try {
            val parsed = service.validateAndParseImport(rawJson)
            _uiState.update { it.copy(pendingImportHouse = parsed, importFormError = "") }
        } catch (e: Exception) {
            showToast(e.message ?: "Il file non è un JSON valido.", isError = true)
        }
    }

    fun dismissImportDialog() {
        _uiState.update { it.copy(pendingImportHouse = null, importFormError = "") }
    }

    fun confirmImportHouse() {
        val state = _uiState.value
        val candidate = state.pendingImportHouse ?: return
        if (sending || pendingCommands.isNotEmpty()) {
            _uiState.update { it.copy(importFormError = "Attendi la fine dei comandi in corso.") }
            return
        }
        viewModelScope.launch {
            try {
                val pubHouse = service.importHouse(candidate)
                navigationGeneration++
                revision++
                val firstRoomId = pubHouse.rooms.firstOrNull()?.id ?: ""
                _uiState.update {
                    it.copy(
                        house = pubHouse,
                        selectedRoomId = firstRoomId,
                        pendingImportHouse = null,
                        importFormError = ""
                    )
                }
                resetSelectedDeviceState("")
                service.saveSelection(firstRoomId, "")
                setIdleConnectionStatus()
                showToast("Casa importata. Seleziona una lampadina per collegarla.")
            } catch (e: Exception) {
                _uiState.update { it.copy(importFormError = e.message ?: "Errore durante l'importazione.") }
            }
        }
    }

    fun exportHouseBackup(onReadyToWrite: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = service.exportHouseJson()
                onReadyToWrite(json)
            } catch (e: Exception) {
                showToast(e.message ?: "Impossibile esportare il backup.", isError = true)
            }
        }
    }
}
