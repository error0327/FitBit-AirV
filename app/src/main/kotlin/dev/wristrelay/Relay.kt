package dev.wristrelay

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.airpulse.fitbit_air.FitbitAirPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

internal object Preferences {
    fun store(context: Context) = context.getSharedPreferences("wrist_relay", Context.MODE_PRIVATE)
    fun enabled(context: Context) = store(context).getBoolean("enabled", true)
    fun allowed(context: Context): Set<String> = store(context).getStringSet("allowed_apps", emptySet()).orEmpty()
    fun setAllowed(context: Context, apps: Set<String>) {
        store(context).edit().putStringSet("allowed_apps", apps).apply()
    }
    fun hasAccess(context: Context) = context.getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(ComponentName(context, RelayListener::class.java))
}

internal fun hasBluetooth(context: Context) =
    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

internal fun hasBondedAir(context: Context): Boolean {
    if (!hasBluetooth(context)) return false
    return try {
        context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty().count {
            it.bondState == BluetoothDevice.BOND_BONDED && it.name in setOf("Google Fitbit Air", "Fitbit Air")
        } == 1
    } catch (_: SecurityException) {
        false
    }
}

internal object Pulse {
    private const val REST_MILLIS = 5_000L
    private const val EXPIRY_MILLIS = 25_000L
    private data class Pending(val context: Context, val packageName: String, val postedAt: Long)
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Pending? = null
    private val dispatch = Runnable { dispatchPending() }

    var running = false
        private set
    var status: String? = null
        private set
    private var lastFinishedAt: Long? = null
    private var transport: FitbitAirPlugin? = null
    private var observer: (() -> Unit)? = null

    fun observe(callback: (() -> Unit)?) {
        observer = callback
    }

    fun enqueue(context: Context, packageName: String) {
        pending = Pending(context.applicationContext, packageName, SystemClock.elapsedRealtime())
        dispatchPending()
    }

    private fun dispatchPending() {
        handler.removeCallbacks(dispatch)
        if (running) return
        val waiting = pending ?: return
        val now = SystemClock.elapsedRealtime()
        val delay = lastFinishedAt?.let { REST_MILLIS - (now - it) } ?: 0
        if (delay > 0) {
            handler.postDelayed(dispatch, delay)
            return
        }
        pending = null
        if (now - waiting.postedAt < EXPIRY_MILLIS &&
            Preferences.enabled(waiting.context) && Preferences.hasAccess(waiting.context) &&
            waiting.packageName in Preferences.allowed(waiting.context)) {
            send(waiting.context)
        }
    }

    fun send(context: Context) {
        if (running) return
        if (!hasBondedAir(context)) {
            status = "No paired Fitbit Air available"
            observer?.invoke()
            return
        }
        running = true
        status = "Sending vibration…"
        observer?.invoke()
        try {
            val client = transport ?: FitbitAirPlugin().also {
                val messenger = object : BinaryMessenger {}
                it.onAttachedToEngine(FlutterPlugin.FlutterPluginBinding(context.applicationContext, messenger))
                transport = it
            }
            client.onMethodCall(MethodCall("tap", null), object : MethodChannel.Result {
                override fun success(result: Any?) = finish("Vibration sent")
                override fun error(code: String, message: String?, details: Any?) {
                    finish(when (code) {
                        "TRACKER_BUSY", "HANDSHAKE_TIMEOUT" -> "Tracker busy or unavailable. Try after Google Health syncs."
                        "AIR_NOT_FOUND" -> "No paired Fitbit Air available"
                        else -> "Vibration could not be verified"
                    })
                }
                override fun notImplemented() = finish("Vibration unavailable")
            })
        } catch (_: RuntimeException) {
            finish("Could not start the Bluetooth connection")
        } catch (_: LinkageError) {
            finish("Bluetooth transport unavailable on this device")
        }
    }

    private fun finish(message: String) {
        running = false
        lastFinishedAt = SystemClock.elapsedRealtime()
        status = message
        observer?.invoke()
        dispatchPending()
    }
}

class RelayListener : NotificationListenerService() {
    private val seen = LinkedHashMap<String, Long>()

    override fun onListenerConnected() {
        seen.clear()
        val current = activeNotifications.orEmpty().filter { it.user == Process.myUserHandle() }
        current.forEach { remember(it) }
        ActiveNotificationApps.connect(current
            .filter { it.packageName != packageName && !it.isGroupSummary() }
            .associate { it.key to it.packageName })
    }

    override fun onListenerDisconnected() {
        ActiveNotificationApps.disconnect()
    }

    override fun onDestroy() {
        ActiveNotificationApps.disconnect()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.user != Process.myUserHandle() || sbn.packageName == packageName) return
        val notification = sbn.notification
        if (sbn.isGroupSummary()) {
            ActiveNotificationApps.remove(sbn.key)
            return
        }
        ActiveNotificationApps.post(sbn.key, sbn.packageName)
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0 &&
            notification.category != Notification.CATEGORY_CALL) return
        val previous = remember(sbn)
        if (previous == sbn.postTime || (previous != null &&
            notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)) return
        if (!Preferences.enabled(this) || !Preferences.hasAccess(this)) return
        if (sbn.packageName in Preferences.allowed(this)) Pulse.enqueue(this, sbn.packageName)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.user != Process.myUserHandle()) return
        seen.remove(sbn.key)
        ActiveNotificationApps.remove(sbn.key)
    }

    private fun StatusBarNotification.isGroupSummary() =
        notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

    private fun remember(sbn: StatusBarNotification): Long? {
        val previous = seen.remove(sbn.key)
        seen[sbn.key] = sbn.postTime
        if (seen.size > 512) seen.remove(seen.keys.first())
        return previous
    }
}
