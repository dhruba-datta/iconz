package app.iconz

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import android.widget.Toast

// The app has no AndroidX dependency: plain framework views, built in code.

object Palette {
    const val BG = 0xFF0E1116.toInt()
    const val CARD = 0xFF1A1F27.toInt()
    const val TEXT = 0xFFF1F3F5.toInt()
    const val MUTED = 0xFF9AA4B2.toInt()
    const val ACCENT = 0xFF2FC28A.toInt()
}

fun Context.dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density + 0.5f).toInt()

fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius.toFloat()
}

fun Context.label(
    text: CharSequence,
    sizeSp: Float = 15f,
    color: Int = Palette.TEXT,
    bold: Boolean = false,
) = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(color)
    if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

fun Context.pill(text: String, onClick: () -> Unit) = label(text, 14f, bold = true).apply {
    background = rounded(Palette.CARD, dp(18))
    setPadding(dp(16), dp(9), dp(16), dp(9))
    setOnClickListener { onClick() }
}

fun Context.dialog() = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

/** Android 15+ always draws apps edge to edge: keep content clear of the system bars. */
fun View.padForSystemBars() {
    if (Build.VERSION.SDK_INT < 35) return
    val (l, t, r, b) = listOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
    setOnApplyWindowInsetsListener { v, insets ->
        val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
        v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom)
        insets
    }
}

val Activity.isGone get() = isFinishing || isDestroyed
