package fyi.slot.ui

/**
 * Finds the best box-art file name for a ROM name. ROM files are rarely named exactly like the No-Intro / Redump
 * sets the libretro thumbnail server uses ("Gran Turismo 4.iso" vs "Gran Turismo 4 (USA).png", "Wave Race 64
 * (USA) (Rev A)" vs "(Rev 1)", a subtitle that one of them leaves out), so names are compared loosely.
 */
object CoverMatch {
    internal class Parsed(val name: String, val main: String, val full: String, val tags: List<String>) {
        val tokens: Set<String> by lazy { full.split(' ').toSet() }
        val tagText: String by lazy { tags.joinToString(" ") }
        val region: String? by lazy { regionOf(tags) }
        val unwanted: Boolean by lazy { tags.any(::isPrerelease) }
    }

    /** A file list, parsed once so each lookup only has to compare. */
    class Index(names: List<String>) {
        internal val entries: List<Parsed> = names.map(::parse)
        internal val byFull: Map<String, List<Parsed>> = entries.groupBy { it.full }
        internal val byMain: Map<String, List<Parsed>> = entries.groupBy { it.main }
        val size get() = entries.size
    }

    private val ARTICLES = setOf("the", "a", "an")
    private val REGIONS = listOf("usa", "world", "europe", "japan")
    private val TAGS = Regex("[(\\[]([^)\\]]*)[)\\]]")
    private val NON_WORD = Regex("[^a-z0-9]+")

    private fun isPrerelease(t: String) = t.startsWith("demo") || t.startsWith("beta") || t.startsWith("proto") || t.startsWith("sample")

    private fun tagsOf(s: String): List<String> = TAGS.findAll(s).map { it.groupValues[1].trim().lowercase() }.toList()

    private fun words(s: String): List<String> =
        s.lowercase().replace(NON_WORD, " ").trim().split(' ').filter { it.isNotEmpty() && it !in ARTICLES }

    private fun parse(name: String): Parsed {
        val base = name.replace(TAGS, " ")
        val colon = base.indexOf(':', 1)
        val dash = base.indexOf(" - ")
        val sub = listOf(colon, dash).filter { it > 0 }.minOrNull() ?: base.length
        return Parsed(name, words(base.substring(0, sub)).joinToString(" "), words(base).joinToString(" "), tagsOf(name))
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        var common = 0
        for (x in a) if (x in b) common++
        return common.toFloat() / (a.size + b.size - common)
    }

    private fun regionOf(tags: List<String>): String? =
        tags.firstNotNullOfOrNull { t -> REGIONS.firstOrNull { t.split(',').map(String::trim).contains(it) } }

    /** The listed file (without extension) that best fits [stem], or null if nothing is close enough. */
    fun best(stem: String, index: Index): String? {
        val want = parse(stem)
        if (want.full.isEmpty()) return null
        val wantRegion = want.region
        val wantPre = want.tags.any(::isPrerelease)
        var top: String? = null
        var topScore = 0f

        fun consider(c: Parsed, base: Float) {
            var s = base
            if (wantRegion != null && c.region == wantRegion) s += 6f
            s += when (c.region) { "usa" -> 3f; "world" -> 2f; "europe" -> 1f; else -> 0f }
            if (want.tagText.isNotEmpty() && c.tagText == want.tagText) s += 4f
            if (c.unwanted && !wantPre) s -= 5f
            // fewer extra tags (revisions, languages) is the plainer, more likely print
            s -= c.tags.size * .3f
            if (s > topScore) { topScore = s; top = c.name }
        }

        val same = index.byFull[want.full].orEmpty()
        for (c in same) consider(c, if (c.name == stem) 200f else 100f)
        if (top != null) return top

        // same title, one of them has a subtitle the other leaves out: "Virtua Tennis" / "Virtua Tennis - Sega Professional Tennis"
        for (c in index.byMain[want.main].orEmpty()) {
            if (want.main.isNotEmpty() && (c.full.startsWith(want.full) || want.full.startsWith(c.full))) consider(c, 90f)
        }
        if (top != null) return top

        for (c in index.entries) {
            val j = jaccard(c.tokens, want.tokens)
            if (j >= .8f) { consider(c, 60f * j); continue }
            // last resort: the file name is the title minus a trailing number ("Wave Race" for "Wave Race 64")
            if (want.full.contains(' ') && c.main.startsWith(want.full + " ") && c.main.removePrefix(want.full).trim().count { it == ' ' } == 0) consider(c, 30f)
        }
        return top
    }

    fun best(stem: String, names: List<String>): String? = best(stem, Index(names))

    /** File names (without .png) from an Apache-style directory index page. */
    fun parseListing(html: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        val open = "<a href=\""
        while (true) {
            val a = html.indexOf(open, i)
            if (a < 0) break
            val s = a + open.length
            val e = html.indexOf('"', s)
            if (e < 0) break
            i = e
            val href = html.substring(s, e)
            if (href.endsWith(".png") && href[0] != '?' && href[0] != '/') out.add(decode(href.dropLast(4)))
        }
        return out
    }

    private fun decode(s: String): String {
        if (!s.contains('%') && !s.contains('&')) return s
        val out = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val v = if (s[i] == '%' && i + 2 < s.length) s.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (v != null) { out.add(v.toByte()); i += 3 } else { out.addAll(s[i].toString().encodeToByteArray().toList()); i++ }
        }
        return out.toByteArray().decodeToString().replace("&amp;", "&")
    }
}
