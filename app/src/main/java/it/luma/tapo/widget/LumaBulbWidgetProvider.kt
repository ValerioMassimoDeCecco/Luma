package it.luma.tapo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import it.luma.tapo.MainActivity
import it.luma.tapo.R
import it.luma.tapo.data.LumaService
import it.luma.tapo.model.DeviceProfile
import it.luma.tapo.model.DeviceSnapshot
import it.luma.tapo.model.LightMode
import it.luma.tapo.model.LumaColorMath
import it.luma.tapo.model.LumaPresets
import it.luma.tapo.model.ScenePreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Interactive Home Screen Widget for Luma per Tapo.
 * Allows controlling power, brightness, scenes, and switching bulbs directly from the launcher
 * without opening the app, while preserving Luma's lavender & glass visual identity.
 */
class LumaBulbWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_POWER = "it.luma.tapo.widget.ACTION_TOGGLE_POWER"
        const val ACTION_BRI_DOWN = "it.luma.tapo.widget.ACTION_BRI_DOWN"
        const val ACTION_BRI_UP = "it.luma.tapo.widget.ACTION_BRI_UP"
        const val ACTION_SCENE_RELAX = "it.luma.tapo.widget.ACTION_SCENE_RELAX"
        const val ACTION_SCENE_FOCUS = "it.luma.tapo.widget.ACTION_SCENE_FOCUS"
        const val ACTION_SCENE_SUNSET = "it.luma.tapo.widget.ACTION_SCENE_SUNSET"
        const val ACTION_SCENE_DREAM = "it.luma.tapo.widget.ACTION_SCENE_DREAM"
        const val ACTION_NEXT_DEVICE = "it.luma.tapo.widget.ACTION_NEXT_DEVICE"
        const val ACTION_REFRESH = "it.luma.tapo.widget.ACTION_REFRESH"

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAllWidgets(context: Context, statusOverride: String? = null) {
            val appContext = context.applicationContext
            widgetScope.launch {
                renderWidgetsInternal(appContext, statusOverride)
            }
        }

        fun canRequestPinWidget(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val mgr = AppWidgetManager.getInstance(context)
            return mgr.isRequestPinAppWidgetSupported
        }

        fun requestPinWidget(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val mgr = AppWidgetManager.getInstance(context)
            if (!mgr.isRequestPinAppWidgetSupported) return false
            val provider = ComponentName(context, LumaBulbWidgetProvider::class.java)
            return mgr.requestPinAppWidget(provider, null, null)
        }

        private suspend fun renderWidgetsInternal(context: Context, statusOverride: String? = null) {
            val mgr = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, LumaBulbWidgetProvider::class.java)
            val ids = mgr.getAppWidgetIds(component)
            if (ids.isEmpty()) return

            val service = LumaService(context)
            val isDark = service.isDarkTheme()
            val house = service.bootstrap()
            val savedDeviceId = service.getSavedDeviceId()
            val activeDevice: DeviceProfile? = house.devices.find { it.id == savedDeviceId }
                ?: house.devices.firstOrNull()
            val roomName = activeDevice?.let { dev ->
                house.rooms.find { it.id == dev.roomId }?.name
            } ?: house.rooms.firstOrNull()?.name ?: "Casa"

            val snapshot: DeviceSnapshot? = if (activeDevice != null) {
                runCatching { service.getCachedSnapshot(activeDevice.id) }.getOrNull()
            } else null

            for (widgetId in ids) {
                val views = buildRemoteViews(
                    context = context,
                    isDark = isDark,
                    roomName = roomName,
                    device = activeDevice,
                    totalDevicesCount = house.devices.size,
                    snapshot = snapshot,
                    statusOverride = statusOverride
                )
                mgr.updateAppWidget(widgetId, views)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            isDark: Boolean,
            roomName: String,
            device: DeviceProfile?,
            totalDevicesCount: Int,
            snapshot: DeviceSnapshot?,
            statusOverride: String?
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.luma_bulb_widget)

            // Theme colors matching Luma Light & Dark palettes
            val inkColor = if (isDark) Color.parseColor("#F3EEF8") else Color.parseColor("#302B3A")
            val mutedColor = if (isDark) Color.parseColor("#A599B0") else Color.parseColor("#7F7482")
            val eyebrowColor = if (isDark) Color.parseColor("#91849E") else Color.parseColor("#9991A1")
            val pillTextColor = if (isDark) Color.parseColor("#DFD3E8") else Color.parseColor("#735A7B")
            val cardBgRes = if (isDark) R.drawable.bg_luma_widget_card_dark else R.drawable.bg_luma_widget_card
            val pillBgRes = if (isDark) R.drawable.bg_luma_widget_pill_dark else R.drawable.bg_luma_widget_pill

            views.setInt(R.id.widget_root, "setBackgroundResource", cardBgRes)
            views.setInt(R.id.widget_status_pill, "setBackgroundResource", pillBgRes)
            views.setInt(R.id.widget_btn_next_device, "setBackgroundResource", pillBgRes)
            views.setInt(R.id.widget_btn_bri_down, "setBackgroundResource", pillBgRes)
            views.setInt(R.id.widget_btn_bri_up, "setBackgroundResource", pillBgRes)
            views.setInt(R.id.widget_scene_relax, "setBackgroundResource", pillBgRes)
            views.setInt(R.id.widget_scene_focus, "setBackgroundResource", pillBgRes)
            views.setInt(R.id.widget_scene_sunset, "setBackgroundResource", pillBgRes)
            views.setInt(R.id.widget_scene_dream, "setBackgroundResource", pillBgRes)

            views.setTextColor(R.id.widget_eyebrow, eyebrowColor)
            views.setTextColor(R.id.widget_device_name, inkColor)
            views.setTextColor(R.id.widget_state_headline, inkColor)
            views.setTextColor(R.id.widget_state_subline, mutedColor)
            views.setTextColor(R.id.widget_status_text, mutedColor)
            views.setTextColor(R.id.widget_btn_next_device, pillTextColor)
            views.setTextColor(R.id.widget_btn_bri_down, inkColor)
            views.setTextColor(R.id.widget_btn_bri_up, inkColor)
            views.setTextColor(R.id.widget_scene_relax, pillTextColor)
            views.setTextColor(R.id.widget_scene_focus, pillTextColor)
            views.setTextColor(R.id.widget_scene_sunset, pillTextColor)
            views.setTextColor(R.id.widget_scene_dream, pillTextColor)

            // Open app intent on title area click
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPending = PendingIntent.getActivity(
                context,
                100,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_open_app_area, openAppPending)

            if (device == null) {
                views.setTextViewText(R.id.widget_eyebrow, "LUMA PER TAPO")
                views.setTextViewText(R.id.widget_device_name, "Nessuna lampadina")
                views.setTextViewText(R.id.widget_status_text, "Da configurare")
                views.setInt(R.id.widget_status_dot, "setColorFilter", Color.parseColor("#B2AAB8"))
                views.setViewVisibility(R.id.widget_btn_next_device, View.GONE)
                views.setTextViewText(R.id.widget_state_headline, "Tocca per aggiungere una luce")
                views.setTextViewText(R.id.widget_state_subline, "Scopri le lampadine sulla tua rete Wi-Fi")
                views.setTextViewText(R.id.widget_power_text, "Apri")
                views.setOnClickPendingIntent(R.id.widget_btn_power, openAppPending)
                return views
            }

            views.setTextViewText(R.id.widget_eyebrow, "LUMA · ${roomName.uppercase()}")
            views.setTextViewText(R.id.widget_device_name, device.name)
            views.setViewVisibility(
                R.id.widget_btn_next_device,
                if (totalDevicesCount > 1) View.VISIBLE else View.GONE
            )

            val info = snapshot?.info
            val isOn = info?.deviceOn == true
            val bri = info?.brightness ?: 72
            val ct = info?.colorTemp ?: 3200
            val hue = info?.hue ?: 30
            val sat = info?.saturation ?: 85

            val statusLabel = statusOverride ?: if (snapshot?.connected == true) "Connessa" else "Pronta"
            val dotColor = when {
                statusOverride != null -> Color.parseColor("#D6A87F")
                snapshot?.connected == true -> Color.parseColor("#76B895")
                else -> Color.parseColor("#A27EAC")
            }
            views.setTextViewText(R.id.widget_status_text, statusLabel)
            views.setInt(R.id.widget_status_dot, "setColorFilter", dotColor)

            // State headline & subline
            if (isOn) {
                views.setTextViewText(R.id.widget_state_headline, "Accesa · $bri%")
                val modeDesc = if (ct > 0) {
                    val label = when {
                        ct < 3500 -> "Bianco caldo"
                        ct <= 5000 -> "Bianco naturale"
                        else -> "Bianco freddo"
                    }
                    "$label · $ct K"
                } else {
                    "Colore · ${hue}° · $sat%"
                }
                views.setTextViewText(R.id.widget_state_subline, modeDesc)

                val rgb = if (ct > 0) LumaColorMath.kelvinToRgb(ct) else LumaColorMath.hsvToRgb(hue, sat)
                val bulbTint = Color.rgb(rgb.first, rgb.second, rgb.third)
                views.setInt(R.id.widget_bulb_icon, "setColorFilter", bulbTint)
                views.setInt(R.id.widget_btn_power, "setBackgroundResource", R.drawable.bg_luma_widget_power_on)
                views.setTextViewText(R.id.widget_power_text, "Spegni")
                views.setTextColor(R.id.widget_power_text, Color.WHITE)
                views.setInt(R.id.widget_power_icon, "setColorFilter", Color.WHITE)
            } else {
                views.setTextViewText(R.id.widget_state_headline, "Spenta · ${device.model.uppercase()}")
                views.setTextViewText(R.id.widget_state_subline, "IP ${device.ip} · Tocca Accendi")
                views.setInt(R.id.widget_bulb_icon, "setColorFilter", Color.WHITE)
                views.setInt(R.id.widget_btn_power, "setBackgroundResource", R.drawable.bg_luma_widget_power_off)
                views.setTextViewText(R.id.widget_power_text, "Accendi")
                views.setTextColor(R.id.widget_power_text, Color.parseColor("#6E5475"))
                views.setInt(R.id.widget_power_icon, "setColorFilter", Color.parseColor("#6E5475"))
            }

            // Wire broadcast PendingIntents for instant Home Screen control
            views.setOnClickPendingIntent(R.id.widget_btn_power, actionPendingIntent(context, ACTION_TOGGLE_POWER, 201))
            views.setOnClickPendingIntent(R.id.widget_btn_bri_down, actionPendingIntent(context, ACTION_BRI_DOWN, 202))
            views.setOnClickPendingIntent(R.id.widget_btn_bri_up, actionPendingIntent(context, ACTION_BRI_UP, 203))
            views.setOnClickPendingIntent(R.id.widget_scene_relax, actionPendingIntent(context, ACTION_SCENE_RELAX, 204))
            views.setOnClickPendingIntent(R.id.widget_scene_focus, actionPendingIntent(context, ACTION_SCENE_FOCUS, 205))
            views.setOnClickPendingIntent(R.id.widget_scene_sunset, actionPendingIntent(context, ACTION_SCENE_SUNSET, 206))
            views.setOnClickPendingIntent(R.id.widget_scene_dream, actionPendingIntent(context, ACTION_SCENE_DREAM, 207))
            views.setOnClickPendingIntent(R.id.widget_btn_next_device, actionPendingIntent(context, ACTION_NEXT_DEVICE, 208))
            views.setOnClickPendingIntent(R.id.widget_status_pill, actionPendingIntent(context, ACTION_REFRESH, 209))

            return views
        }

        private fun actionPendingIntent(context: Context, action: String, reqCode: Int): PendingIntent {
            val intent = Intent(context, LumaBulbWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (!action.startsWith("it.luma.tapo.widget.ACTION_")) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        widgetScope.launch {
            try {
                handleWidgetAction(appContext, action)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleWidgetAction(context: Context, action: String) {
        val service = LumaService(context)
        val house = service.bootstrap()
        if (house.devices.isEmpty()) {
            renderWidgetsInternal(context)
            return
        }

        val savedId = service.getSavedDeviceId()
        var targetDevice = house.devices.find { it.id == savedId } ?: house.devices.first()

        if (action == ACTION_NEXT_DEVICE) {
            val currentIdx = house.devices.indexOfFirst { it.id == targetDevice.id }.coerceAtLeast(0)
            val nextDevice = house.devices[(currentIdx + 1) % house.devices.size]
            service.saveSelection(nextDevice.roomId, nextDevice.id)
            targetDevice = nextDevice
            renderWidgetsInternal(context, statusOverride = "Cambio…")
            runCatching { service.activateDevice(targetDevice.id) }
            renderWidgetsInternal(context)
            return
        }

        renderWidgetsInternal(context, statusOverride = "Invio…")

        try {
            val cached = service.getCachedSnapshot(targetDevice.id)
            val activeSnap = if (cached != null && cached.session.isNotEmpty()) {
                cached
            } else {
                service.activateDevice(targetDevice.id).snapshot
            }
            val token = activeSnap.session
            val currentInfo = activeSnap.info

            when (action) {
                ACTION_TOGGLE_POWER -> {
                    val nextOn = !(currentInfo?.deviceOn ?: false)
                    service.sendCommand(
                        path = "/api/power",
                        deviceId = targetDevice.id,
                        session = token,
                        powerState = nextOn
                    )
                }
                ACTION_BRI_DOWN -> {
                    val nextBri = ((currentInfo?.brightness ?: 70) - 20).coerceIn(5, 100)
                    service.sendCommand(
                        path = "/api/brightness",
                        deviceId = targetDevice.id,
                        session = token,
                        brightness = nextBri
                    )
                }
                ACTION_BRI_UP -> {
                    val nextBri = ((currentInfo?.brightness ?: 70) + 20).coerceIn(5, 100)
                    service.sendCommand(
                        path = "/api/brightness",
                        deviceId = targetDevice.id,
                        session = token,
                        brightness = nextBri
                    )
                }
                ACTION_SCENE_RELAX -> applySceneFromWidget(service, targetDevice, token, LumaPresets.SCENES[0])
                ACTION_SCENE_FOCUS -> applySceneFromWidget(service, targetDevice, token, LumaPresets.SCENES[1])
                ACTION_SCENE_SUNSET -> applySceneFromWidget(service, targetDevice, token, LumaPresets.SCENES[2])
                ACTION_SCENE_DREAM -> applySceneFromWidget(service, targetDevice, token, LumaPresets.SCENES[3])
                ACTION_REFRESH -> {
                    service.activateDevice(targetDevice.id)
                }
            }
            renderWidgetsInternal(context)
        } catch (_: Exception) {
            renderWidgetsInternal(context, statusOverride = "Offline")
        }
    }

    private suspend fun applySceneFromWidget(
        service: LumaService,
        device: DeviceProfile,
        token: String,
        scene: ScenePreset
    ) {
        val caps = service.caps(device.model)
        service.sendCommand(
            path = "/api/brightness",
            deviceId = device.id,
            session = token,
            brightness = scene.bri
        )
        if (scene.mode == LightMode.COLOR && caps.color) {
            service.sendCommand(
                path = "/api/color_hsv",
                deviceId = device.id,
                session = token,
                hue = scene.hue,
                saturation = scene.sat
            )
        } else if (caps.ct) {
            service.sendCommand(
                path = "/api/color_temp",
                deviceId = device.id,
                session = token,
                colorTemp = scene.ct.coerceIn(2500, 6500)
            )
        }
    }
}
