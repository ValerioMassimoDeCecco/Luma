package it.luma.tapo.protocol

import it.luma.tapo.model.DiscoveredTapoDevice
import it.luma.tapo.model.LumaColorMath
import it.luma.tapo.model.LumaPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hybrid local network discovery engine for Tapo smart bulbs:
 * 1. Sends UDP broadcast packets on ports 20002 (Tapo TDDP v2) and 9999 (TP-Link Smart Home).
 * 2. Performs a fast concurrent HTTP /24 subnet scan probing both TPAP (`sub_method: discover`)
 *    and KLAP v2 (`/app/handshake1`).
 */
object TapoDiscoveryEngine {

    data class LocalNetworkInfo(
        val localIp: String,
        val subnetPrefix: String // e.g., "192.168.1"
    )

    fun detectLocalSubnet(): LocalNetworkInfo {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // Prioritize wlan / eth interfaces first
            val sorted = interfaces.sortedBy { iface ->
                val n = iface.name.lowercase()
                when {
                    n.startsWith("wlan") || n.startsWith("ap") -> 0
                    n.startsWith("eth") || n.startsWith("en") -> 1
                    else -> 2
                }
            }
            var fallbackCandidate: LocalNetworkInfo? = null
            for (iface in sorted) {
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = Collections.list(iface.inetAddresses)
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (LumaColorMath.isPrivateIPv4(ip)) {
                            val prefix = ip.substringBeforeLast(".")
                            val info = LocalNetworkInfo(localIp = ip, subnetPrefix = prefix)
                            if (ip.startsWith("192.168.") || ip.startsWith("10.") && !ip.startsWith("10.0.2.")) {
                                return info
                            }
                            if (fallbackCandidate == null) {
                                fallbackCandidate = info
                            }
                        }
                    }
                }
            }
            if (fallbackCandidate != null) return fallbackCandidate
        } catch (_: Exception) {
            // Fallback to default home subnet
        }
        return LocalNetworkInfo(localIp = "", subnetPrefix = "192.168.1")
    }

    /**
     * Runs both UDP broadcast discovery and parallel HTTP /24 subnet probing.
     */
    suspend fun discoverTapoBulbs(
        subnetPrefixInput: String,
        existingHouseIps: Set<String>,
        onProgress: suspend (progress: Float, currentList: List<DiscoveredTapoDevice>) -> Unit
    ): List<DiscoveredTapoDevice> = withContext(Dispatchers.IO) {
        val cleanPrefix = subnetPrefixInput.trim().removeSuffix(".")
        val netInfo = detectLocalSubnet()
        val prefix = if (LumaColorMath.isValidSubnetPrefix(cleanPrefix)) {
            cleanPrefix
        } else {
            netInfo.subnetPrefix
        }
        val selfIp = netInfo.localIp

        val discoveredMap = linkedMapOf<String, DiscoveredTapoDevice>()
        val mapMutex = Mutex()

        suspend fun registerDevice(device: DiscoveredTapoDevice, progress: Float) {
            val updatedList = mapMutex.withLock {
                val existing = discoveredMap[device.ip]
                val merged = if (existing != null) {
                    existing.copy(
                        protocol = if (device.protocol.isNotEmpty()) device.protocol else existing.protocol,
                        model = if (device.model != "l530" || existing.model.isEmpty()) device.model else existing.model,
                        mac = device.mac ?: existing.mac,
                        suggestedName = if (existing.suggestedName.startsWith("Lampadina Tapo")) device.suggestedName else existing.suggestedName
                    )
                } else {
                    device
                }
                discoveredMap[device.ip] = merged
                discoveredMap.values.sortedWith(
                    compareBy<DiscoveredTapoDevice> { it.alreadyInHouse }
                        .thenBy { it.ip.substringAfterLast(".").toIntOrNull() ?: 999 }
                )
            }
            onProgress(progress, updatedList)
        }

        // Step 1: Quick UDP Broadcast on ports 20002 & 9999
        val udpResults = runUdpBroadcastProbe(prefix, selfIp, existingHouseIps)
        for (dev in udpResults) {
            coroutineContext.ensureActive()
            registerDevice(dev, 0.08f)
        }

        // Step 2: Parallel /24 HTTP Subnet Scan (1..254)
        val totalHosts = 254
        val completedHosts = AtomicInteger(0)
        val semaphore = Semaphore(32)

        coroutineScope {
            val jobs = (1..254).map { hostOctet ->
                async {
                    semaphore.withPermit {
                        coroutineContext.ensureActive()
                        val ip = "$prefix.$hostOctet"
                        if (ip != selfIp) {
                            val probed = probeSingleIpHttp(ip, existingHouseIps)
                            val done = completedHosts.incrementAndGet()
                            val frac = (0.08f + (done.toFloat() / totalHosts) * 0.92f).coerceIn(0f, 1f)
                            if (probed != null) {
                                registerDevice(probed, frac)
                            } else if (done % 12 == 0 || done == totalHosts) {
                                val snapshot = mapMutex.withLock {
                                    discoveredMap.values.sortedWith(
                                        compareBy<DiscoveredTapoDevice> { it.alreadyInHouse }
                                            .thenBy { it.ip.substringAfterLast(".").toIntOrNull() ?: 999 }
                                    )
                                }
                                onProgress(frac, snapshot)
                            }
                        } else {
                            completedHosts.incrementAndGet()
                        }
                    }
                }
            }
            jobs.awaitAll()
        }

        mapMutex.withLock {
            discoveredMap.values.sortedWith(
                compareBy<DiscoveredTapoDevice> { it.alreadyInHouse }
                    .thenBy { it.ip.substringAfterLast(".").toIntOrNull() ?: 999 }
            )
        }
    }

    /**
     * Sends UDP broadcast packets to port 20002 (Tapo TDDP v2) and port 9999 (TP-Link Smart Home)
     * and collects responses for 650ms.
     */
    private fun runUdpBroadcastProbe(
        subnetPrefix: String,
        selfIp: String,
        existingHouseIps: Set<String>
    ): List<DiscoveredTapoDevice> {
        val results = mutableListOf<DiscoveredTapoDevice>()
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 220
            }

            // 1. Tapo port 20002 discovery packet (16-byte TDDP v2 header + RSA/discovery JSON)
            val tddpJson = """{"params":{"rsa_key":"-----BEGIN PUBLIC KEY-----\nMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC4\n-----END PUBLIC KEY-----"}}""".toByteArray(Charsets.UTF_8)
            val tddpHeader = ByteBuffer.allocate(16).apply {
                put(0x02.toByte()) // version 2
                put(0x00.toByte()) // type
                putShort(0x0001.toShort()) // code
                putInt(tddpJson.size)
                put(0x11.toByte())
                put(0x00.toByte())
                putShort(0x5A6B.toShort())
                putInt(0x00000000)
            }.array()
            val tddpPacket = TapoCrypto.concat(tddpHeader, tddpJson)

            // 2. TP-Link port 9999 XOR discovery packet: {"system":{"get_sysinfo":{}}}
            val kasaPlain = """{"system":{"get_sysinfo":{}}}""".toByteArray(Charsets.UTF_8)
            val kasaXor = ByteArray(kasaPlain.size)
            var key = 171
            for (i in kasaPlain.indices) {
                val c = kasaPlain[i].toInt() xor key
                key = c
                kasaXor[i] = c.toByte()
            }

            val targets = listOfNotNull(
                runCatching { InetAddress.getByName("$subnetPrefix.255") }.getOrNull(),
                runCatching { InetAddress.getByName("255.255.255.255") }.getOrNull()
            )
            for (target in targets) {
                runCatching {
                    socket.send(DatagramPacket(tddpPacket, tddpPacket.size, target, 20002))
                    socket.send(DatagramPacket(kasaXor, kasaXor.size, target, 9999))
                }
            }

            val deadline = System.currentTimeMillis() + 650L
            val recvBuf = ByteArray(4096)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val packet = DatagramPacket(recvBuf, recvBuf.size)
                    socket.receive(packet)
                    val senderIp = packet.address?.hostAddress ?: continue
                    if (senderIp == selfIp || !LumaColorMath.isPrivateIPv4(senderIp)) continue

                    val rawBytes = recvBuf.copyOfRange(0, packet.length)
                    val parsed = parseUdpDiscoveryPayload(senderIp, rawBytes, existingHouseIps)
                    if (parsed != null) {
                        results.add(parsed)
                    }
                } catch (_: Exception) {
                    // Timeout slice elapsed
                }
            }
        } catch (_: Exception) {
            // Ignore socket errors on restricted networks
        } finally {
            runCatching { socket?.close() }
        }
        return results
    }

    internal fun parseUdpDiscoveryPayload(
        senderIp: String,
        rawBytes: ByteArray,
        existingHouseIps: Set<String>
    ): DiscoveredTapoDevice? {
        if (rawBytes.isEmpty()) return null

        // Check if payload after 16-byte TDDP header is JSON
        val textCandidates = mutableListOf<String>()
        if (rawBytes.size > 16) {
            runCatching {
                val str = String(rawBytes, 16, rawBytes.size - 16, Charsets.UTF_8).trim()
                if (str.startsWith("{")) textCandidates.add(str)
            }
        }
        // Or direct JSON
        runCatching {
            val str = String(rawBytes, Charsets.UTF_8).trim()
            if (str.startsWith("{")) textCandidates.add(str)
        }
        // Or XOR-encoded port 9999 response
        runCatching {
            val decoded = ByteArray(rawBytes.size)
            var k = 171
            for (i in rawBytes.indices) {
                val b = rawBytes[i].toInt() and 0xFF
                decoded[i] = (b xor k).toByte()
                k = b
            }
            val str = String(decoded, Charsets.UTF_8).trim()
            if (str.startsWith("{")) textCandidates.add(str)
        }

        for (jsonStr in textCandidates) {
            // Verify this looks like a Tapo / TP-Link discovery JSON payload
            val lower = jsonStr.lowercase()
            if (!lower.contains("device_model") &&
                !lower.contains("device_id") &&
                !lower.contains("get_sysinfo") &&
                !lower.contains("mgt_encrypt_schm") &&
                !lower.contains("tpap") &&
                !lower.contains("error_code")
            ) {
                continue
            }

            try {
                val obj = JSONObject(jsonStr)
                val result = obj.optJSONObject("result")
                    ?: obj.optJSONObject("system")?.optJSONObject("get_sysinfo")
                    ?: obj
                val rawModel = result.optString("device_model", result.optString("model", "")).lowercase()
                if (rawModel.isNotEmpty() || result.has("device_id") || result.has("mac") || obj.has("error_code")) {
                    val detectedModel = LumaPresets.SUPPORTED_MODELS.firstOrNull { rawModel.contains(it) } ?: "l530"
                    val mac = result.optString("mac", result.optString("device_id", "")).takeIf { it.isNotEmpty() }
                    val lastOctet = senderIp.substringAfterLast(".")
                    val hasTpap = result.optJSONObject("tpap") != null ||
                        result.optString("mgt_encrypt_schm").contains("TPAP", ignoreCase = true)
                    return DiscoveredTapoDevice(
                        ip = senderIp,
                        protocol = if (hasTpap) "TPAP" else "KLAP v2",
                        model = detectedModel,
                        suggestedName = "Lampadina ${detectedModel.uppercase()} ($lastOctet)",
                        mac = mac,
                        alreadyInHouse = existingHouseIps.contains(senderIp)
                    )
                }
            } catch (_: Throwable) {
                // Fallback for pure JVM unit test environments where android.jar JSONObject is stubbed
            }

            // Regex extraction fallback
            val modelMatch = Regex(""""(?:device_model|model)"\s*:\s*"([^"]+)"""").find(jsonStr)?.groupValues?.get(1)?.lowercase() ?: ""
            val macMatch = Regex(""""mac"\s*:\s*"([^"]+)"""").find(jsonStr)?.groupValues?.get(1)
            val detectedModel = LumaPresets.SUPPORTED_MODELS.firstOrNull { modelMatch.contains(it) } ?: "l530"
            val lastOctet = senderIp.substringAfterLast(".")
            val hasTpap = lower.contains("\"tpap\"") || lower.contains("tpap")
            return DiscoveredTapoDevice(
                ip = senderIp,
                protocol = if (hasTpap) "TPAP" else "KLAP v2",
                model = detectedModel,
                suggestedName = "Lampadina ${detectedModel.uppercase()} ($lastOctet)",
                mac = macMatch,
                alreadyInHouse = existingHouseIps.contains(senderIp)
            )
        }
        return null
    }

    /**
     * Probes a single IP via HTTP on port 80:
     * 1) Checks `POST /` with `{"method":"login","params":{"sub_method":"discover"}}` for TPAP or Tapo JSON.
     * 2) Checks `POST /app/handshake1` with 16 random bytes for KLAP v2 (48-byte response or TP_SESSIONID cookie).
     */
    private fun probeSingleIpHttp(
        ip: String,
        existingHouseIps: Set<String>
    ): DiscoveredTapoDevice? {
        val lastOctet = ip.substringAfterLast(".")

        // 1. Probe TPAP / Tapo JSON endpoint
        val discoverPayload = """{"method":"login","params":{"sub_method":"discover"}}""".toByteArray(Charsets.UTF_8)
        val rootResp = quickHttpPost("http://$ip/", discoverPayload, "application/json")
        if (rootResp != null && rootResp.status == 200 && rootResp.bytes.isNotEmpty()) {
            try {
                val json = JSONObject(String(rootResp.bytes, Charsets.UTF_8))
                if (json.has("error_code")) {
                    val result = json.optJSONObject("result")
                    val tpap = result?.optJSONObject("tpap")
                    val rawModel = result?.optString("device_model", result.optString("model", ""))?.lowercase() ?: ""
                    val model = LumaPresets.SUPPORTED_MODELS.firstOrNull { rawModel.contains(it) } ?: "l530"
                    val protocol = if (tpap != null) "TPAP" else "KLAP v2"
                    return DiscoveredTapoDevice(
                        ip = ip,
                        protocol = protocol,
                        model = model,
                        suggestedName = "Lampadina Tapo ($lastOctet)",
                        alreadyInHouse = existingHouseIps.contains(ip)
                    )
                }
            } catch (_: Exception) {
                // Not JSON -> proceed to KLAP v2 binary handshake1 probe
            }
        }

        // 2. Probe KLAP v2 /app/handshake1 with 16 random bytes
        val seed = TapoCrypto.randomBytes(16)
        val klapResp = quickHttpPost("http://$ip/app/handshake1", seed, "application/octet-stream")
        if (klapResp != null) {
            val isKlapResponse = (klapResp.status == 200 && klapResp.bytes.size == 48) ||
                (klapResp.setCookie?.contains("TP_SESSIONID", ignoreCase = true) == true)
            if (isKlapResponse) {
                return DiscoveredTapoDevice(
                    ip = ip,
                    protocol = "KLAP v2",
                    model = "l530",
                    suggestedName = "Lampadina Tapo ($lastOctet)",
                    alreadyInHouse = existingHouseIps.contains(ip)
                )
            }
        }

        return null
    }

    private data class QuickHttpResp(
        val status: Int,
        val bytes: ByteArray,
        val setCookie: String?
    )

    private fun quickHttpPost(
        urlStr: String,
        body: ByteArray,
        contentType: String
    ): QuickHttpResp? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 420
                readTimeout = 520
                instanceFollowRedirects = false
                useCaches = false
                doOutput = true
                setRequestProperty("Content-Type", contentType)
                setRequestProperty("Connection", "close")
            }
            conn.outputStream.use { it.write(body) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val out = ByteArrayOutputStream()
            stream?.use { input ->
                val buf = ByteArray(1024)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    if (out.size() > 8192) break
                }
            }
            val cookie = conn.headerFields["Set-Cookie"]?.firstOrNull()
                ?: conn.headerFields["set-cookie"]?.firstOrNull()
            QuickHttpResp(status = status, bytes = out.toByteArray(), setCookie = cookie)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
