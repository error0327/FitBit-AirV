package dev.wristrelay

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var ui: DarkInstrument
    private lateinit var allowedApps: AllowedAppsPanel
    private lateinit var trackerStatus: TextView
    private lateinit var testStatus: TextView
    private lateinit var bluetooth: Button
    private lateinit var test: Button
    private var setupDialog: AlertDialog? = null
    private var setupOptions: BackgroundOptions? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = DarkInstrument(this)
        val panel = ui.column()
        panel.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(24))
        panel.isFocusableInTouchMode = true
        setContentView(ui.screen(panel))
        panel.requestFocus()
        val heading = ui.row()
        heading.addView(ui.label(getText(R.string.app_name), 34, bold = true).apply { isAccessibilityHeading = true },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(8) })
        heading.addView(ui.action("Settings") {
            startActivity(Intent(this, BackgroundSettingsActivity::class.java))
        }, LinearLayout.LayoutParams(-2, -2))
        panel.addView(heading, ui.stacked())
        panel.addView(trackerPanel(), ui.stacked(17))
        test = ui.action("Send test vibration", outlined = true) { Pulse.send(this) }
        panel.addView(test, ui.stacked(14))
        testStatus = ui.label("", 14, ui.muted).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        panel.addView(testStatus, ui.stacked(6))
        allowedApps = AllowedAppsPanel(this, ui)
        panel.addView(allowedApps.view, ui.stacked(14))
    }

    private fun trackerPanel() = ui.card().apply {
        val device = ui.row()
        val picture = ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.fitbit_air_status)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        device.addView(picture, LinearLayout.LayoutParams(ui.dp(88), ui.dp(88)).apply { marginEnd = ui.dp(12) })
        val deviceDetails = ui.column()
        deviceDetails.addView(ui.label("Fitbit Air", 27, bold = true))
        trackerStatus = ui.label("", 15, ui.muted)
        deviceDetails.addView(trackerStatus, ui.stacked(2))
        device.addView(deviceDetails, LinearLayout.LayoutParams(0, -2, 1f))
        addView(device, ui.stacked())
        bluetooth = ui.action("Allow nearby devices") { requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 7) }
        addView(bluetooth, ui.stacked(6))
        ui.addDivider(this)

        addView(ui.toggle("Relay notifications", Preferences.enabled(this@MainActivity)) { value ->
            Preferences.store(this@MainActivity).edit().putBoolean("enabled", value).apply()
        }, ui.stacked())
    }

    override fun onResume() {
        super.onResume()
        Pulse.observe { showSetup() }
        allowedApps.resume()
        showSetup()
        showBackgroundSetup()
    }

    override fun onPause() {
        Pulse.observe(null)
        allowedApps.pause()
        super.onPause()
    }

    override fun onDestroy() {
        setupDialog?.dismiss()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        showSetup()
    }

    private fun showBackgroundSetup() {
        setupOptions?.let {
            it.refresh()
            return
        }
        if (Preferences.store(this).getBoolean("background_setup_seen", false)) return
        val options = BackgroundOptions(this, ui)
        setupOptions = options
        options.refresh()
        val scroll = ScrollView(this).apply {
            options.view.setPadding(ui.dp(24), ui.dp(12), ui.dp(24), ui.dp(8))
            addView(options.view)
        }
        setupDialog = AlertDialog.Builder(this)
            .setTitle("Set up background alerts")
            .setView(scroll)
            .setPositiveButton("Done") { _, _ -> finishBackgroundSetup() }
            .setOnCancelListener { finishBackgroundSetup() }
            .setOnDismissListener {
                setupOptions = null
                setupDialog = null
            }
            .show()
    }

    private fun finishBackgroundSetup() {
        Preferences.store(this).edit().putBoolean("background_setup_seen", true).apply()
    }

    private fun showSetup() {
        val nearby = hasBluetooth(this)
        val paired = hasBondedAir(this)
        bluetooth.visibility = if (nearby) View.GONE else View.VISIBLE
        test.isEnabled = paired && !Pulse.running
        trackerStatus.text = when {
            paired -> "Paired"
            !nearby -> "Nearby devices access required"
            else -> "Pair in Google Health"
        }
        trackerStatus.setTextColor(if (paired) ui.cyan else ui.muted)
        testStatus.text = Pulse.status.orEmpty()
        testStatus.visibility = if (Pulse.status == null) View.GONE else View.VISIBLE
    }
}
