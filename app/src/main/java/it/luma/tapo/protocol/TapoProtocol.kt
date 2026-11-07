package it.luma.tapo.protocol

import android.util.Base64
import it.luma.tapo.model.LumaException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object TapoCrypto {
    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    fun concat(vararg arrays: ByteArray): ByteArray {
        val total = arrays.sumOf { it.size }
        val result = ByteArray(total)
        var offset = 0
        for (a in arrays) {
            System.arraycopy(a, 0, result, offset, a.size)
            offset += a.size
        }
        return result
    }

    fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) {
            append(((b.toInt() and 0xFF) + 0x100).toString(16).substring(1))
        }
    }

    fun unhex(str: String): ByteArray {
        if (str.length % 2 != 0 || !str.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            throw LumaException("Dati crittografici non validi.", "protocol")
        }
        return ByteArray(str.length / 2) { i ->
            str.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun from64(str: String): ByteArray {
        return try {
            Base64.decode(str, Base64.DEFAULT)
        } catch (_: Exception) {
            throw LumaException("La lampadina ha inviato dati non validi.", "protocol")
        }
    }

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    fun sha1(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(data)

    fun hmacSha256(keyBytes: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val realSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val prk = hmacSha256(realSalt, ikm)
        val out = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            t = hmacSha256(prk, concat(t, info, byteArrayOf(counter.toByte())))
            val copyLen = minOf(t.size, length - offset)
            System.arraycopy(t, 0, out, offset, copyLen)
            offset += copyLen
            counter++
        }
        return out
    }

    fun pbkdf2Sha256(passwordBytes: ByteArray, salt: ByteArray, iterations: Int, lengthBytes: Int): ByteArray {
        // RFC 2898 PBKDF2-HMAC-SHA256 supporting raw UTF-8 byte passwords directly
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(passwordBytes, "HmacSHA256"))
        val hLen = 32
        val blocks = (lengthBytes + hLen - 1) / hLen
        val out = ByteArray(lengthBytes)
        val blockIndexBytes = ByteArray(4)
        for (block in 1..blocks) {
            blockIndexBytes[0] = ((block ushr 24) and 0xFF).toByte()
            blockIndexBytes[1] = ((block ushr 16) and 0xFF).toByte()
            blockIndexBytes[2] = ((block ushr 8) and 0xFF).toByte()
            blockIndexBytes[3] = (block and 0xFF).toByte()
            var u = mac.doFinal(concat(salt, blockIndexBytes))
            val t = u.copyOf()
            for (i in 2..iterations) {
                u = mac.doFinal(u)
                for (j in 0 until hLen) {
                    t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
                }
            }
            val offset = (block - 1) * hLen
            val copyLen = minOf(hLen, lengthBytes - offset)
            System.arraycopy(t, 0, out, offset, copyLen)
        }
        return out
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }

    fun signedBytes(seq: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(seq).array()
    }

    fun aesCbcEncrypt(key: ByteArray, iv: ByteArray, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(plain)
    }

    fun aesCbcDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            return cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            throw LumaException("La risposta KLAP non può essere decifrata.", "bad_signature")
        }
    }

    /**
     * RFC 3610 AES-128-CCM encryption (12-byte nonce, L=3, M=16 byte tag, empty AAD).
     * Built on standard AES/ECB/NoPadding so it works across all Android and JVM environments.
     */
    fun ccmEncrypt(key: ByteArray, nonce: ByteArray, plain: ByteArray): ByteArray {
        require(nonce.size == 12) { "CCM nonce must be 12 bytes" }
        val ecb = Cipher.getInstance("AES/ECB/NoPadding")
        ecb.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))

        // 1. Compute CBC-MAC Tag T
        val b0 = ByteArray(16)
        b0[0] = 0x3A.toByte() // M=16 -> ((16-2)/2)<<3 = 56; L=3 -> (3-1) = 2; 56 + 2 = 58 (0x3A)
        System.arraycopy(nonce, 0, b0, 1, 12)
        val len = plain.size
        b0[13] = ((len ushr 16) and 0xFF).toByte()
        b0[14] = ((len ushr 8) and 0xFF).toByte()
        b0[15] = (len and 0xFF).toByte()

        var x = ecb.doFinal(b0)
        var offset = 0
        val block = ByteArray(16)
        while (offset < plain.size) {
            block.fill(0)
            val take = minOf(16, plain.size - offset)
            System.arraycopy(plain, offset, block, 0, take)
            for (i in 0 until 16) {
                x[i] = (x[i].toInt() xor block[i].toInt()).toByte()
            }
            x = ecb.doFinal(x)
            offset += 16
        }
        val tag = x

        // 2. CTR encryption of plaintext and tag
        val out = ByteArray(plain.size + 16)
        val ctrBlock = ByteArray(16)
        ctrBlock[0] = 0x02.toByte() // L-1 = 2
        System.arraycopy(nonce, 0, ctrBlock, 1, 12)

        // S_0 for tag
        ctrBlock[13] = 0
        ctrBlock[14] = 0
        ctrBlock[15] = 0
        val s0 = ecb.doFinal(ctrBlock)

        offset = 0
        var counter = 1
        while (offset < plain.size) {
            ctrBlock[13] = ((counter ushr 16) and 0xFF).toByte()
            ctrBlock[14] = ((counter ushr 8) and 0xFF).toByte()
            ctrBlock[15] = (counter and 0xFF).toByte()
            val si = ecb.doFinal(ctrBlock)
            val take = minOf(16, plain.size - offset)
            for (i in 0 until take) {
                out[offset + i] = (plain[offset + i].toInt() xor si[i].toInt()).toByte()
            }
            offset += 16
            counter++
        }

        for (i in 0 until 16) {
            out[plain.size + i] = (tag[i].toInt() xor s0[i].toInt()).toByte()
        }
        return out
    }

    fun ccmDecrypt(key: ByteArray, nonce: ByteArray, cipher: ByteArray): ByteArray {
        if (cipher.size < 16 || nonce.size != 12) {
            throw LumaException("La risposta cifrata della lampadina non è autentica o non è valida.", "bad_signature")
        }
        val ecb = Cipher.getInstance("AES/ECB/NoPadding")
        ecb.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))

        val plainLen = cipher.size - 16
        val plain = ByteArray(plainLen)

        val ctrBlock = ByteArray(16)
        ctrBlock[0] = 0x02.toByte()
        System.arraycopy(nonce, 0, ctrBlock, 1, 12)

        val s0 = ecb.doFinal(ctrBlock)
        val receivedTag = ByteArray(16)
        for (i in 0 until 16) {
            receivedTag[i] = (cipher[plainLen + i].toInt() xor s0[i].toInt()).toByte()
        }

        var offset = 0
        var counter = 1
        while (offset < plainLen) {
            ctrBlock[13] = ((counter ushr 16) and 0xFF).toByte()
            ctrBlock[14] = ((counter ushr 8) and 0xFF).toByte()
            ctrBlock[15] = (counter and 0xFF).toByte()
            val si = ecb.doFinal(ctrBlock)
            val take = minOf(16, plainLen - offset)
            for (i in 0 until take) {
                plain[offset + i] = (cipher[offset + i].toInt() xor si[i].toInt()).toByte()
            }
            offset += 16
            counter++
        }

        // Verify CBC-MAC
        val b0 = ByteArray(16)
        b0[0] = 0x3A.toByte()
        System.arraycopy(nonce, 0, b0, 1, 12)
        b0[13] = ((plainLen ushr 16) and 0xFF).toByte()
        b0[14] = ((plainLen ushr 8) and 0xFF).toByte()
        b0[15] = (plainLen and 0xFF).toByte()

        var x = ecb.doFinal(b0)
        offset = 0
        val block = ByteArray(16)
        while (offset < plainLen) {
            block.fill(0)
            val take = minOf(16, plainLen - offset)
            System.arraycopy(plain, offset, block, 0, take)
            for (i in 0 until 16) {
                x[i] = (x[i].toInt() xor block[i].toInt()).toByte()
            }
            x = ecb.doFinal(x)
            offset += 16
        }

        if (!constantTimeEquals(x, receivedTag)) {
            throw LumaException("La risposta cifrata della lampadina non è autentica o non è valida.", "bad_signature")
        }
        return plain
    }
}

data class SpakeProofResult(
    val share: ByteArray,
    val confirm: ByteArray,
    val expectedDevice: ByteArray,
    val sharedKey: ByteArray
)

object Spake2Plus {
    private fun transcript(items: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        for (item in items) {
            val lenBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(item.size.toLong()).array()
            out.write(lenBytes)
            out.write(item)
        }
        return out.toByteArray()
    }

    fun randomScalar(): BigInteger {
        while (true) {
            val candidate = BigInteger(1, TapoCrypto.randomBytes(32))
            if (candidate > BigInteger.ZERO && candidate < P256Curve.ORDER) {
                return candidate
            }
        }
    }

    fun spakeProof(
        w0: BigInteger,
        w1: BigInteger,
        x: BigInteger,
        context: ByteArray,
        proverId: ByteArray,
        verifierId: ByteArray,
        deviceShare: ByteArray
    ): SpakeProofResult {
        val yPoint = P256Curve.fromBytes(deviceShare)
        yPoint.assertValidity()

        val xPoint = P256Curve.BASE.multiply(x).add(P256Curve.M.multiply(w0))
        val unmasked = yPoint.subtract(P256Curve.N.multiply(w0))
        if (unmasked.isZero) {
            throw LumaException("La chiave della lampadina non è sicura.", "protocol")
        }
        val zPoint = unmasked.multiply(x)
        val vPoint = unmasked.multiply(w1)
        if (zPoint.isZero || vPoint.isZero) {
            throw LumaException("Scambio di chiavi non valido.", "protocol")
        }

        val share = xPoint.toRawBytesUncompressed()
        val yBytes = yPoint.toRawBytesUncompressed()
        val trans = transcript(
            listOf(
                context,
                proverId,
                verifierId,
                P256Curve.M.toRawBytesUncompressed(),
                P256Curve.N.toRawBytesUncompressed(),
                share,
                yBytes,
                zPoint.toRawBytesUncompressed(),
                vPoint.toRawBytesUncompressed(),
                P256Curve.toFixed32(w0)
            )
        )
        val main = TapoCrypto.sha256(trans)
        val confirmation = TapoCrypto.hkdfSha256(
            main,
            ByteArray(0),
            "ConfirmationKeys".toByteArray(Charsets.UTF_8),
            64
        )
        val confirm = TapoCrypto.hmacSha256(confirmation.copyOfRange(0, 32), yBytes)
        val expectedDevice = TapoCrypto.hmacSha256(confirmation.copyOfRange(32, 64), share)
        val sharedKey = TapoCrypto.hkdfSha256(
            main,
            ByteArray(0),
            "SharedKey".toByteArray(Charsets.UTF_8),
            32
        )
        return SpakeProofResult(share, confirm, expectedDevice, sharedKey)
    }

    fun makeTpapProof(password: String, registration: JSONObject, userRandom: ByteArray): SpakeProofResult {
        val crypt = registration.optJSONObject("extra_crypt")
        val type = crypt?.optString("type")
        val passwdId = crypt?.optJSONObject("params")?.optInt("passwd_id", -1) ?: -1
        if (type != "password_shadow" || passwdId != 2) {
            throw LumaException(
                "Questo firmware richiede una variante TPAP non ancora supportata. Non verranno effettuati tentativi di accesso ripetuti.",
                "unsupported"
            )
        }
        val cipherSuites = registration.optInt("cipher_suites", -1)
        val encryption = registration.optString("encryption", "")
        val iterations = registration.optInt("iterations", -1)
        if (cipherSuites != 1 || encryption != "aes_128_ccm" || iterations !in 1..100000) {
            throw LumaException("La lampadina richiede parametri TPAP non supportati.", "unsupported")
        }

        val devRandom = TapoCrypto.from64(registration.optString("dev_random", ""))
        val salt = TapoCrypto.from64(registration.optString("dev_salt", ""))
        val devShare = TapoCrypto.from64(registration.optString("dev_share", ""))
        if (devRandom.size != 32 || salt.isEmpty() || salt.size > 256 || devShare.size != 65) {
            throw LumaException("Risposta TPAP non valida.", "protocol")
        }

        val credentialHex = TapoCrypto.hex(TapoCrypto.sha1(password.toByteArray(Charsets.UTF_8)))
        val stretch = TapoCrypto.pbkdf2Sha256(
            credentialHex.toByteArray(Charsets.UTF_8),
            salt,
            iterations,
            80
        )
        val w0 = BigInteger(1, stretch.copyOfRange(0, 40)).mod(P256Curve.ORDER)
        val w1 = BigInteger(1, stretch.copyOfRange(40, 80)).mod(P256Curve.ORDER)
        val context = TapoCrypto.sha256(
            TapoCrypto.concat("PAKE V1".toByteArray(Charsets.UTF_8), userRandom, devRandom)
        )
        return spakeProof(w0, w1, randomScalar(), context, ByteArray(0), ByteArray(0), devShare)
    }
}

interface TapoSession {
    suspend fun login(email: String, password: String)
    suspend fun request(method: String, params: JSONObject? = null): JSONObject
    fun exportState(): JSONObject?
}

object TapoHttp {
    private const val MAX_RESPONSE = 1_048_576
    private const val TIMEOUT_MS = 10_000

    data class HttpResponse(
        val status: Int,
        val bytes: ByteArray,
        val setCookie: String?
    )

    suspend fun post(
        urlStr: String,
        body: ByteArray,
        contentType: String = "application/octet-stream",
        cookie: String? = null
    ): HttpResponse = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                doOutput = true
                setRequestProperty("Content-Type", contentType)
                if (!cookie.isNullOrBlank()) {
                    setRequestProperty("Cookie", cookie)
                }
            }
            conn.outputStream.use { it.write(body) }
            val status = conn.responseCode
            val contentLength = conn.contentLength
            if (contentLength > MAX_RESPONSE) {
                throw LumaException("Risposta della lampadina troppo grande.", "protocol")
            }
            val stream = if (status in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val out = ByteArrayOutputStream()
            if (stream != null) {
                stream.use { input ->
                    val buf = ByteArray(4096)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        if (out.size() > MAX_RESPONSE) {
                            throw LumaException("Risposta della lampadina troppo grande.", "protocol")
                        }
                    }
                }
            }
            val rawCookie = conn.headerFields["Set-Cookie"]?.firstOrNull()
                ?: conn.headerFields["set-cookie"]?.firstOrNull()
            val sessionCookie = rawCookie?.substringBefore(";")?.trim()
            HttpResponse(status = status, bytes = out.toByteArray(), setCookie = sessionCookie)
        } catch (e: LumaException) {
            throw e
        } catch (_: SocketTimeoutException) {
            throw LumaException("La lampadina non risponde. Controlla IP, rete e alimentazione.", "timeout")
        } catch (_: Exception) {
            throw LumaException("Non riesco a raggiungere la lampadina. Controlla IP, rete Wi-Fi e alimentazione.", "network")
        } finally {
            conn?.disconnect()
        }
    }

    fun parseJson(bytes: ByteArray): JSONObject {
        return try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (_: Exception) {
            throw LumaException("La lampadina ha inviato una risposta non valida.", "protocol")
        }
    }

    fun checkDeviceResult(data: JSONObject, duringLogin: Boolean = false): JSONObject {
        if (!data.has("error_code")) {
            throw LumaException("La risposta della lampadina non è riconosciuta.", "protocol")
        }
        val code = data.optInt("error_code", -1)
        if (code == 0) {
            return data.optJSONObject("result") ?: JSONObject()
        }
        if (code == -2101) {
            throw LumaException("Troppi tentativi di accesso: attendi che la lampadina si sblocchi prima di riprovare.", "locked")
        }
        if (code == -2203 || (duringLogin && code == -40401)) {
            throw LumaException("Credenziali Tapo non riconosciute. Verifica la password e riprova senza ripetere troppi tentativi.", "credentials")
        }
        if (code == -40401 || code == 9999) {
            throw LumaException("La sessione della lampadina è scaduta.", "session_expired")
        }
        throw LumaException("La lampadina ha rifiutato la richiesta (codice $code).", "device_error")
    }
}

class KlapSession(private val base: String) : TapoSession {
    private var key: ByteArray? = null
    private var ivPrefix: ByteArray = ByteArray(0)
    private var sigKey: ByteArray = ByteArray(0)
    private var seq: Int = 0
    private var sessionCookie: String? = null

    companion object {
        fun fromState(obj: JSONObject): KlapSession? {
            return try {
                val base = obj.optString("base", "")
                val keyB64 = obj.optString("key", "")
                val ivB64 = obj.optString("iv_prefix", "")
                val sigB64 = obj.optString("sig_key", "")
                if (base.isEmpty() || keyB64.isEmpty() || ivB64.isEmpty() || sigB64.isEmpty()) return null
                val keyBytes = TapoCrypto.from64(keyB64)
                val ivBytes = TapoCrypto.from64(ivB64)
                val sigBytes = TapoCrypto.from64(sigB64)
                if (keyBytes.size != 16 || ivBytes.size != 12 || sigBytes.size != 28) return null
                KlapSession(base).apply {
                    this.key = keyBytes
                    this.ivPrefix = ivBytes
                    this.sigKey = sigBytes
                    this.seq = obj.optInt("seq", 0)
                    this.sessionCookie = obj.optString("cookie", "").ifEmpty { null }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun exportState(): JSONObject? {
        val currentKey = key ?: return null
        if (ivPrefix.size != 12 || sigKey.size != 28) return null
        return JSONObject().apply {
            put("type", "klap")
            put("base", base)
            put("key", TapoCrypto.b64(currentKey))
            put("iv_prefix", TapoCrypto.b64(ivPrefix))
            put("sig_key", TapoCrypto.b64(sigKey))
            put("seq", seq)
            put("cookie", sessionCookie ?: "")
        }
    }

    override suspend fun login(email: String, password: String) {
        val seed = TapoCrypto.randomBytes(16)
        val auth = TapoCrypto.sha256(
            TapoCrypto.concat(
                TapoCrypto.sha1(email.toByteArray(Charsets.UTF_8)),
                TapoCrypto.sha1(password.toByteArray(Charsets.UTF_8))
            )
        )
        val one = TapoHttp.post("$base/app/handshake1", seed)
        if (one.status != 200) {
            throw LumaException(
                "La lampadina non accetta KLAP. Verifica firmware e compatibilità con servizi di terze parti nell’app Tapo.",
                "unsupported"
            )
        }
        if (one.bytes.size != 48) {
            throw LumaException(
                "Risposta KLAP non valida; questo firmware potrebbe richiedere un altro protocollo.",
                "protocol"
            )
        }
        if (!one.setCookie.isNullOrBlank()) {
            sessionCookie = one.setCookie
        }
        val remote = one.bytes.copyOfRange(0, 16)
        val serverHash = one.bytes.copyOfRange(16, 48)
        val expectedServerHash = TapoCrypto.sha256(TapoCrypto.concat(seed, remote, auth))
        if (!TapoCrypto.constantTimeEquals(expectedServerHash, serverHash)) {
            throw LumaException("Credenziali Tapo non riconosciute o lampadina associata a un altro account.", "credentials")
        }

        val twoPayload = TapoCrypto.sha256(TapoCrypto.concat(remote, seed, auth))
        val two = TapoHttp.post("$base/app/handshake2", twoPayload, cookie = sessionCookie)
        if (two.status != 200) {
            throw LumaException("Accesso KLAP non riuscito. Controlla la connessione alla rete locale.", "credentials")
        }
        if (!two.setCookie.isNullOrBlank()) {
            sessionCookie = two.setCookie
        }

        val material = TapoCrypto.concat(seed, remote, auth)
        this.key = TapoCrypto.sha256(TapoCrypto.concat("lsk".toByteArray(Charsets.UTF_8), material)).copyOfRange(0, 16)
        val ivHash = TapoCrypto.sha256(TapoCrypto.concat("iv".toByteArray(Charsets.UTF_8), material))
        this.ivPrefix = ivHash.copyOfRange(0, 12)
        this.seq = ByteBuffer.wrap(ivHash, 28, 4).order(ByteOrder.BIG_ENDIAN).int
        this.sigKey = TapoCrypto.sha256(TapoCrypto.concat("ldk".toByteArray(Charsets.UTF_8), material)).copyOfRange(0, 28)
    }

    override suspend fun request(method: String, params: JSONObject?): JSONObject {
        val currentKey = key ?: throw LumaException("Sessione non inizializzata.", "session_expired")
        seq += 1
        val currentSeq = seq
        val seqBytes = TapoCrypto.signedBytes(currentSeq)
        val iv = TapoCrypto.concat(ivPrefix, seqBytes)

        val payloadJson = JSONObject().apply {
            put("method", method)
            put("params", params ?: JSONObject.NULL)
        }
        val plain = payloadJson.toString().toByteArray(Charsets.UTF_8)
        val encrypted = TapoCrypto.aesCbcEncrypt(currentKey, iv, plain)
        val sig = TapoCrypto.sha256(TapoCrypto.concat(sigKey, seqBytes, encrypted))
        val response = TapoHttp.post(
            "$base/app/request?seq=$currentSeq",
            TapoCrypto.concat(sig, encrypted),
            cookie = sessionCookie
        )
        if (response.status == 403 || response.status == 401) {
            throw LumaException("Sessione della lampadina scaduta.", "session_expired")
        }
        if (response.status != 200) {
            throw LumaException("La lampadina non ha accettato il comando.", "device_error")
        }
        if (response.bytes.size < 48 || (response.bytes.size - 32) % 16 != 0) {
            throw LumaException("Risposta KLAP incompleta.", "protocol")
        }
        val receivedSig = response.bytes.copyOfRange(0, 32)
        val ciphertext = response.bytes.copyOfRange(32, response.bytes.size)
        val expectedSig = TapoCrypto.sha256(TapoCrypto.concat(sigKey, seqBytes, ciphertext))
        if (!TapoCrypto.constantTimeEquals(receivedSig, expectedSig)) {
            throw LumaException("La firma della risposta KLAP non è valida.", "bad_signature")
        }
        val decoded = TapoCrypto.aesCbcDecrypt(currentKey, iv, ciphertext)
        return TapoHttp.checkDeviceResult(TapoHttp.parseJson(decoded))
    }
}

class TpapSession(
    private val base: String,
    private val info: JSONObject
) : TapoSession {
    private var url: String = ""
    private var seq: Int = 0
    private var key: ByteArray? = null
    private var noncePrefix: ByteArray = ByteArray(0)

    companion object {
        fun fromState(obj: JSONObject): TpapSession? {
            return try {
                val base = obj.optString("base", "")
                val url = obj.optString("url", "")
                val keyB64 = obj.optString("key", "")
                val nonceB64 = obj.optString("nonce_prefix", "")
                if (base.isEmpty() || url.isEmpty() || keyB64.isEmpty() || nonceB64.isEmpty()) return null
                val keyBytes = TapoCrypto.from64(keyB64)
                val nonceBytes = TapoCrypto.from64(nonceB64)
                if (keyBytes.size != 16 || nonceBytes.size < 8) return null
                TpapSession(base, JSONObject()).apply {
                    this.url = url
                    this.key = keyBytes
                    this.noncePrefix = nonceBytes
                    this.seq = obj.optInt("seq", 0)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun exportState(): JSONObject? {
        val currentKey = key ?: return null
        if (url.isEmpty() || noncePrefix.size < 8) return null
        return JSONObject().apply {
            put("type", "tpap")
            put("base", base)
            put("url", url)
            put("key", TapoCrypto.b64(currentKey))
            put("nonce_prefix", TapoCrypto.b64(noncePrefix))
            put("seq", seq)
        }
    }

    override suspend fun login(email: String, password: String) {
        val tls = info.optInt("tls", 0)
        val dac = info.optInt("dac", 0)
        val pakeArray = info.optJSONArray("pake")
        val pakeList = mutableListOf<Int>()
        if (pakeArray != null) {
            for (i in 0 until pakeArray.length()) {
                pakeList.add(pakeArray.optInt(i, -1))
            }
        }
        if (tls != 0 || dac != 0 || !pakeList.contains(2) || pakeList.contains(0)) {
            val pakeSummary = if (pakeList.isEmpty()) "?" else pakeList.filter { it in 0..255 }.take(16).joinToString(",")
            throw LumaException(
                "TPAP non supportato (TLS=$tls, DAC=$dac, PAKE=[$pakeSummary]). Se vuoi, attiva Compatibilità con terze parti nell’app Tapo, attendi e riprova. Questa impostazione può ridurre la sicurezza e non garantisce il collegamento.",
                "unsupported"
            )
        }

        val ur = TapoCrypto.randomBytes(32)
        val registerParams = JSONObject().apply {
            put("sub_method", "pake_register")
            put("username", "21232f297a57a5a743894a0e4a801fc3")
            put("user_random", TapoCrypto.b64(ur))
            put("cipher_suites", JSONArray().put(1))
            put("encryption", JSONArray().put("aes_128_ccm"))
            put("passcode_type", "userpw")
        }
        val registerBody = JSONObject().apply {
            put("method", "login")
            put("params", registerParams)
        }
        val registration = loginRequest(registerBody)
        val proof = Spake2Plus.makeTpapProof(password, registration, ur)

        val shareParams = JSONObject().apply {
            put("sub_method", "pake_share")
            put("user_share", TapoCrypto.b64(proof.share))
            put("user_confirm", TapoCrypto.b64(proof.confirm))
        }
        val shareBody = JSONObject().apply {
            put("method", "login")
            put("params", shareParams)
        }
        val share = loginRequest(shareBody)
        val devConfirm = TapoCrypto.from64(share.optString("dev_confirm", ""))
        if (!TapoCrypto.constantTimeEquals(devConfirm, proof.expectedDevice)) {
            throw LumaException("La lampadina non ha confermato la propria identità; connessione interrotta.", "bad_signature")
        }
        val stok = share.optString("stok", "")
        if (!Regex("^[a-zA-Z0-9_-]{1,256}$").matches(stok) || !share.has("start_seq")) {
            throw LumaException("Parametri della sessione TPAP non validi.", "protocol")
        }
        val startSeq = share.optLong("start_seq", Long.MIN_VALUE)
        if (startSeq < Int.MIN_VALUE || startSeq > Int.MAX_VALUE) {
            throw LumaException("Parametri della sessione TPAP non validi.", "protocol")
        }
        this.url = "$base/stok=$stok/ds"
        this.seq = startSeq.toInt()
        this.key = TapoCrypto.hkdfSha256(
            proof.sharedKey,
            "tp-kdf-salt-aes128-key".toByteArray(Charsets.UTF_8),
            "tp-kdf-info-aes128-key".toByteArray(Charsets.UTF_8),
            16
        )
        this.noncePrefix = TapoCrypto.hkdfSha256(
            proof.sharedKey,
            "tp-kdf-salt-aes128-iv".toByteArray(Charsets.UTF_8),
            "tp-kdf-info-aes128-iv".toByteArray(Charsets.UTF_8),
            12
        )
    }

    private suspend fun loginRequest(body: JSONObject): JSONObject {
        val response = TapoHttp.post("$base/", body.toString().toByteArray(Charsets.UTF_8), "application/json")
        if (response.status != 200) {
            throw LumaException("Accesso TPAP non riuscito.", "device_error")
        }
        return TapoHttp.checkDeviceResult(TapoHttp.parseJson(response.bytes), duringLogin = true)
    }

    override suspend fun request(method: String, params: JSONObject?): JSONObject {
        val currentKey = key ?: throw LumaException("Sessione non inizializzata.", "session_expired")
        val currentSeq = seq
        seq = currentSeq + 1
        val seqBytes = TapoCrypto.signedBytes(currentSeq)
        val nonce = TapoCrypto.concat(noncePrefix.copyOfRange(0, 8), seqBytes)
        val reqJson = JSONObject().apply {
            put("method", method)
            put("params", params ?: JSONObject.NULL)
        }
        val encrypted = TapoCrypto.ccmEncrypt(currentKey, nonce, reqJson.toString().toByteArray(Charsets.UTF_8))
        val response = TapoHttp.post(url, TapoCrypto.concat(seqBytes, encrypted))
        if (response.status == 403 || response.status == 401) {
            throw LumaException("Sessione della lampadina scaduta.", "session_expired")
        }
        if (response.status != 200) {
            throw LumaException("La lampadina non ha accettato il comando.", "device_error")
        }
        if (response.bytes.size < 20 || !TapoCrypto.constantTimeEquals(response.bytes.copyOfRange(0, 4), seqBytes)) {
            try {
                val obj = TapoHttp.parseJson(response.bytes)
                if (obj.optInt("error_code", 0) != 0) {
                    throw LumaException("Sessione della lampadina scaduta.", "session_expired")
                }
            } catch (e: LumaException) {
                if (e.code == "session_expired") throw e
            }
            throw LumaException("Risposta TPAP non valida o fuori sequenza.", "bad_signature")
        }
        val plain = TapoCrypto.ccmDecrypt(currentKey, nonce, response.bytes.copyOfRange(4, response.bytes.size))
        return TapoHttp.checkDeviceResult(TapoHttp.parseJson(plain))
    }
}

object TapoConnector {
    fun restoreSession(stateObj: JSONObject): TapoSession? {
        return when (stateObj.optString("type", "")) {
            "klap" -> KlapSession.fromState(stateObj)
            "tpap" -> TpapSession.fromState(stateObj)
            else -> null
        }
    }

    suspend fun connectTapo(
        ip: String,
        email: String,
        password: String,
        portOverride: Int? = null
    ): TapoSession {
        val base = "http://$ip${if (portOverride != null) ":$portOverride" else ""}"
        val discoverBody = JSONObject().apply {
            put("method", "login")
            put("params", JSONObject().put("sub_method", "discover"))
        }
        val discovery = TapoHttp.post("$base/", discoverBody.toString().toByteArray(Charsets.UTF_8), "application/json")
        var answer: JSONObject? = null
        if (discovery.status == 200) {
            try {
                answer = TapoHttp.parseJson(discovery.bytes)
            } catch (_: Exception) {
                // Legacy KLAP response is not JSON
            }
        }
        if (answer != null && answer.optInt("error_code", 0) == -2101) {
            TapoHttp.checkDeviceResult(answer)
        }
        val resultObj = answer?.optJSONObject("result")
        val tpapInfo = resultObj?.optJSONObject("tpap")
        val session: TapoSession = if (answer?.optInt("error_code", -1) == 0 && tpapInfo != null) {
            val port = tpapInfo.optInt("port", -1)
            if (port !in 1..65535) {
                throw LumaException("Porta TPAP non valida.", "protocol")
            }
            val destination = if (portOverride != null) base else "http://$ip${if (port == 80) "" else ":$port"}"
            TpapSession(destination, tpapInfo)
        } else {
            KlapSession(base)
        }
        session.login(email, password)
        return session
    }
}
