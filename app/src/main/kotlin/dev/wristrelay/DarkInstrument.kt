package dev.wristrelay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

internal class DarkInstrument(private val context: Context) {
    val ink = color(R.color.vib_ink)
    val muted = color(R.color.vib_muted)
    val cyan = color(R.color.vib_cyan)
    private val surface = color(R.color.vib_surface)
    private val field = color(R.color.vib_field)
    private val divider = color(R.color.vib_divider)
    private val disabled = color(R.color.vib_disabled)

    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun row() = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }

    fun card() = column().apply {
        background = shape(surface, 19)
        setPadding(dp(15), dp(12), dp(15), dp(12))
    }

    fun screen(content: View) = FrameLayout(context).apply {
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(content)
        }
        addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsets.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, maxOf(safe.bottom, keyboard.bottom))
            WindowInsets.CONSUMED
        }
    }

    fun label(value: CharSequence, size: Int, textColor: Int = ink, bold: Boolean = false) =
        TextView(context).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(textColor)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    fun action(title: CharSequence, outlined: Boolean = false, handler: () -> Unit) =
        Button(context).apply {
            text = title
            textSize = 14f
            isAllCaps = false
            minHeight = dp(48)
            minimumWidth = 0
            minWidth = 0
            letterSpacing = 0.01f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setTextColor(states(cyan, cyan, disabled))
            val background = if (outlined) Color.TRANSPARENT else field
            val base = StateListDrawable().apply {
                addState(intArrayOf(-android.R.attr.state_enabled), shape(background, 12, if (outlined) divider else null))
                addState(intArrayOf(), shape(background, 12, cyan.takeIf { outlined }))
            }
            this.background = RippleDrawable(ColorStateList.valueOf(0x225BE5F3), base, shape(Color.WHITE, 12))
            setOnClickListener { handler() }
        }

    fun toggle(title: CharSequence, selected: Boolean, onChange: (Boolean) -> Unit) =
        Switch(context).apply {
            text = title
            textSize = 16f
            setTextColor(ink)
            minHeight = dp(50)
            switchMinWidth = dp(48)
            switchPadding = dp(14)
            trackDrawable = shape(Color.WHITE, 12).apply { setSize(dp(48), dp(24)) }
            thumbDrawable = InsetDrawable(shape(Color.WHITE, 9).apply { setSize(dp(18), dp(18)) }, dp(3))
            trackTintList = states(cyan, color(R.color.vib_track_off), divider)
            thumbTintList = states(Color.WHITE, Color.WHITE, muted)
            isChecked = selected
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }

    fun addDivider(parent: LinearLayout) {
        parent.addView(View(context).apply { setBackgroundColor(divider) }, LinearLayout.LayoutParams(-1, dp(1)))
    }

    fun searchBackground() = shape(field, 12, divider)

    fun stacked(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }

    fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun color(id: Int) = context.getColor(id)

    private fun shape(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun states(checked: Int, normal: Int, off: Int) = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(off, checked, normal)
    )
}
