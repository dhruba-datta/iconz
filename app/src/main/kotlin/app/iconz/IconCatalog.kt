package app.iconz

import android.content.Context
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

data class Icon(
    val drawable: String,
    val label: String,
    val category: String,
    val packages: List<String>,
    val resId: Int,
)

/**
 * What the pack contains. Launchers read the same files: res/xml/drawable.xml (the icon
 * list, for their icon pickers) and res/xml/appfilter.xml (which app gets which icon).
 * All three files, plus assets/icon_index.json, are written by tool/build_icons.py from
 * icons/icons.json; never edit them by hand.
 */
object IconCatalog {
    private class Entry(val label: String, val packages: List<String>)

    fun icons(ctx: Context): List<Icon> {
        val index = index(ctx)
        val out = mutableListOf<Icon>()
        var category = ""
        forEachTag(ctx, R.xml.drawable) { name, attr ->
            when (name) {
                "category" -> category = attr("title").orEmpty()
                "item" -> {
                    val drawable = attr("drawable")
                    val id = drawable?.let { drawableId(ctx, it) } ?: 0
                    if (drawable != null && id != 0) {
                        val e = index[drawable]
                        out += Icon(drawable, e?.label ?: drawable, category, e?.packages.orEmpty(), id)
                    }
                }
            }
        }
        return out
    }

    @Suppress("DiscouragedApi") // icon packs are looked up by name, like launchers do
    fun drawableId(ctx: Context, name: String) =
        ctx.resources.getIdentifier(name, "drawable", ctx.packageName)

    /** "package/activity" of every app this pack gives an icon. */
    fun themedComponents(ctx: Context): Set<String> {
        val out = mutableSetOf<String>()
        forEachTag(ctx, R.xml.appfilter) { name, attr ->
            val c = attr("component")
            if (name == "item" && c != null && c.startsWith("ComponentInfo{")) {
                out += c.removePrefix("ComponentInfo{").removeSuffix("}")
            }
        }
        return out
    }

    /** Package name → drawable, for icons drawn but not yet mapped to an activity. */
    fun drawableForPackage(ctx: Context): Map<String, String> =
        index(ctx).flatMap { (drawable, e) -> e.packages.map { it to drawable } }.toMap()

    private fun index(ctx: Context): Map<String, Entry> {
        val json = ctx.assets.open("icon_index.json").bufferedReader().use { JSONObject(it.readText()) }
        return json.keys().asSequence().associateWith { key ->
            val o = json.getJSONObject(key)
            val pkgs = o.optJSONArray("packages")
            Entry(o.optString("label", key), List(pkgs?.length() ?: 0) { pkgs!!.getString(it) })
        }
    }

    private fun forEachTag(ctx: Context, xmlRes: Int, onTag: (String, (String) -> String?) -> Unit) {
        val xml = ctx.resources.getXml(xmlRes)
        try {
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG) {
                    onTag(xml.name) { xml.getAttributeValue(null, it) }
                }
            }
        } finally {
            xml.close()
        }
    }
}
