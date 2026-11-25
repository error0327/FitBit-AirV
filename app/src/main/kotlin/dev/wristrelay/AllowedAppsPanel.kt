package dev.wristrelay

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import java.text.Collator

internal class AllowedAppsPanel(private val context: Context, private val ui: DarkInstrument) {
    private data class App(val id: String, val label: String)

    private val rows = ui.column()
    private val count = ui.label("", 13, ui.muted)
    private val preferences = Preferences.store(context)
    private val collator = Collator.getInstance()
    private var installed = emptyMap<String, App>()
    private var query = ""
    private var activeOnly = preferences.getBoolean("active_notifications_filter", false)
    private val allowAll = ui.action("Allow all") {
        Preferences.setAllowed(context, Preferences.allowed(context) + installed.keys)
        showApps()
    }

    val view = ui.card().apply {
        val heading = ui.row()
        val titles = ui.column()
        titles.addView(ui.label("Allowed apps", 23, bold = true).apply { isAccessibilityHeading = true })
        titles.addView(count)
        heading.addView(titles, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(8) })
        heading.addView(allowAll, LinearLayout.LayoutParams(-2, -2))
        addView(heading, ui.stacked())
        addView(ui.toggle("Active now", activeOnly) { checked ->
            activeOnly = checked
            preferences.edit().putBoolean("active_notifications_filter", checked).apply()
            showApps()
        }, ui.stacked(4))
        addView(searchField(), ui.stacked(4))
        addView(rows, ui.stacked(8))
    }

    fun resume() {
        installed = context.getSystemService(LauncherApps::class.java).getActivityList(null, Process.myUserHandle())
            .map { App(it.applicationInfo.packageName, it.label.toString()) }
            .filter { it.id != context.packageName }
            .distinctBy { it.id }
            .associateBy { it.id }
        ActiveNotificationApps.observe { if (activeOnly) showApps() }
        showApps()
    }

    fun pause() {
        ActiveNotificationApps.observe(null)
    }

    private fun searchField() = EditText(context).apply {
        hint = "Search apps"
        isSingleLine = true
        textSize = 16f
        minHeight = ui.dp(48)
        background = ui.searchBackground()
        setTextColor(ui.ink)
        setHintTextColor(ui.muted)
        setPaddingRelative(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
        setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
        compoundDrawablePadding = ui.dp(10)
        imeOptions = EditorInfo.IME_ACTION_DONE
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString()?.trim().orEmpty()
                showApps()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun showApps() {
        val allowed = Preferences.allowed(context)
        updateCount(allowed)
        rows.removeAllViews()
        val hasAccess = activeOnly && Preferences.hasAccess(context)
        val active = if (hasAccess) ActiveNotificationApps.counts else null
        val ids = if (activeOnly) active.orEmpty().keys else installed.keys + allowed
        val byName = compareBy<App, String>(collator) { it.label }
        val order = if (activeOnly) compareByDescending<App> { it.id in allowed }.then(byName) else byName
        val visible = ids.map(::findApp)
            .filter { it.label.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true) }
            .sortedWith(order)
        if (visible.isEmpty()) {
            val message = when {
                activeOnly && !hasAccess -> "Allow notification access in Settings to see active apps."
                activeOnly && active == null -> "Connecting to Android notifications…"
                query.isNotEmpty() -> "No apps match your search"
                activeOnly -> "No apps have active notifications."
                else -> "No apps found"
            }
            rows.addView(ui.label(message, 15, ui.muted), ui.stacked(8))
            return
        }
        for ((index, app) in visible.withIndex()) {
            if (index > 0) ui.addDivider(rows)
            rows.addView(appRow(app, app.id in allowed, active?.get(app.id)), ui.stacked())
        }
    }

    @Suppress("DEPRECATION")
    private fun findApp(id: String): App = installed[id] ?: App(id, try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(id, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        id
    })

    private fun appRow(app: App, allowed: Boolean, activeCount: Int?) = ui.row().apply {
        val icon = ImageView(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            val pm = context.packageManager
            setImageDrawable(runCatching { pm.getApplicationIcon(app.id) }.getOrNull() ?: pm.defaultActivityIcon)
        }
        addView(icon, LinearLayout.LayoutParams(ui.dp(30), ui.dp(30)).apply { marginEnd = ui.dp(12) })
        val toggle = ui.toggle(appTitle(app, activeCount), allowed) { checked ->
            val current = Preferences.allowed(context)
            val updated = if (checked) current + app.id else current - app.id
            Preferences.setAllowed(context, updated)
            updateCount(updated)
        }
        addView(toggle, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun appTitle(app: App, activeCount: Int?): CharSequence {
        if (activeCount == null) return app.label
        val detail = if (activeCount == 1) "1 active notification" else "$activeCount active notifications"
        return SpannableStringBuilder(app.label).append('\n').apply {
            val start = length
            append(detail)
            setSpan(ForegroundColorSpan(ui.muted), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(0.8f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun updateCount(allowed: Set<String>) {
        count.text = if (allowed.size == 1) "1 app enabled" else "${allowed.size} apps enabled"
        allowAll.isEnabled = installed.keys.any { it !in allowed }
    }
}
