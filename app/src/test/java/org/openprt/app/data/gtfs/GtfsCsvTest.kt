package org.openprt.app.data.gtfs

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GtfsCsvTest {
    private fun rows(text: String): List<GtfsRow> = readGtfsTable(StringReader(text)).toList()

    @Test
    fun quotedField_withComma_keepsCommaInValue() {
        val row = rows("stop_id,stop_name\n1,\"FIFTH AVE + BELLEFIELD, OPP\"\n").single()

        assertEquals("FIFTH AVE + BELLEFIELD, OPP", row["stop_name"])
    }

    @Test
    fun quotedField_withDoubledQuotes_unescapesToSingleQuote() {
        val row = rows("stop_id,stop_name\n1,\"MOREWOOD \"\"CMU\"\"\"\n").single()

        assertEquals("MOREWOOD \"CMU\"", row["stop_name"])
    }

    @Test
    fun quotedField_withLineBreak_staysOneRow() {
        val row = rows("stop_id,stop_desc\n1,\"line one\nline two\"\n").single()

        assertEquals("line one\nline two", row["stop_desc"])
    }

    @Test
    fun quotedField_neverClosed_throwsFormatException() {
        assertThrows(GtfsFormatException::class.java) {
            rows("stop_id,stop_name\n1,\"STEEL PLAZA\n")
        }
    }

    @Test
    fun header_withByteOrderMark_findsFirstColumn() {
        val row = rows("\uFEFFstop_id,stop_name\n10,STEEL PLAZA\n").single()

        assertEquals("10", row["stop_id"])
    }

    @Test
    fun crlfLineEndings_doNotLeakIntoLastField() {
        val row = rows("stop_id,stop_name\r\n10,STEEL PLAZA\r\n").single()

        assertEquals("STEEL PLAZA", row["stop_name"])
    }

    @Test
    fun blankLines_areSkipped() {
        val ids = rows("stop_id\n\n10\r\n\r\n11\n\n").map { it["stop_id"] }

        assertEquals(listOf("10", "11"), ids)
    }

    @Test
    fun lastLine_withoutLineBreak_isRead() {
        val row = rows("stop_id\n10").single()

        assertEquals("10", row["stop_id"])
    }

    @Test
    fun rowShorterThanHeader_readsMissingTrailingFieldsAsNull() {
        val row = rows("stop_id,stop_name,wheelchair_boarding\n10,STEEL PLAZA\n").single()

        assertNull(row["wheelchair_boarding"])
    }

    @Test
    fun emptyField_readsAsNull() {
        val row = rows("stop_id,stop_code,stop_name\n10,,STEEL PLAZA\n").single()

        assertNull(row["stop_code"])
    }

    @Test
    fun columnNotInHeader_readsAsNull() {
        val row = rows("stop_id\n10\n").single()

        assertNull(row["platform_code"])
    }

    @Test
    fun required_whenFieldMissing_throwsFormatExceptionWithRowNumber() {
        val row = rows("stop_id,stop_name\n10,STEEL PLAZA\n11\n")[1]

        val error = assertThrows(GtfsFormatException::class.java) { row.required("stop_name") }

        assertEquals("Row 3: missing stop_name", error.message)
    }

    @Test
    fun requiredDouble_whenNotANumber_throwsFormatException() {
        val row = rows("stop_id,stop_lat\n10,north\n").single()

        assertThrows(GtfsFormatException::class.java) { row.requiredDouble("stop_lat") }
    }

    @Test
    fun values_areTrimmed() {
        val row = rows("stop_id, stop_name\n10, STEEL PLAZA \n").single()

        assertEquals("STEEL PLAZA", row["stop_name"])
    }

    @Test
    fun emptyInput_hasNoRows() {
        assertEquals(emptyList<GtfsRow>(), rows(""))
    }
}
