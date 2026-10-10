package fyi.slot.ui

/**
 * Finds the best box-art file name for a ROM name. ROM files are rarely named exactly like the No-Intro / Redump
 * sets the libretro thumbnail server uses ("Gran Turismo 4.iso" vs "Gran Turismo 4 (USA).png", "Wave Race 64
 * (USA) (Rev A)" vs "(Rev 1)", a subtitle that one of them leaves out), so names are compared loosely.
 */
object CoverMatch {
    private class Parsed(val name: String, val main: String, val full: String, val tags: List<String>)

    private val ARTICLES = setOf("the", "a", "an")
    private val REGIONS = listOf("usa", "world", "europe", "japan")

    private fun tagsOf(s: String): List<String> =
        Regex("[(\\[]([^)\\]]*)[)\\]]").findAll(s).map { it.groupValues[1].trim().lowercase() }.toList()

    private fun words(s: String): List<String> =
        s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().split(' ').filter { it.isNotEmpty() && it !in ARTICLES }

    private fun parse(name: String): Parsed {
        val base = name.replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ")
        val sub = base.indexOfAny(charArrayOf(':'), 1).takeIf { it > 0 } ?: base.indexOf(" - ").takeIf { it > 0 } ?: base.length
        return Parsed(name, words(base.substring(0, sub)).joinToString(" "), words(base).joinToString(" "), tagsOf(name))
    }

    private fun jaccard(a: String, b: String): Float {
        val x = a.split(' ').toSet(); val y = b.split(' ').toSet()
        if (x.isEmpty() || y.isEmpty()) return 0f
        return x.intersect(y).size.toFloat() / x.union(y).size
    }

    private fun regionOf(tags: List<String>): String? =
        tags.firstNotNullOfOrNull { t -> REGIONS.firstOrNull { t.split(',').map(String::trim).contains(it) } }

    /** The listed file (without extension) that best fits [stem], or null if nothing is close enough. */
    fun best(stem: String, names: List<String>): String? {
        val want = parse(stem)
        if (want.full.isEmpty()) return null
        val wantRegion = regionOf(want.tags)
        val wantTagWords = want.tags.joinToString(" ")
        var top: String? = null
        var topScore = 0f
        for (n in names) {
            val c = parse(n)
            var s = when {
                c.name == stem -> 200f
                c.full == want.full -> 100f
                // same title, one of them has a subtitle the other leaves out: "Virtua Tennis" / "Virtua Tennis - Sega Professional Tennis"
                c.main == want.main && c.main.isNotEmpty() && (c.full.startsWith(want.full) || want.full.startsWith(c.full)) -> 90f
                else -> {
                    val j = jaccard(c.full, want.full)
                    // last resort: the file name is the title minus a trailing number ("Wave Race" for "Wave Race 64")
                    if (j >= .8f) 60f * j
                    else if (want.full.contains(' ') && c.main.startsWith(want.full + " ") && c.main.removePrefix(want.full).trim().count { it == ' ' } == 0) 30f
                    else 0f
                }
            }
            if (s == 0f) continue
            val region = regionOf(c.tags)
            if (wantRegion != null && region == wantRegion) s += 6f
            s += when (region) { "usa" -> 3f; "world" -> 2f; "europe" -> 1f; else -> 0f }
            if (wantTagWords.isNotEmpty() && c.tags.joinToString(" ") == wantTagWords) s += 4f
            if (c.tags.any { it.startsWith("demo") || it.startsWith("beta") || it.startsWith("proto") || it.startsWith("sample") } &&
                want.tags.none { it.startsWith("demo") || it.startsWith("beta") || it.startsWith("proto") || it.startsWith("sample") }) s -= 5f
            // fewer extra tags (revisions, languages) is the plainer, more likely print
            s -= c.tags.size * .3f
            if (s > topScore) { topScore = s; top = n }
        }
        return top
    }

    /** File names (without .png) from an Apache-style directory index page. */
    fun parseListing(html: String): List<String> =
        Regex("<a href=\"([^\"?/][^\"]*)\\.png\"").findAll(html).map { decode(it.groupValues[1]) }.toList()

    private fun decode(s: String): String {
        val out = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val v = if (s[i] == '%' && i + 2 < s.length) s.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (v != null) { out.add(v.toByte()); i += 3 } else { out.addAll(s[i].toString().encodeToByteArray().toList()); i++ }
        }
        return out.toByteArray().decodeToString().replace("&amp;", "&")
    }
}
