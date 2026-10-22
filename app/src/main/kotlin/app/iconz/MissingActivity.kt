package app.iconz

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.time.LocalDate

/**
 * Apps on this phone that Iconz has no icon for. Green rows are already drawn (their package
 * is in icons.json) and only need their launcher activity added: share the list to the Mac
 * and run `python3 tool/build_icons.py --merge missing.txt`.
 */
class MissingActivity : Activity() {
    private class App(
        val label: String,
        val component: String,
        val drawable: String?,
        val icon: Drawable,
    ) {
        val line get() = "${drawable ?: "-"} $component # $label"
    }

    private lateinit var apps: List<App>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themed = IconCatalog.themedComponents(this)
        val byPackage = IconCatalog.drawableForPackage(this)
        @Suppress("DEPRECATION")
        val launchable = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        ).filter { it.activityInfo.packageName != packageName }
        apps = launchable.map {
            val pkg = it.activityInfo.packageName
            App(it.loadLabel(packageManager).toString(), "$pkg/${it.activityInfo.name}", byPackage[pkg], it.loadIcon(packageManager))
        }.filter { it.component !in themed }
            .sortedWith(compareBy<App>({ it.drawable == null }, { it.label.lowercase() }))
        val ready = apps.count { it.drawable != null }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.BG)
            setPadding(dp(16), dp(20), dp(16), 0)
            padForSystemBars()
        }
        root.addView(label("Missing icons", 24f, bold = true))
        root.addView(
            label(
                "${apps.size} of ${launchable.size} apps have no Iconz icon. $ready are already drawn " +
                    "(green) and only need mapping. Tap a row to copy it.",
                13f, Palette.MUTED,
            ).apply { setPadding(0, dp(4), 0, dp(12)) },
        )
        root.addView(pill("Share list") { share() }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(8) })
        root.addView(
            ListView(this).apply {
                divider = null
                adapter = Rows()
                setOnItemClickListener { _, _, pos, _ -> copy(apps[pos].line) }
            },
            LinearLayout.LayoutParams(-1, 0, 1f),
        )
        setContentView(root)
    }

    private fun copy(line: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Iconz", line))
        toast("Copied")
    }

    private fun share() {
        val text = buildString {
            appendLine("# Iconz missing icons, ${LocalDate.now()}")
            appendLine("# <drawable or -> <package/activity> # <app name>")
            apps.forEach { appendLine(it.line) }
        }
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    .putExtra(Intent.EXTRA_SUBJECT, "missing.txt"),
                "Share missing icons",
            ),
        )
    }

    private inner class Rows : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView as? LinearLayout ?: LinearLayout(this@MissingActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
                addView(ImageView(context), LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(label("", 15f))
                        addView(label("", 11f, Palette.MUTED).apply { isSingleLine = true })
                    },
                    LinearLayout.LayoutParams(0, -2, 1f),
                )
                addView(label("", 12f, bold = true).apply { setPadding(dp(8), 0, 0, 0) })
            }
            val app = apps[position]
            (row.getChildAt(0) as ImageView).setImageDrawable(app.icon)
            val texts = row.getChildAt(1) as LinearLayout
            (texts.getChildAt(0) as TextView).text = app.label
            (texts.getChildAt(1) as TextView).text = app.component
            (row.getChildAt(2) as TextView).apply {
                text = if (app.drawable != null) "drawn" else "to draw"
                setTextColor(if (app.drawable != null) Palette.ACCENT else Palette.MUTED)
            }
            return row
        }
    }
}
