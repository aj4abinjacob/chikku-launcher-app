package io.github.aj4abinjacob.chikku.platform.icons

import org.xmlpull.v1.XmlPullParser

/**
 * Parsed `appfilter.xml` of an icon pack (the de-facto format shared by compatible Android
 * launchers). [components] maps a normalized `package/class` to a drawable name; the fallback fields
 * describe how to theme apps the pack does not cover.
 */
data class AppFilter(
    val components: Map<String, String>,
    val iconBacks: List<String> = emptyList(),
    val iconMask: String? = null,
    val iconUpon: String? = null,
    /** Scale of the original icon inside the pack's background; 1 = full size. */
    val scale: Float = 1f,
    val calendars: Map<String, String> = emptyMap(),
) {
    val hasFallbackTheme: Boolean get() = iconBacks.isNotEmpty() || iconMask != null || iconUpon != null

    // Packs often retain a calendar's old activity names after the app changes its launcher alias.
    // A unanimous package mapping can theme that alias; it never changes the app's launch identity.
    private val calendarPackagePrefixes = calendars.entries.groupBy { it.key.substringBefore('/') }
        .mapNotNull { (name, entries) -> entries.map { it.value }.distinct().singleOrNull()?.let { name to it } }
        .toMap()

    fun calendarDrawableFor(packageName: String, className: String, day: Int): String? =
        if (day in 1..31) (calendars["$packageName/$className"] ?: calendarPackagePrefixes[packageName])
            ?.let { "$it$day" } else null

    fun drawableFor(packageName: String, className: String): String? = components["$packageName/$className"]

    companion object {
        val EMPTY = AppFilter(emptyMap())
    }
}

/** Reads [AppFilter]s and drawable lists. Malformed entries are skipped, never fatal. */
object AppFilterParser {

    private const val MAX_ENTRIES = 50_000

    /**
     * Parses `appfilter.xml`. Recognized elements: `item component="ComponentInfo{pkg/cls}"
     * drawable="name"`, `iconback img1=".." img2=".."`, `iconmask img1=".."`, `iconupon img1=".."`,
     * `calendar component="ComponentInfo{pkg/cls}" prefix="calendar_"`, and `scale factor=".."`.
     */
    fun parse(parser: XmlPullParser): AppFilter {
        val components = LinkedHashMap<String, String>()
        val calendars = LinkedHashMap<String, String>()
        val backs = mutableListOf<String>()
        var mask: String? = null
        var upon: String? = null
        var scale = 1f
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && components.size + calendars.size < MAX_ENTRIES) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "item" -> {
                        val component = parser.attr("component")?.let(::normalizeComponent)
                        val drawable = parser.attr("drawable")?.trim()?.takeIf(::isResourceName)
                        // First mapping wins, as in other launchers.
                        if (component != null && drawable != null) components.putIfAbsent(component, drawable)
                    }
                    "calendar" -> {
                        val component = parser.attr("component")?.let(::normalizeComponent)
                        val prefix = parser.attr("prefix")?.trim()?.takeIf(::isResourceName)
                        if (component != null && prefix != null) calendars.putIfAbsent(component, prefix)
                    }
                    "iconback" -> backs += parser.imageAttributes()
                    "iconmask" -> mask = parser.imageAttributes().firstOrNull() ?: mask
                    "iconupon" -> upon = parser.imageAttributes().firstOrNull() ?: upon
                    "scale" -> parser.attr("factor")?.toFloatOrNull()
                        ?.takeIf { it.isFinite() && it > 0f && it <= MAX_SCALE }
                        ?.let { scale = it }
                }
            }
            event = parser.next()
        }
        return AppFilter(components, backs, mask, upon, scale, calendars)
    }

    /**
     * Drawable names from a pack's `drawable.xml` (`<item drawable="name"/>`), in file order without
     * duplicates. Packs use it to list every icon they ship, including alternates.
     */
    fun parseDrawableList(parser: XmlPullParser): List<String> {
        val names = LinkedHashSet<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && names.size < MAX_ENTRIES) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                parser.attr("drawable")?.trim()?.takeIf(::isResourceName)?.let(names::add)
            }
            event = parser.next()
        }
        return names.toList()
    }

    /**
     * `ComponentInfo{com.example/com.example.Main}` -> `com.example/com.example.Main`. A relative
     * class (`.Main`) is expanded with the package. Returns null for anything else.
     */
    fun normalizeComponent(raw: String): String? {
        val inner = raw.trim().removePrefix("ComponentInfo{").removeSuffix("}").trim()
        val slash = inner.indexOf('/')
        if (slash <= 0 || slash == inner.length - 1) return null
        val pkg = inner.substring(0, slash).trim()
        var cls = inner.substring(slash + 1).trim()
        if (cls.startsWith(".")) cls = pkg + cls
        if (pkg.isEmpty() || cls.isEmpty() || pkg.contains(' ') || cls.contains(' ')) return null
        return "$pkg/$cls"
    }

    private fun isResourceName(name: String): Boolean =
        name.isNotEmpty() && name.all { it.isLetterOrDigit() || it == '_' || it == '.' }

    private fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)

    private fun XmlPullParser.imageAttributes(): List<String> =
        (0 until attributeCount)
            .filter { getAttributeName(it).startsWith("img") }
            .mapNotNull { getAttributeValue(it)?.trim()?.takeIf(::isResourceName) }

    private const val MAX_SCALE = 2f
}
