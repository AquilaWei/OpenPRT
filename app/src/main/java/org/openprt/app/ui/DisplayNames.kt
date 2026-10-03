package org.openprt.app.ui

/** Short names riders know in capitals; everything else in an all-caps name is title-cased. */
private val ACRONYMS = setOf(
    "CCAC", "CMU", "NE", "NW", "PNC", "PPG", "PRT", "SE", "SW", "UPMC", "US", "VA", "YMCA", "YWCA",
    "II", "III", "IV"
)

/** Joining words that read better in lower case, as on PRT's own signs ("Forbes Ave at Wood"). */
private val MINOR_WORDS = setOf("AT", "OF", "AND", "THE", "TO")

private val WORD = Regex("[A-Z0-9]+(?:'[A-Z]+)?")
private val ORDINAL = Regex("\\d+(ST|ND|RD|TH)")
private val DIRECTION_PREFIX =
    Regex("^(INBOUND|OUTBOUND|NORTHBOUND|SOUTHBOUND|EASTBOUND|WESTBOUND)\\s*[-–:]\\s*")

/**
 * A stop or place name as riders read it. The GTFS feed and some TrueTime answers spell names in
 * capitals ("FORBES AVE + MOREWOOD (CARNEGIE MELLON)"), which is slow to read in a list, so they
 * become "Forbes Ave + Morewood (Carnegie Mellon)". A name with any lower-case letter is already
 * written for people and comes back unchanged.
 */
fun displayName(raw: String): String {
    if (raw.any { it.isLowerCase() }) return raw
    var first = true
    return WORD.replace(raw) { match ->
        val word = match.value
        val minor = !first && word in MINOR_WORDS
        first = false
        when {
            word in ACRONYMS -> word
            minor -> word.lowercase()
            ORDINAL.matches(word) -> word.lowercase()
            else -> titleCase(word)
        }
    }
}

/**
 * Where a trip is headed, without the direction the GTFS feed puts in front of it:
 * "INBOUND-DOWNTOWN" becomes "Downtown". The direction is shown separately, and "Toward
 * INBOUND-DOWNTOWN" read as one long code. A headsign that is only a direction is kept.
 */
fun displayHeadsign(raw: String): String {
    val place = raw.replace(DIRECTION_PREFIX, "")
    return displayName(place.ifBlank { raw })
}

private fun titleCase(word: String): String {
    val lower = word.lowercase()
    return when {
        // McKeesport, McKnight.
        word.length > 3 && word.startsWith("MC") ->
            "Mc" + lower[2].uppercaseChar() + lower.substring(3)

        // O'Hara, but Mary's.
        word.length > 2 && word[1] == '\'' ->
            word.substring(0, 2) + lower[2].uppercaseChar() + lower.substring(3)

        else -> lower.replaceFirstChar { it.uppercaseChar() }
    }
}
