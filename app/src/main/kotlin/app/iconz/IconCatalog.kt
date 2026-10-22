package app.iconz

import android.content.Context
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

data class Icon(
    val drawable: String,
    val label: String,
    val packages: List<String>,
)

class Catalog(val bangladesh: List<Icon>, val all: List<Icon>, val appEntries: Int)

/**
 * What the pack contains. Launchers read the same files: res/xml/drawable.xml (the icon
 * list, for their icon pickers) and res/xml/appfilter.xml (which app gets which icon).
 * Both, plus assets/icon_index.json, are written by tool/build_icons.py; never edit them
 * by hand.
 */
object IconCatalog {
    private val ids = HashMap<String, Int>()

    fun load(ctx: Context): Catalog {
        val json = ctx.assets.open("icon_index.json").bufferedReader().use { JSONObject(it.readText()) }
        val named = json.getJSONObject("icons")
        fun icon(name: String): Icon {
            val o = named.optJSONObject(name) ?: return Icon(name, pretty(name), emptyList())
            val pkgs = o.optJSONArray("packages")
            return Icon(name, o.optString("label", pretty(name)), List(pkgs?.length() ?: 0) { pkgs!!.getString(it) })
        }

        val bangladesh = mutableListOf<Icon>()
        val all = mutableListOf<Icon>()
        var category = ""
        val xml = ctx.resources.getXml(R.xml.drawable)
        try {
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType != XmlPullParser.START_TAG) continue
                when (xml.name) {
                    "category" -> category = xml.getAttributeValue(null, "title").orEmpty()
                    "item" -> {
                        val name = xml.getAttributeValue(null, "drawable") ?: continue
                        (if (category == "Bangladesh") bangladesh else all) += icon(name)
                    }
                }
            }
        } finally {
            xml.close()
        }
        return Catalog(bangladesh, all.sortedBy { it.label.lowercase() }, json.optInt("apps"))
    }

    /** Looked up only when an icon is shown: there are about 15,000. */
    @Suppress("DiscouragedApi") // icon packs are looked up by name, like launchers do
    fun drawableId(ctx: Context, name: String) =
        ids.getOrPut(name) { ctx.resources.getIdentifier(name, "drawable", ctx.packageName) }

    /** "_10_minute_school" → "10 Minute School". */
    private fun pretty(name: String) =
        name.split('_').filter { it.isNotEmpty() }.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}
