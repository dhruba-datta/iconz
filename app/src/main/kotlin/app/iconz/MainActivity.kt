package app.iconz

import android.app.Activity
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

/** The icon gallery with search, and Apply. */
class MainActivity : Activity() {
    private lateinit var all: List<Icon>
    private var shown: List<Icon> = emptyList()
    private lateinit var adapter: BaseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        all = IconCatalog.icons(this)
        shown = all
        val mapped = IconCatalog.themedComponents(this).size

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.BG)
            setPadding(dp(16), dp(20), dp(16), 0)
            padForSystemBars()
        }
        root.addView(label(getString(R.string.app_name), 28f, bold = true))
        root.addView(
            label("${all.size} icons · $mapped apps mapped · v${BuildConfig.VERSION_NAME}", 13f, Palette.MUTED)
                .apply { setPadding(0, dp(2), 0, dp(14)) },
        )

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun add(v: View) = actions.addView(v, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
        add(pill("Apply") { Launchers.show(this) })
        root.addView(actions)

        val search = EditText(this).apply {
            hint = "Search icons"
            setHintTextColor(Palette.MUTED)
            setTextColor(Palette.TEXT)
            textSize = 15f
            isSingleLine = true
            background = rounded(Palette.CARD, dp(12))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = filter(s.toString())
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
                adapter = this@MainActivity.adapter
                setOnItemClickListener { _, _, pos, _ -> preview(shown[pos]) }
            },
            LinearLayout.LayoutParams(-1, 0, 1f),
        )
        setContentView(root)
    }

    private fun filter(q: String) {
        val t = q.trim().lowercase()
        shown = if (t.isEmpty()) all else all.filter { t in it.label.lowercase() || t in it.drawable }
        adapter.notifyDataSetChanged()
    }

    private fun preview(icon: Icon) {
        val big = ImageView(this).apply {
            setImageResource(icon.resId)
            background = rounded(Palette.CARD, dp(28))
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        val box = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(20), dp(24), 0)
            addView(big, LinearLayout.LayoutParams(dp(144), dp(144)))
        }
        val apps = icon.packages.joinToString("\n").ifEmpty { "No app package listed yet" }
        dialog().setTitle(icon.label).setView(box)
            .setMessage("${icon.category}\n\n$apps\n\nDrawable: ${icon.drawable}")
            .setPositiveButton("Close", null).show()
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
            (cell.getChildAt(0) as ImageView).setImageResource(icon.resId)
            (cell.getChildAt(1) as android.widget.TextView).text = icon.label
            return cell
        }
    }
}
