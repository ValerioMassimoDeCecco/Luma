package it.luma.tapo.data

import android.content.Context
import it.luma.tapo.model.AccountRecord
import it.luma.tapo.model.DeviceCaps
import it.luma.tapo.model.DeviceProfile
import it.luma.tapo.model.DeviceSnapshot
import it.luma.tapo.model.DiscoveredTapoDevice
import it.luma.tapo.model.EncryptedVault
import it.luma.tapo.model.House
import it.luma.tapo.model.LightInfo
import it.luma.tapo.model.LumaColorMath
import it.luma.tapo.model.LumaException
import it.luma.tapo.model.LumaPresets
import it.luma.tapo.model.PublicAccount
import it.luma.tapo.model.PublicHouse
import it.luma.tapo.model.Room
import it.luma.tapo.protocol.TapoConnector
import it.luma.tapo.protocol.TapoCrypto
import it.luma.tapo.protocol.TapoSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Local service managing Rooms, Devices, Automatic Android Keystore AES-256-GCM Credential & Session Persistence,
 * batch device addition, and Real Tapo protocol sessions over LAN.
 */
class LumaService(context: Context) {
    private val prefs = context.getSharedPreferences("luma_storage", Context.MODE_PRIVATE)
    private val keystoreVault = LumaKeystoreVault(prefs)
    private val houseMutex = Mutex()
    private val sessionMutex = Mutex()
    private val deviceLocks = mutableMapOf<String, Mutex>()
    private val locksMutex = Mutex()

    private data class ActiveRecord(
        val protocol: TapoSession,
        val fingerprint: String,
        var info: LightInfo
    )

    private val records = mutableMapOf<String, ActiveRecord>()
    private val cachedInfos = mutableMapOf<String, LightInfo>()
    private val sessionPasswords = mutableMapOf<String, String>()
    private val sessionTokens = mutableMapOf<String, String>()

    private fun norm(value: String?): String = (value ?: "").trim().lowercase()
    private fun uid(): String = UUID.randomUUID().toString()
    private fun idKey(id: String): String = "real:$id"
    private val houseStorageKey = "luma_house_real"

    private fun credPrefKey(email: String): String = "luma_auto_cred_${norm(email)}"
    private fun devSessionPrefKey(deviceId: String): String = "luma_dev_session_$deviceId"

    private suspend fun deviceMutex(key: String): Mutex = locksMutex.withLock {
        deviceLocks.getOrPut(key) { Mutex() }
    }

    fun isDarkTheme(): Boolean = prefs.getString("luma_theme", "light") == "dark"

    fun setDarkTheme(dark: Boolean) {
        prefs.edit().putString("luma_theme", if (dark) "dark" else "light").apply()
    }

    fun getSavedRoomId(): String = prefs.getString("luma_room_real", "") ?: ""

    fun getSavedDeviceId(): String = prefs.getString("luma_device_real", "") ?: ""

    fun getSavedDeviceForRoom(roomId: String): String =
        prefs.getString("luma_device_room_$roomId", "") ?: ""

    fun saveSelection(roomId: String, deviceId: String) {
        val editor = prefs.edit()
            .putString("luma_room_real", roomId)
            .putString("luma_device_real", deviceId)
        if (roomId.isNotEmpty() && deviceId.isNotEmpty()) {
            editor.putString("luma_device_room_$roomId", deviceId)
        }
        editor.apply()
    }

    private fun persistAccountPassword(email: String, password: String) {
        if (password.isEmpty()) return
        val normalized = norm(email)
        val encrypted = keystoreVault.encrypt(password, "luma-cred-v1|$normalized")
        prefs.edit().putString(credPrefKey(normalized), encrypted).apply()
    }

    private fun loadPersistedPassword(email: String): String? {
        val normalized = norm(email)
        val encoded = prefs.getString(credPrefKey(normalized), null) ?: return null
        return keystoreVault.decrypt(encoded, "luma-cred-v1|$normalized")
    }

    private fun removePersistedPassword(email: String) {
        prefs.edit().remove(credPrefKey(email)).apply()
    }

    private fun serializeLightInfo(info: LightInfo): JSONObject {
        return JSONObject().apply {
            put("name", info.name)
            put("model", info.model)
            put("device_on", info.deviceOn)
            put("brightness", info.brightness)
            put("color_temp", info.colorTemp)
            put("min_ct", info.colorTempRange.first)
            put("max_ct", info.colorTempRange.second)
            put("hue", info.hue)
            put("saturation", info.saturation)
        }
    }

    private fun deserializeLightInfo(obj: JSONObject, profile: DeviceProfile): LightInfo {
        val minCt = obj.optInt("min_ct", 2500)
        val maxCt = obj.optInt("max_ct", 6500)
        val range = if (minCt in 1000..10000 && maxCt in 1000..10000 && minCt < maxCt) {
            minCt to maxCt
        } else {
            2500 to 6500
        }
        return LightInfo(
            name = profile.name,
            model = obj.optString("model", profile.model).ifEmpty { profile.model },
            deviceOn = obj.optBoolean("device_on", false),
            brightness = obj.optInt("brightness", 72).coerceIn(1, 100),
            colorTemp = obj.optInt("color_temp", 3200).coerceIn(0, 10000),
            colorTempRange = range,
            hue = obj.optInt("hue", 30).coerceIn(0, 360),
            saturation = obj.optInt("saturation", 85).coerceIn(0, 100)
        )
    }

    private fun persistDeviceSessionAndState(
        profile: DeviceProfile,
        record: ActiveRecord,
        token: String
    ) {
        cachedInfos[idKey(profile.id)] = record.info
        val payload = JSONObject().apply {
            put("fingerprint", record.fingerprint)
            put("token", token)
            put("info", serializeLightInfo(record.info))
            val sessionJson = record.protocol.exportState()
            if (sessionJson != null) {
                put("session", sessionJson)
            }
        }
        val encrypted = keystoreVault.encrypt(payload.toString(), "luma-session-v1|${profile.id}")
        prefs.edit().putString(devSessionPrefKey(profile.id), encrypted).apply()
    }

    private suspend fun restorePersistedSessionAndState(profile: DeviceProfile) {
        val encoded = prefs.getString(devSessionPrefKey(profile.id), null) ?: return
        val decrypted = keystoreVault.decrypt(encoded, "luma-session-v1|${profile.id}") ?: return
        try {
            val obj = JSONObject(decrypted)
            val savedFingerprint = obj.optString("fingerprint", "")
            if (savedFingerprint != fingerprint(profile)) return
            val infoObj = obj.optJSONObject("info") ?: return
            val info = deserializeLightInfo(infoObj, profile)
            val savedToken = obj.optString("token", "").ifEmpty { uid() }
            val key = idKey(profile.id)

            cachedInfos[key] = info
            sessionMutex.withLock {
                sessionTokens[key] = savedToken
            }

            val sessionObj = obj.optJSONObject("session")
            if (sessionObj != null) {
                val restoredProtocol = TapoConnector.restoreSession(sessionObj)
                if (restoredProtocol != null) {
                    records[key] = ActiveRecord(
                        protocol = restoredProtocol,
                        fingerprint = savedFingerprint,
                        info = info
                    )
                }
            }
        } catch (_: Exception) {
            // Ignore malformed cached session
        }
    }

    private fun removePersistedDeviceSession(deviceId: String) {
        cachedInfos.remove(idKey(deviceId))
        prefs.edit().remove(devSessionPrefKey(deviceId)).apply()
    }

    private fun emptyHouse(): House = House(
        schema = 1,
        rooms = listOf(Room(id = uid(), name = "Casa")),
        devices = emptyList(),
        accounts = emptyList()
    )

    private fun serializeHouse(house: House): String {
        val root = JSONObject()
        root.put("schema", house.schema)
        val roomsArr = JSONArray()
        for (r in house.rooms) {
            roomsArr.put(JSONObject().put("id", r.id).put("name", r.name))
        }
        root.put("rooms", roomsArr)
        val devicesArr = JSONArray()
        for (d in house.devices) {
            devicesArr.put(
                JSONObject()
                    .put("id", d.id)
                    .put("room_id", d.roomId)
                    .put("name", d.name)
                    .put("email", d.email)
                    .put("ip", d.ip)
                    .put("model", d.model)
            )
        }
        root.put("devices", devicesArr)
        val accountsArr = JSONArray()
        for (a in house.accounts) {
            val accObj = JSONObject().put("email", a.email)
            if (a.vault != null) {
                accObj.put(
                    "vault",
                    JSONObject()
                        .put("version", a.vault.version)
                        .put("iterations", a.vault.iterations)
                        .put("salt", a.vault.salt)
                        .put("iv", a.vault.iv)
                        .put("ciphertext", a.vault.ciphertext)
                )
            }
            accountsArr.put(accObj)
        }
        root.put("accounts", accountsArr)
        return root.toString()
    }

    private fun parseStoredHouse(rawJson: String?): House {
        if (rawJson.isNullOrBlank()) return emptyHouse()
        return try {
            val data = JSONObject(rawJson)
            val roomsArr = data.optJSONArray("rooms") ?: return emptyHouse()
            val devicesArr = data.optJSONArray("devices") ?: JSONArray()
            val accountsArr = data.optJSONArray("accounts") ?: JSONArray()

            val rooms = mutableListOf<Room>()
            for (i in 0 until roomsArr.length()) {
                val r = roomsArr.optJSONObject(i) ?: continue
                val id = r.optString("id", "").trim()
                val name = r.optString("name", "").trim()
                if (id.isNotEmpty() && name.isNotEmpty()) {
                    rooms.add(Room(id = id, name = name.take(50)))
                }
            }
            if (rooms.isEmpty()) return emptyHouse()
            val roomIds = rooms.map { it.id }.toSet()

            val accounts = mutableListOf<AccountRecord>()
            for (i in 0 until accountsArr.length()) {
                val a = accountsArr.optJSONObject(i) ?: continue
                val email = a.optString("email", "").trim()
                if (email.isEmpty()) continue
                val vObj = a.optJSONObject("vault")
                val vault = if (vObj != null) {
                    EncryptedVault(
                        version = vObj.optInt("version", 1),
                        iterations = vObj.optInt("iterations", 310000),
                        salt = vObj.optString("salt", ""),
                        iv = vObj.optString("iv", ""),
                        ciphertext = vObj.optString("ciphertext", "")
                    )
                } else null
                if (accounts.none { norm(it.email) == norm(email) }) {
                    accounts.add(AccountRecord(email = email, vault = vault))
                }
            }

            val devices = mutableListOf<DeviceProfile>()
            for (i in 0 until devicesArr.length()) {
                val d = devicesArr.optJSONObject(i) ?: continue
                val id = d.optString("id", "").trim()
                val roomId = d.optString("room_id", "").trim()
                val name = d.optString("name", "").trim()
                val email = d.optString("email", "").trim()
                val ip = d.optString("ip", "").trim()
                val model = d.optString("model", "l530").trim().lowercase()
                if (id.isNotEmpty() && roomIds.contains(roomId) && name.isNotEmpty() && LumaColorMath.isPrivateIPv4(ip)) {
                    val validMod = if (LumaPresets.SUPPORTED_MODELS.contains(model)) model else "l530"
                    devices.add(DeviceProfile(id = id, roomId = roomId, name = name.take(60), email = email, ip = ip, model = validMod))
                    if (email.isNotEmpty() && accounts.none { norm(it.email) == norm(email) }) {
                        accounts.add(AccountRecord(email = email, vault = null))
                    }
                }
            }

            House(schema = 1, rooms = rooms, devices = devices, accounts = accounts)
        } catch (_: Exception) {
            emptyHouse()
        }
    }

    private fun loadHouse(): House {
        return parseStoredHouse(prefs.getString(houseStorageKey, null))
    }

    private fun saveHouse(house: House) {
        prefs.edit().putString(houseStorageKey, serializeHouse(house)).apply()
    }

    private suspend fun getToken(id: String, rotate: Boolean = false): String =
        sessionMutex.withLock {
            val key = idKey(id)
            val existing = sessionTokens[key]
            if (existing == null || rotate) {
                val next = uid()
                sessionTokens[key] = next
                next
            } else {
                existing
            }
        }

    private suspend fun currentToken(id: String): String =
        sessionMutex.withLock { sessionTokens[idKey(id)] ?: "" }

    private fun cleanLabel(value: String?, what: String, max: Int): String {
        val s = (value ?: "").trim()
        if (s.isEmpty() || s.codePointCount(0, s.length) > max || s.any { it.code in 0..0x1F || it.code == 0x7F }) {
            throw LumaException("Il nome di $what deve contenere da 1 a $max caratteri.", "validation")
        }
        return s
    }

    private fun validEmail(value: String?): String {
        val s = (value ?: "").trim()
        if (s.isEmpty() || s.length > 254 || !s.contains("@") || s.any { it.isWhitespace() }) {
            throw LumaException("Inserisci l’email del tuo account Tapo.", "validation")
        }
        return s
    }

    private fun validIp(value: String?): String {
        val ip = (value ?: "").trim()
        if (!LumaColorMath.isPrivateIPv4(ip)) {
            throw LumaException("Inserisci l’IPv4 privato della lampadina, ad esempio 192.168.1.20.", "validation")
        }
        return ip
    }

    private fun validModel(value: String?): String {
        val m = (value ?: "").trim().lowercase()
        if (!LumaPresets.SUPPORTED_MODELS.contains(m)) {
            throw LumaException("Seleziona un modello Tapo supportato.", "validation")
        }
        return m
    }

    fun caps(model: String): DeviceCaps {
        val m = model.lowercase()
        val hasColorAndCt = !LumaPresets.WHITE_ONLY_MODELS.contains(m)
        return DeviceCaps(brightness = true, ct = hasColorAndCt, color = hasColorAndCt)
    }

    private fun normalizeInfo(raw: JSONObject, profile: DeviceProfile): LightInfo {
        val rangeArr = raw.optJSONArray("color_temp_range")
        val range = if (rangeArr != null && rangeArr.length() == 2) {
            val minK = rangeArr.optInt(0, 2500)
            val maxK = rangeArr.optInt(1, 6500)
            if (minK >= 1000 && maxK <= 10000 && minK < maxK) minK to maxK else 2500 to 6500
        } else {
            2500 to 6500
        }
        return LightInfo(
            name = profile.name,
            model = raw.optString("model", profile.model).take(50),
            deviceOn = raw.optBoolean("device_on", false),
            brightness = raw.optInt("brightness", 100).coerceIn(1, 100),
            colorTemp = raw.optInt("color_temp", 0).coerceIn(0, 10000),
            colorTempRange = range,
            hue = raw.optInt("hue", 0).coerceIn(0, 360),
            saturation = raw.optInt("saturation", 100).coerceIn(0, 100)
        )
    }

    private suspend fun resolveAccountSecret(email: String, explicitPassword: String = ""): String {
        if (explicitPassword.isNotEmpty()) return explicitPassword
        val normalized = norm(email)
        val inMem = sessionMutex.withLock { sessionPasswords[normalized] ?: "" }
        if (inMem.isNotEmpty()) return inMem
        val persisted = loadPersistedPassword(normalized)
        if (!persisted.isNullOrEmpty()) {
            sessionMutex.withLock { sessionPasswords[normalized] = persisted }
            return persisted
        }
        return ""
    }

    private suspend fun publicHouse(house: House): PublicHouse {
        val accounts = sessionMutex.withLock {
            house.accounts.map { a ->
                val normEmail = norm(a.email)
                val hasSecret = sessionPasswords.containsKey(normEmail) || loadPersistedPassword(normEmail) != null
                PublicAccount(
                    email = a.email,
                    rememberPassword = hasSecret || a.vault != null,
                    sessionAvailable = hasSecret
                )
            }
        }
        return PublicHouse(
            schema = 1,
            rooms = house.rooms,
            devices = house.devices,
            accounts = accounts,
            legacyPasswordPresent = false
        )
    }

    private fun makeSnapshot(profile: DeviceProfile, info: LightInfo, token: String): DeviceSnapshot {
        return DeviceSnapshot(
            connected = true,
            deviceId = profile.id,
            info = info.copy(name = profile.name),
            caps = caps(profile.model),
            model = profile.model,
            ip = profile.ip,
            session = token
        )
    }

    private fun fingerprint(profile: DeviceProfile): String =
        "${norm(profile.email)}|${profile.ip}|${profile.model}"

    private suspend fun readReal(record: ActiveRecord, profile: DeviceProfile): LightInfo {
        val raw = record.protocol.request("get_device_info", null)
        val normalized = normalizeInfo(raw, profile)
        record.info = normalized
        val token = getToken(profile.id)
        persistDeviceSessionAndState(profile, record, token)
        return normalized
    }

    private suspend fun newReal(profile: DeviceProfile, password: String): ActiveRecord {
        if (password.isEmpty()) {
            throw LumaException("Inserisci la password dell’account Tapo per collegare la lampadina.", "password_required")
        }
        val protocol = TapoConnector.connectTapo(profile.ip, profile.email, password)
        val raw = protocol.request("get_device_info", null)
        val info = normalizeInfo(raw, profile)
        return ActiveRecord(protocol = protocol, fingerprint = fingerprint(profile), info = info)
    }

    private suspend fun ensureReal(profile: DeviceProfile): ActiveRecord {
        val key = idKey(profile.id)
        val current = records[key]
        if (current != null && current.fingerprint == fingerprint(profile)) {
            return current
        }
        restorePersistedSessionAndState(profile)
        val restored = records[key]
        if (restored != null && restored.fingerprint == fingerprint(profile)) {
            return restored
        }
        val secret = resolveAccountSecret(profile.email)
        val next = newReal(profile, secret)
        records[key] = next
        val token = getToken(profile.id)
        persistDeviceSessionAndState(profile, next, token)
        return next
    }

    private suspend fun liveSnapshot(profile: DeviceProfile): DeviceSnapshot {
        val token = getToken(profile.id)
        val record = ensureReal(profile)
        return try {
            readReal(record, profile)
            makeSnapshot(profile, record.info, token)
        } catch (err: LumaException) {
            if (err.code != "session_expired" && err.code != "bad_signature" && err.code != "protocol") {
                throw err
            }
            records.remove(idKey(profile.id))
            val secret = resolveAccountSecret(profile.email)
            val replacement = newReal(profile, secret)
            records[idKey(profile.id)] = replacement
            persistDeviceSessionAndState(profile, replacement, token)
            makeSnapshot(profile, replacement.info, token)
        }
    }

    // Legacy PBKDF2-SHA256 + AES-256-GCM vault compatibility
    private fun openVault(email: String, vault: EncryptedVault, passphrase: String): String {
        if (passphrase.isEmpty()) {
            throw LumaException("Inserisci la frase segreta per sbloccare la password salvata.", "password_required")
        }
        try {
            if (vault.version != 1 || vault.iterations !in 100000..1000000) {
                throw IllegalArgumentException("vault format")
            }
            val salt = TapoCrypto.from64(vault.salt)
            val iv = TapoCrypto.from64(vault.iv)
            val cipherBytes = TapoCrypto.from64(vault.ciphertext)
            if (salt.size != 16 || iv.size != 12 || cipherBytes.size !in 16..4096) {
                throw IllegalArgumentException("vault size")
            }
            val keyBytes = TapoCrypto.pbkdf2Sha256(passphrase.toByteArray(Charsets.UTF_8), salt, vault.iterations, 32)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(128, iv)
            )
            cipher.updateAAD("luma-vault-v1|${norm(email)}".toByteArray(Charsets.UTF_8))
            val plain = cipher.doFinal(cipherBytes)
            return String(plain, Charsets.UTF_8)
        } catch (_: Exception) {
            throw LumaException("Frase segreta errata o archivio cifrato non valido.", "vault")
        }
    }

    data class RoomSaveResult(val roomId: String, val house: PublicHouse)
    data class DeviceSaveResult(val house: PublicHouse, val snapshot: DeviceSnapshot)
    data class BatchDeviceSaveResult(
        val house: PublicHouse,
        val firstSnapshot: DeviceSnapshot?,
        val addedCount: Int,
        val warnings: List<String>
    )

    suspend fun bootstrap(): PublicHouse = houseMutex.withLock {
        val house = loadHouse()
        // Automatically restore persisted account passwords and device sessions
        sessionMutex.withLock {
            for (acc in house.accounts) {
                val normEmail = norm(acc.email)
                if (!sessionPasswords.containsKey(normEmail)) {
                    val savedPass = loadPersistedPassword(normEmail)
                    if (!savedPass.isNullOrEmpty()) {
                        sessionPasswords[normEmail] = savedPass
                    }
                }
            }
        }
        for (dev in house.devices) {
            restorePersistedSessionAndState(dev)
        }
        publicHouse(house)
    }

    /**
     * Returns the immediately available cached snapshot for a device (including its last known
     * power, brightness, color temperature/HSV, and session token) so the UI can render immediately
     * on app startup before the background network refresh completes.
     */
    suspend fun getCachedSnapshot(deviceId: String): DeviceSnapshot? = houseMutex.withLock {
        val house = loadHouse()
        val profile = house.devices.find { it.id == deviceId } ?: return@withLock null
        val key = idKey(deviceId)
        if (!cachedInfos.containsKey(key)) {
            restorePersistedSessionAndState(profile)
        }
        val info = cachedInfos[key] ?: records[key]?.info ?: return@withLock null
        val token = getToken(deviceId)
        makeSnapshot(profile, info, token)
    }

    suspend fun saveRoom(nameInput: String, roomId: String? = null): RoomSaveResult =
        houseMutex.withLock {
            val house = loadHouse()
            val name = cleanLabel(nameInput, "la stanza", 50)
            if (house.rooms.any { it.id != roomId && norm(it.name) == norm(name) }) {
                throw LumaException("Esiste già una stanza con questo nome.", "duplicate")
            }
            val targetId: String
            val updatedRooms = if (roomId != null) {
                if (house.rooms.none { it.id == roomId }) {
                    throw LumaException("La stanza non esiste più.", "not_found")
                }
                targetId = roomId
                house.rooms.map { if (it.id == roomId) it.copy(name = name) else it }
            } else {
                if (house.rooms.size >= 50) {
                    throw LumaException("Hai raggiunto il limite di 50 stanze.", "limit")
                }
                targetId = uid()
                house.rooms + Room(id = targetId, name = name)
            }
            val nextHouse = house.copy(rooms = updatedRooms)
            saveHouse(nextHouse)
            RoomSaveResult(roomId = targetId, house = publicHouse(nextHouse))
        }

    suspend fun deleteRoom(roomId: String): PublicHouse = houseMutex.withLock {
        val house = loadHouse()
        if (house.rooms.none { it.id == roomId }) {
            throw LumaException("La stanza non esiste più.", "not_found")
        }
        if (house.devices.any { it.roomId == roomId }) {
            throw LumaException("Sposta o rimuovi prima le lampadine della stanza.", "room_not_empty")
        }
        if (house.rooms.size <= 1) {
            throw LumaException("Mantieni almeno una stanza.", "last_room")
        }
        val nextHouse = house.copy(rooms = house.rooms.filterNot { it.id == roomId })
        saveHouse(nextHouse)
        publicHouse(nextHouse)
    }

    suspend fun saveDevice(
        nameInput: String,
        roomId: String,
        emailInput: String,
        passwordInput: String,
        ipInput: String,
        modelInput: String,
        rememberPassword: Boolean = true,
        vaultPassword: String = "",
        editingId: String? = null
    ): DeviceSaveResult = houseMutex.withLock {
        val house = loadHouse()
        val old = if (editingId != null) {
            house.devices.find { it.id == editingId }
                ?: throw LumaException("La lampadina non esiste più.", "not_found")
        } else null
        if (old == null && house.devices.size >= 100) {
            throw LumaException("Hai raggiunto il limite di 100 lampadine.", "limit")
        }
        val name = cleanLabel(nameInput, "la lampadina", 60)
        if (house.rooms.none { it.id == roomId }) {
            throw LumaException("La stanza non esiste più.", "not_found")
        }
        val model = validModel(modelInput)
        val cleanEmail = validEmail(emailInput)
        val ip = validIp(ipInput)
        if (house.devices.any { it.id != editingId && it.ip == ip }) {
            throw LumaException("Questo IP è già associato a un’altra lampadina.", "duplicate")
        }
        val account = house.accounts.find { norm(it.email) == norm(cleanEmail) }
        val email = account?.email ?: cleanEmail
        val id = editingId ?: uid()
        val profile = DeviceProfile(id = id, roomId = roomId, name = name, email = email, ip = ip, model = model)

        deviceMutex(idKey(id)).withLock {
            var fresh: ActiveRecord? = null
            if (passwordInput.length > 1024) {
                throw LumaException("Password troppo lunga.", "validation")
            }
            var secret = resolveAccountSecret(email, passwordInput)
            if (secret.isEmpty() && account?.vault != null && vaultPassword.isNotEmpty()) {
                secret = openVault(email, account.vault, vaultPassword)
            }
            val changed = old == null || fingerprint(old) != fingerprint(profile)
            val shouldConnect = changed || passwordInput.isNotEmpty() || records[idKey(id)] == null
            if (shouldConnect && secret.isEmpty()) {
                throw LumaException("Inserisci la password dell’account Tapo per collegare la lampadina.", "password_required")
            }
            if (shouldConnect) {
                fresh = newReal(profile, secret)
            }

            val updatedDevices = if (old != null) {
                house.devices.map { if (it.id == id) profile else it }
            } else {
                house.devices + profile
            }

            val mutableAccounts = house.accounts.toMutableList()
            val existingIndex = mutableAccounts.indexOfFirst { norm(it.email) == norm(email) }
            if (existingIndex < 0) {
                mutableAccounts.add(AccountRecord(email = email, vault = null))
            }
            val usedEmails = updatedDevices.map { norm(it.email) }.toSet()
            val removedEmails = mutableAccounts.filter { !usedEmails.contains(norm(it.email)) }.map { norm(it.email) }
            val retainedAccounts = mutableAccounts.filter { usedEmails.contains(norm(it.email)) }
            val nextHouse = house.copy(devices = updatedDevices, accounts = retainedAccounts)
            saveHouse(nextHouse)

            sessionMutex.withLock {
                if (secret.isNotEmpty()) {
                    sessionPasswords[norm(email)] = secret
                    persistAccountPassword(email, secret)
                }
                for (rem in removedEmails) {
                    sessionPasswords.remove(rem)
                    removePersistedPassword(rem)
                }
            }

            val snap = if (fresh != null) {
                records[idKey(id)] = fresh
                val token = getToken(id, rotate = true)
                persistDeviceSessionAndState(profile, fresh, token)
                makeSnapshot(profile, fresh.info, token)
            } else {
                val current = records[idKey(id)]
                if (current != null && current.fingerprint == fingerprint(profile)) {
                    val token = getToken(id)
                    persistDeviceSessionAndState(profile, current, token)
                    makeSnapshot(profile, current.info, token)
                } else {
                    DeviceSnapshot(connected = false, deviceId = id)
                }
            }
            DeviceSaveResult(house = publicHouse(nextHouse), snapshot = snap)
        }
    }

    /**
     * Connects and adds multiple discovered Tapo bulbs at once to the target room.
     */
    suspend fun saveMultipleDevices(
        discoveredList: List<DiscoveredTapoDevice>,
        roomId: String,
        emailInput: String,
        passwordInput: String,
        rememberPassword: Boolean = true,
        vaultPassword: String = ""
    ): BatchDeviceSaveResult = houseMutex.withLock {
        val house = loadHouse()
        if (house.rooms.none { it.id == roomId }) {
            throw LumaException("La stanza selezionata non esiste più.", "not_found")
        }
        val cleanEmail = validEmail(emailInput)
        val account = house.accounts.find { norm(it.email) == norm(cleanEmail) }
        val email = account?.email ?: cleanEmail

        if (passwordInput.length > 1024) {
            throw LumaException("Password troppo lunga.", "validation")
        }
        var secret = resolveAccountSecret(email, passwordInput)
        if (secret.isEmpty() && account?.vault != null && vaultPassword.isNotEmpty()) {
            secret = openVault(email, account.vault, vaultPassword)
        }
        if (secret.isEmpty()) {
            throw LumaException("Inserisci la password dell’account Tapo per collegare le lampadine.", "password_required")
        }

        val candidates = discoveredList.filter { item ->
            LumaColorMath.isPrivateIPv4(item.ip) && house.devices.none { it.ip == item.ip }
        }
        if (candidates.isEmpty()) {
            throw LumaException("Seleziona almeno una lampadina non ancora presente nella casa.", "validation")
        }
        if (house.devices.size + candidates.size > 100) {
            throw LumaException("Superato il limite massimo di 100 lampadine.", "limit")
        }

        val addedProfiles = mutableListOf<DeviceProfile>()
        val warnings = mutableListOf<String>()
        var firstSnapshot: DeviceSnapshot? = null

        for (item in candidates) {
            val id = uid()
            val model = if (LumaPresets.SUPPORTED_MODELS.contains(item.model.lowercase())) item.model.lowercase() else "l530"
            val name = cleanLabel(item.suggestedName, "la lampadina", 60)
            val profile = DeviceProfile(
                id = id,
                roomId = roomId,
                name = name,
                email = email,
                ip = item.ip,
                model = model
            )
            try {
                val fresh = newReal(profile, secret)
                records[idKey(id)] = fresh
                val token = getToken(id, rotate = true)
                persistDeviceSessionAndState(profile, fresh, token)
                val snap = makeSnapshot(profile, fresh.info, token)
                if (firstSnapshot == null) {
                    firstSnapshot = snap
                }
                addedProfiles.add(profile)
            } catch (e: Exception) {
                warnings.add("${item.ip}: ${e.message ?: "Impossibile collegare"}")
            }
        }

        if (addedProfiles.isEmpty()) {
            throw LumaException(
                warnings.firstOrNull() ?: "Nessuna delle lampadine selezionate ha completato l'autenticazione.",
                "device_error"
            )
        }

        val updatedDevices = house.devices + addedProfiles
        val mutableAccounts = house.accounts.toMutableList()
        val existingIndex = mutableAccounts.indexOfFirst { norm(it.email) == norm(email) }
        if (existingIndex < 0) {
            mutableAccounts.add(AccountRecord(email = email, vault = null))
        }
        val nextHouse = house.copy(devices = updatedDevices, accounts = mutableAccounts)
        saveHouse(nextHouse)

        sessionMutex.withLock {
            sessionPasswords[norm(email)] = secret
            persistAccountPassword(email, secret)
        }

        BatchDeviceSaveResult(
            house = publicHouse(nextHouse),
            firstSnapshot = firstSnapshot,
            addedCount = addedProfiles.size,
            warnings = warnings
        )
    }

    suspend fun activateDevice(deviceId: String): DeviceSaveResult {
        return deviceMutex(idKey(deviceId)).withLock {
            val house = houseMutex.withLock { loadHouse() }
            val profile = house.devices.find { it.id == deviceId }
                ?: throw LumaException("La lampadina non esiste più.", "not_found")
            val snap = liveSnapshot(profile)
            val pub = houseMutex.withLock { publicHouse(house) }
            DeviceSaveResult(house = pub, snapshot = snap)
        }
    }

    suspend fun deleteDevice(deviceId: String): PublicHouse = houseMutex.withLock {
        val house = loadHouse()
        val profile = house.devices.find { it.id == deviceId }
            ?: throw LumaException("La lampadina non esiste più.", "not_found")
        deviceMutex(idKey(deviceId)).withLock {
            val remainingDevices = house.devices.filterNot { it.id == deviceId }
            val deleteSecret = remainingDevices.none { norm(it.email) == norm(profile.email) }
            val remainingAccounts = if (deleteSecret) {
                house.accounts.filterNot { norm(it.email) == norm(profile.email) }
            } else {
                house.accounts
            }
            val nextHouse = house.copy(devices = remainingDevices, accounts = remainingAccounts)
            saveHouse(nextHouse)
            records.remove(idKey(deviceId))
            removePersistedDeviceSession(deviceId)
            sessionMutex.withLock {
                sessionTokens.remove(idKey(deviceId))
                if (deleteSecret) {
                    sessionPasswords.remove(norm(profile.email))
                    removePersistedPassword(profile.email)
                }
            }
            publicHouse(nextHouse)
        }
    }

    suspend fun deviceState(deviceId: String): DeviceSnapshot {
        return deviceMutex(idKey(deviceId)).withLock {
            val house = houseMutex.withLock { loadHouse() }
            val profile = house.devices.find { it.id == deviceId }
                ?: throw LumaException("La lampadina non esiste più.", "not_found")
            liveSnapshot(profile)
        }
    }

    suspend fun sendCommand(
        path: String,
        deviceId: String,
        session: String,
        powerState: Boolean? = null,
        brightness: Int? = null,
        colorTemp: Int? = null,
        hue: Int? = null,
        saturation: Int? = null
    ): DeviceSnapshot {
        return deviceMutex(idKey(deviceId)).withLock {
            val house = houseMutex.withLock { loadHouse() }
            val profile = house.devices.find { it.id == deviceId }
                ?: throw LumaException("La lampadina non esiste più.", "not_found")
            val token = currentToken(deviceId)
            if (token.isEmpty() || session != token) {
                throw LumaException("La sessione della lampadina è cambiata. Selezionala di nuovo.", "stale_session")
            }
            val available = caps(profile.model)
            val params = JSONObject()
            when (path) {
                "/api/power" -> {
                    val s = powerState ?: throw LumaException("Comando di accensione non valido.", "validation")
                    params.put("device_on", s)
                }
                "/api/brightness" -> {
                    val b = brightness ?: -1
                    if (b !in 1..100) throw LumaException("Luminosità non valida.", "validation")
                    params.put("device_on", true)
                    params.put("brightness", b)
                }
                "/api/color_temp" -> {
                    if (!available.ct) throw LumaException("Questo modello non supporta la temperatura.", "unsupported")
                    val ct = colorTemp ?: -1
                    if (ct !in 1000..10000) throw LumaException("Temperatura non valida.", "validation")
                    params.put("device_on", true)
                    params.put("color_temp", ct)
                }
                "/api/color_hsv" -> {
                    if (!available.color) throw LumaException("Questo modello non supporta il colore.", "unsupported")
                    val h = hue ?: -1
                    val sat = saturation ?: -1
                    if (h !in 0..360) throw LumaException("Tonalità non valida.", "validation")
                    if (sat !in 1..100) throw LumaException("Saturazione non valida.", "validation")
                    params.put("device_on", true)
                    params.put("hue", h)
                    params.put("saturation", sat)
                    params.put("color_temp", 0)
                }
                else -> throw LumaException("Comando non valido.", "validation")
            }

            suspend fun applyTo(record: ActiveRecord): DeviceSnapshot {
                if (params.optInt("color_temp", 0) > 0) {
                    val ct = params.optInt("color_temp")
                    val range = record.info.colorTempRange
                    if (ct < range.first || ct > range.second) {
                        throw LumaException("Scegli una temperatura tra ${range.first} e ${range.second} K.", "validation")
                    }
                }
                record.protocol.request("set_device_info", params)
                readReal(record, profile)
                return makeSnapshot(profile, record.info, token)
            }

            try {
                applyTo(ensureReal(profile))
            } catch (err: LumaException) {
                if (err.code != "session_expired" && err.code != "bad_signature" && err.code != "protocol") {
                    throw err
                }
                records.remove(idKey(deviceId))
                val secret = resolveAccountSecret(profile.email)
                val fresh = newReal(profile, secret)
                records[idKey(deviceId)] = fresh
                persistDeviceSessionAndState(profile, fresh, token)
                applyTo(fresh)
            }
        }
    }

    suspend fun exportHouseJson(): String = houseMutex.withLock {
        val house = loadHouse()
        val root = JSONObject()
        root.put("schema", 1)
        val roomsArr = JSONArray()
        for (r in house.rooms) {
            roomsArr.put(JSONObject().put("id", r.id).put("name", r.name))
        }
        root.put("rooms", roomsArr)
        val devicesArr = JSONArray()
        for (d in house.devices) {
            devicesArr.put(
                JSONObject()
                    .put("id", d.id)
                    .put("room_id", d.roomId)
                    .put("name", d.name)
                    .put("email", d.email)
                    .put("ip", d.ip)
                    .put("model", d.model)
            )
        }
        root.put("devices", devicesArr)
        val accountsArr = JSONArray()
        for (a in house.accounts) {
            accountsArr.put(JSONObject().put("email", a.email).put("remember_password", false))
        }
        root.put("accounts", accountsArr)
        root.toString(2)
    }

    fun validateAndParseImport(rawJson: String): House {
        val data = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            throw LumaException("Il file non è un JSON valido.", "validation")
        }
        val roomsArr = data.optJSONArray("rooms")
        val devicesArr = data.optJSONArray("devices")
        if (data.optInt("schema", -1) != 1 ||
            roomsArr == null || roomsArr.length() !in 1..50 ||
            devicesArr == null || devicesArr.length() > 100
        ) {
            throw LumaException("Il file non è un home.json valido della versione 2.1.", "validation")
        }
        val idRegex = Regex("^[a-zA-Z0-9_-]{1,80}$")
        val roomIds = mutableSetOf<String>()
        val roomNames = mutableSetOf<String>()
        val rooms = mutableListOf<Room>()
        for (i in 0 until roomsArr.length()) {
            val r = roomsArr.optJSONObject(i)
                ?: throw LumaException("Il file contiene stanze non valide.", "validation")
            val id = r.optString("id", "")
            if (!idRegex.matches(id) || !roomIds.add(id)) {
                throw LumaException("Il file contiene stanze non valide.", "validation")
            }
            val name = cleanLabel(r.optString("name", ""), "la stanza", 50)
            if (!roomNames.add(norm(name))) {
                throw LumaException("Il file contiene stanze duplicate.", "validation")
            }
            rooms.add(Room(id = id, name = name))
        }

        val deviceIds = mutableSetOf<String>()
        val ips = mutableSetOf<String>()
        val accounts = mutableListOf<AccountRecord>()
        val devices = mutableListOf<DeviceProfile>()
        for (i in 0 until devicesArr.length()) {
            val d = devicesArr.optJSONObject(i)
                ?: throw LumaException("Il file contiene lampadine non valide.", "validation")
            val id = d.optString("id", "")
            val roomId = d.optString("room_id", "")
            if (!idRegex.matches(id) || !deviceIds.add(id) || !roomIds.contains(roomId)) {
                throw LumaException("Il file contiene lampadine non valide.", "validation")
            }
            val profile = DeviceProfile(
                id = id,
                roomId = roomId,
                name = cleanLabel(d.optString("name", ""), "la lampadina", 60),
                email = validEmail(d.optString("email", "")),
                ip = validIp(d.optString("ip", "")),
                model = validModel(d.optString("model", ""))
            )
            if (!ips.add(profile.ip)) {
                throw LumaException("Il file contiene indirizzi IP duplicati.", "validation")
            }
            devices.add(profile)
            if (accounts.none { norm(it.email) == norm(profile.email) }) {
                accounts.add(AccountRecord(email = profile.email, vault = null))
            }
        }
        return House(schema = 1, rooms = rooms, devices = devices, accounts = accounts)
    }

    suspend fun importHouse(candidate: House): PublicHouse = houseMutex.withLock {
        val previous = loadHouse()
        saveHouse(candidate)
        for (d in previous.devices) {
            records.remove(idKey(d.id))
            removePersistedDeviceSession(d.id)
        }
        sessionMutex.withLock {
            sessionPasswords.clear()
            sessionTokens.clear()
        }
        publicHouse(candidate)
    }
}
