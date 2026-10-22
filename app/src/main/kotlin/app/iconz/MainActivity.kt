package app.iconz

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.NumberFormat

/** The icon gallery: Bangladesh or all icons, search, Apply and About. */
class MainActivity : Activity() {
    private lateinit var catalog: Catalog
    private var tab: List<Icon> = emptyList()
    private var shown: List<Icon> = emptyList()
    private var query = ""
    private lateinit var adapter: BaseAdapter
    private lateinit var tabs: List<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        catalog = IconCatalog.load(this)
        val n = NumberFormat.getIntegerInstance()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.BG)
            setPadding(dp(16), dp(20), dp(16), 0)
            padForSystemBars()
        }
        root.addView(label(getString(R.string.app_name), 28f, bold = true))
        root.addView(
            label(
                "${n.format(catalog.all.size)} icons · ${n.format(catalog.appEntries)} apps · v${BuildConfig.VERSION_NAME}",
                13f, Palette.MUTED,
            ).apply { setPadding(0, dp(2), 0, dp(14)) },
        )

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun add(v: View) = actions.addView(v, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
        tabs = listOf(
            pill("Bangladesh") { select(0) },
            pill("All icons") { select(1) },
        )
        tabs.forEach(::add)
        add(pill("Apply") { Launchers.show(this) })
        add(pill("About") { about() })
        root.addView(android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(actions)
        })

        val search = EditText(this).apply {
            hint = "Search icons"
            setHintTextColor(Palette.MUTED)
            setTextColor(Palette.TEXT)
            textSize = 15f
            isSingleLine = true
            background = rounded(Palette.CARD, dp(12))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    query = s.toString().trim().lowercase()
                    refresh()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        root.addView(search, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14); bottomMargin = dp(10) })

        adapter = IconAdapter()
        root.addView(
            GridView(this).apply {
                numColumns = GridView.AUTO_FIT
                columnWidth = dp(84)
                stretchMode = GridView.STRETCH_COLUMN_WIDTH
                verticalSpacing = dp(6)
                clipToPadding = false
                setPadding(0, 0, 0, dp(16))
                isFastScrollEnabled = true
                adapter = this@MainActivity.adapter
                setOnItemClickListener { _, _, pos, _ -> preview(shown[pos]) }
            },
            LinearLayout.LayoutParams(-1, 0, 1f),
        )
        setContentView(root)
        select(0)
    }

    private fun select(index: Int) {
        tab = if (index == 0) catalog.bangladesh else catalog.all
        tabs.forEachIndexed { i, t ->
            t.background = rounded(if (i == index) Palette.ACCENT else Palette.CARD, dp(18))
            t.setTextColor(if (i == index) Palette.BG else Palette.TEXT)
        }
        refresh()
    }

    private fun refresh() {
        // Searching always looks through every icon.
        shown = if (query.isEmpty()) tab
        else (catalog.bangladesh + catalog.all).distinctBy { it.drawable }
            .filter { query in it.label.lowercase() || query in it.drawable }
        adapter.notifyDataSetChanged()
    }

    private fun preview(icon: Icon) {
        val big = ImageView(this).apply {
            setImageResource(IconCatalog.drawableId(this@MainActivity, icon.drawable))
            background = rounded(Palette.CARD, dp(28))
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        val box = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(20), dp(24), 0)
            addView(big, LinearLayout.LayoutParams(dp(144), dp(144)))
        }
        val apps = icon.packages.joinToString("\n")
        dialog().setTitle(icon.label).setView(box)
            .setMessage(if (apps.isEmpty()) "Drawable: ${icon.drawable}" else "$apps\n\nDrawable: ${icon.drawable}")
            .setPositiveButton("Close", null).show()
    }

    private fun about() {
        dialog().setTitle("About Iconz")
            .setMessage(
                "Iconz ${BuildConfig.VERSION_NAME}, a free line icon pack with Bangladeshi apps.\n\n" +
                    "Most icons are from Arcticons by Donnnno and the Arcticons contributors, licensed " +
                    "CC BY-SA 4.0. The Bangladeshi icons are drawn for Iconz and share the same licence. " +
                    "App names and logos belong to their owners.\n\n" +
                    "Iconz asks for no permissions and has no internet access.",
            )
            .setPositiveButton("Close", null)
            .setNeutralButton("Source code") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dhruba-datta/iconz")))
            }.show()
    }

    private inner class IconAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val cell = convertView as? LinearLayout ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(10), dp(4), dp(8))
                addView(ImageView(context), LinearLayout.LayoutParams(dp(52), dp(52)))
                addView(label("", 11f, Palette.MUTED).apply {
                    gravity = Gravity.CENTER
                    maxLines = 2
                    setPadding(0, dp(6), 0, 0)
                })
            }
            val icon = shown[position]
            (cell.getChildAt(0) as ImageView).setImageResource(IconCatalog.drawableId(this@MainActivity, icon.drawable))
            (cell.getChildAt(1) as TextView).text = icon.label
            return cell
        }
    }
}
