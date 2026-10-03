package org.openprt.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNamesTest {
    @Test
    fun displayName_allCapsStop_isTitleCased() {
        assertEquals(
            "Forbes Ave + Morewood (Carnegie Mellon)",
            displayName("FORBES AVE + MOREWOOD (CARNEGIE MELLON)")
        )
    }

    @Test
    fun displayName_mixedCase_isUnchanged() {
        assertEquals(
            "Forbes Ave at Morewood Ave (Carnegie Mellon)",
            displayName("Forbes Ave at Morewood Ave (Carnegie Mellon)")
        )
    }

    @Test
    fun displayName_commaAndOpp_keepsPunctuation() {
        assertEquals("Fifth Ave + Bellefield, Opp", displayName("FIFTH AVE + BELLEFIELD, OPP"))
    }

    @Test
    fun displayName_acronym_staysInCapitals() {
        assertEquals("UPMC Presbyterian", displayName("UPMC PRESBYTERIAN"))
    }

    @Test
    fun displayName_joiningWordInside_isLowerCase() {
        assertEquals("Fifth Ave at Wood St", displayName("FIFTH AVE AT WOOD ST"))
    }

    @Test
    fun displayName_joiningWordFirst_isCapitalized() {
        assertEquals("The Waterfront", displayName("THE WATERFRONT"))
    }

    @Test
    fun displayName_ordinal_isLowerCase() {
        assertEquals("21st St + Penn Ave", displayName("21ST ST + PENN AVE"))
    }

    @Test
    fun displayName_mcPrefix_capitalizesBothParts() {
        assertEquals("McKeesport", displayName("MCKEESPORT"))
    }

    @Test
    fun displayName_irishApostrophe_capitalizesAfterIt() {
        assertEquals("O'Hara St", displayName("O'HARA ST"))
    }

    @Test
    fun displayName_possessive_staysLowerCase() {
        assertEquals("St. Mary's Church", displayName("ST. MARY'S CHURCH"))
    }

    @Test
    fun displayHeadsign_inboundPrefix_isRemoved() {
        assertEquals("Downtown", displayHeadsign("INBOUND-DOWNTOWN"))
    }

    @Test
    fun displayHeadsign_outboundPrefixWithSpaces_isRemoved() {
        assertEquals("Lawrenceville", displayHeadsign("OUTBOUND - LAWRENCEVILLE"))
    }

    @Test
    fun displayHeadsign_onlyDirection_isKept() {
        assertEquals("Inbound", displayHeadsign("INBOUND"))
    }

    @Test
    fun displayHeadsign_noPrefix_isTitleCased() {
        assertEquals(
            "Braddock Hills Shopping Center",
            displayHeadsign("BRADDOCK HILLS SHOPPING CENTER")
        )
    }
}
