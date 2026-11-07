package it.luma.tapo.model

import androidx.compose.ui.graphics.Color
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

class LumaException(
    override val message: String,
    val code: String = "error"
) : Exception(message)

data class Room(
    val id: String,
    val name: String
)

data class DeviceProfile(
    val id: String,
    val roomId: String,
    val name: String,
    val email: String,
    val ip: String,
    val model: String
)

data class EncryptedVault(
    val version: Int = 1,
    val iterations: Int = 310000,
    val salt: String,
    val iv: String,
    val ciphertext: String
)

data class AccountRecord(
    val email: String,
    val vault: EncryptedVault? = null
)

data class PublicAccount(
    val email: String,
    val rememberPassword: Boolean,
    val sessionAvailable: Boolean
)

data class House(
    val schema: Int = 1,
    val rooms: List<Room>,
    val devices: List<DeviceProfile>,
    val accounts: List<AccountRecord> = emptyList()
)

data class PublicHouse(
    val schema: Int = 1,
    val rooms: List<Room>,
    val devices: List<DeviceProfile>,
    val accounts: List<PublicAccount> = emptyList(),
    val legacyPasswordPresent: Boolean = false
)

data class DeviceCaps(
    val brightness: Boolean = false,
    val ct: Boolean = false,
    val color: Boolean = false
)

data class LightInfo(
    val name: String,
    val model: String,
    val deviceOn: Boolean,
    val brightness: Int,
    val colorTemp: Int,
    val colorTempRange: Pair<Int, Int> = 2500 to 6500,
    val hue: Int,
    val saturation: Int
)

data class DeviceSnapshot(
    val connected: Boolean,
    val deviceId: String,
    val info: LightInfo? = null,
    val caps: DeviceCaps = DeviceCaps(),
    val model: String = "",
    val ip: String = "",
    val session: String = ""
)

data class DiscoveredTapoDevice(
    val ip: String,
    val protocol: String,
    val model: String = "l530",
    val suggestedName: String,
    val mac: String? = null,
    val alreadyInHouse: Boolean = false
)

enum class LightMode {
    WHITE, COLOR
}

enum class ConnectionBadgeState {
    IDLE, ONLINE, BUSY, ERROR
}

data class ScenePreset(
    val key: String,
    val title: String,
    val subtitle: String,
    val bri: Int,
    val mode: LightMode,
    val ct: Int = 3200,
    val hue: Int = 0,
    val sat: Int = 100
)

object LumaPresets {
    val SUPPORTED_MODELS = listOf("l530", "l535", "l510", "l520", "l610", "l630", "l900", "l920", "l930")
    val WHITE_ONLY_MODELS = setOf("l510", "l520", "l610")

    val SCENES = listOf(
        ScenePreset(
            key = "relax",
            title = "Relax",
            subtitle = "Calma e luce soffusa",
            bri = 35,
            mode = LightMode.WHITE,
            ct = 2700
        ),
        ScenePreset(
            key = "focus",
            title = "Concentrazione",
            subtitle = "Chiara e luminosa",
            bri = 85,
            mode = LightMode.WHITE,
            ct = 5000
        ),
        ScenePreset(
            key = "sunset",
            title = "Tramonto",
            subtitle = "Calore da fine giornata",
            bri = 65,
            mode = LightMode.COLOR,
            hue = 28,
            sat = 90
        ),
        ScenePreset(
            key = "dream",
            title = "Sogno",
            subtitle = "Un tocco di magia",
            bri = 30,
            mode = LightMode.COLOR,
            hue = 275,
            sat = 75
        )
    )

    data class QuickSwatch(val label: String, val hue: Int, val sat: Int, val displayColor: Color)

    val QUICK_SWATCHES = listOf(
        QuickSwatch("Rosso", 0, 100, Color(0xFFF77F84)),
        QuickSwatch("Arancione", 30, 100, Color(0xFFF6AD66)),
        QuickSwatch("Giallo", 55, 100, Color(0xFFF2D569)),
        QuickSwatch("Verde", 145, 100, Color(0xFF85CDAA)),
        QuickSwatch("Blu", 220, 100, Color(0xFF88B7ED)),
        QuickSwatch("Viola", 280, 100, Color(0xFFBC9ADF))
    )

    data class WhitePreset(val label: String, val kelvin: Int, val dotColor: Color)

    val WHITE_PRESETS = listOf(
        WhitePreset("Calda", 2700, Color(0xFFEFB386)),
        WhitePreset("Naturale", 4000, Color(0xFFF4E5D2)),
        WhitePreset("Fredda", 6000, Color(0xFFCBDCF0))
    )
}

object LumaColorMath {
    fun kelvinToRgb(kelvin: Int): Triple<Int, Int, Int> {
        val t = kelvin / 100.0
        val r = if (t <= 66.0) 255.0 else 329.698727446 * (t - 60.0).pow(-0.1332047592)
        val g = if (t <= 66.0) {
            99.4708025861 * ln(t) - 161.1195681661
        } else {
            288.1221695283 * (t - 60.0).pow(-0.0755148492)
        }
        val b = when {
            t >= 66.0 -> 255.0
            t <= 19.0 -> 0.0
            else -> 138.5177312231 * ln(t - 10.0) - 305.0447927307
        }
        return Triple(
            r.roundToInt().coerceIn(0, 255),
            g.roundToInt().coerceIn(0, 255),
            b.roundToInt().coerceIn(0, 255)
        )
    }

    fun hsvToRgb(hue: Int, saturation: Int, value: Int = 100): Triple<Int, Int, Int> {
        val s = saturation.coerceIn(0, 100) / 100.0
        val v = value.coerceIn(0, 100) / 100.0
        fun f(n: Int): Int {
            val k = (n + hue / 60.0) % 6.0
            val c = v - v * s * max(0.0, min(min(k, 4.0 - k), 1.0))
            return (c * 255.0).roundToInt().coerceIn(0, 255)
        }
        return Triple(f(5), f(3), f(1))
    }

    fun rgbToColor(triple: Triple<Int, Int, Int>): Color {
        return Color(triple.first, triple.second, triple.third)
    }

    fun isPrivateIPv4(value: String): Boolean {
        val trimmed = value.trim()
        val regex = Regex("^(?:0|[1-9]\\d{0,2})(?:\\.(?:0|[1-9]\\d{0,2})){3}$")
        if (!regex.matches(trimmed)) return false
        val parts = trimmed.split(".").map { it.toIntOrNull() ?: return false }
        if (parts.any { it !in 0..255 }) return false
        if (parts[3] == 0 || parts[3] == 255) return false
        return parts[0] == 10 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168) ||
            (parts[0] == 169 && parts[1] == 254)
    }

    fun isValidSubnetPrefix(prefix: String): Boolean {
        val trimmed = prefix.trim().removeSuffix(".")
        val parts = trimmed.split(".")
        if (parts.size != 3) return false
        val nums = parts.map { it.toIntOrNull() ?: return false }
        if (nums.any { it !in 0..255 }) return false
        return nums[0] == 10 ||
            (nums[0] == 172 && nums[1] in 16..31) ||
            (nums[0] == 192 && nums[1] == 168) ||
            (nums[0] == 169 && nums[1] == 254)
    }
}
