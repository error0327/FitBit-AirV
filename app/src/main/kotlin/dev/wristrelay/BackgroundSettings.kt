package dev.wristrelay

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast

internal class BackgroundOptions(private val activity: Activity, private val ui: DarkInstrument) {
    private val notificationStatus = ui.label("", 13, ui.muted)
    private val notificationAction = ui.action("Open notification access") { openNotificationAccess() }
    private val batteryAction = ui.action("Open Android battery settings") { openBatterySettings() }

    val view = ui.column().apply {
        addView(ui.label("Notification access", 18, bold = true).asHeading())
        addView(notificationStatus, ui.stacked(3))
        addView(ui.label("Lets Fitbit Vib recognize alerts from the apps you allow and relay them to your band.", 14, ui.muted), ui.stacked(6))
        addView(notificationAction, ui.stacked(8))
        addView(View(activity), LinearLayout.LayoutParams(1, ui.dp(14)))
        ui.addDivider(this)
        addView(ui.label("Battery optimization", 18, bold = true).asHeading(), ui.stacked(14))
        addView(ui.label("If alerts are delayed with the screen off, open Battery or Battery usage in Android’s app info for Fitbit Vib.", 14, ui.muted), ui.stacked(6))
        addView(batteryAction, ui.stacked(8))
    }

    fun refresh() {
        val notificationGranted = Preferences.hasAccess(activity)
        notificationStatus.text = if (notificationGranted) "Access granted" else "Access required"
        notificationStatus.setTextColor(if (notificationGranted) ui.cyan else ui.muted)
    }

    private fun openNotificationAccess() {
        val component = ComponentName(activity, RelayListener::class.java)
        open(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString()),
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        )
    }

    private fun openBatterySettings() {
        open(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    private fun open(vararg destinations: Intent) {
        for (intent in destinations) {
            try {
                activity.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                continue
            } catch (_: SecurityException) {
                continue
            }
        }
        Toast.makeText(activity, "Android settings are unavailable on this device.", Toast.LENGTH_SHORT).show()
    }

    private fun View.asHeading() = apply { isAccessibilityHeading = true }
}

class BackgroundSettingsActivity : Activity() {
    private lateinit var options: BackgroundOptions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = DarkInstrument(this)
        val panel = ui.column().apply { setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(24)) }
        val heading = ui.row()
        heading.addView(ui.label("Settings", 32, bold = true).apply { isAccessibilityHeading = true },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(8) })
        heading.addView(ui.action("Back") { finish() }, LinearLayout.LayoutParams(-2, -2))
        panel.addView(heading, ui.stacked())
        options = BackgroundOptions(this, ui)
        val card = ui.card().apply { addView(options.view, ui.stacked()) }
        panel.addView(card, ui.stacked(16))
        val about = ui.card().apply {
            addView(ui.label("About", 18, bold = true).apply { isAccessibilityHeading = true })
            addView(ui.label("Developed by Obada abu alhiga", 14, ui.muted), ui.stacked(6))
            addView(ui.action("View on GitHub") { openProjectPage() }, ui.stacked(8))
        }
        panel.addView(about, ui.stacked(16))
        setContentView(ui.screen(panel))
    }

    override fun onResume() {
        super.onResume()
        options.refresh()
    }

    private fun openProjectPage() {
        val projectPage = Uri.parse("https://github.com/error0327/fitbit-vib")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, projectPage))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app can open the GitHub link.", Toast.LENGTH_SHORT).show()
        }
    }
}
