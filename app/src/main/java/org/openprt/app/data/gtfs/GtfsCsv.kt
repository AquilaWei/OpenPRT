package org.openprt.app.data.gtfs

import java.io.Reader

/** A GTFS file broke the format rules: bad quoting, a missing column, or an unparsable value. */
class GtfsFormatException(message: String) : Exception(message)

/**
 * Reads a GTFS `.txt` table (RFC 4180 CSV with a header row) one row at a time.
 *
 * Handles a UTF-8 BOM, CRLF or LF line endings, quoted fields containing commas, quotes (`""`)
 * or line breaks, and blank lines. The sequence can be iterated only once and does not close
 * [reader], so it can read one entry of a still-open zip stream.
 *
 * @throws GtfsFormatException while iterating, if a quoted field is never closed.
 */
fun readGtfsTable(reader: Reader): Sequence<GtfsRow> = sequence {
    val records = CsvRecordReader(reader)
    val header = records.next() ?: return@sequence
    val columns = header
        .mapIndexed { index, name -> name.trimStart(BOM).trim() to index }
        .toMap()
    var rowNumber = 1
    while (true) {
        val values = records.next() ?: break
        rowNumber++
        yield(GtfsRow(columns, values, rowNumber))
    }
}

/**
 * One data row. Rows shorter than the header (trailing fields left out) read as if the missing
 * fields were empty; GTFS treats empty and absent the same, so both read as `null`.
 */
class GtfsRow internal constructor(
    private val columns: Map<String, Int>,
    private val values: List<String>,
    /** 1-based record number including the header, for error messages. */
    val rowNumber: Int
) {
    /** The trimmed value of [column], or `null` if the column is absent or the field empty. */
    operator fun get(column: String): String? =
        columns[column]?.let(values::getOrNull)?.trim()?.ifEmpty { null }

    /** @throws GtfsFormatException if [column] is absent or empty in this row. */
    fun required(column: String): String =
        get(column) ?: throw GtfsFormatException("Row $rowNumber: missing $column")

    /** @throws GtfsFormatException if [column] is absent, empty or not a number. */
    fun requiredDouble(column: String): Double = required(column).toDoubleOrNull()
        ?: throw GtfsFormatException("Row $rowNumber: $column is not a number")

    /** @throws GtfsFormatException if [column] is present but not an integer. */
    fun optionalInt(column: String): Int? = get(column)?.let {
        it.toIntOrNull() ?: throw GtfsFormatException("Row $rowNumber: $column is not an integer")
    }
}

private const val BOM = '\uFEFF'

/** Splits CSV text into records; buffers itself because GTFS files can be tens of megabytes. */
private class CsvRecordReader(private val reader: Reader) {
    private val buffer = CharArray(64 * 1024)
    private var length = 0
    private var position = 0

    /** The fields of the next non-blank record, or `null` at end of input. */
    fun next(): List<String>? {
        var c = read()
        while (c == CR || c == LF) c = read()
        if (c == EOF) return null

        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        while (true) {
            if (inQuotes) {
                when (c) {
                    EOF -> throw GtfsFormatException("Unterminated quoted field")

                    QUOTE -> if (peek() ==
                        QUOTE
                    ) {
                        field.append(read().toChar())
                    } else {
                        inQuotes = false
                    }

                    else -> field.append(c.toChar())
                }
            } else {
                when (c) {
                    QUOTE -> inQuotes = true

                    COMMA -> {
                        fields += field.toString()
                        field.setLength(0)
                    }

                    CR, LF, EOF -> {
                        if (c == CR && peek() == LF) read()
                        fields += field.toString()
                        return fields
                    }

                    else -> field.append(c.toChar())
                }
            }
            c = read()
        }
    }

    private fun read(): Int = if (fill()) buffer[position++].code else EOF

    private fun peek(): Int = if (fill()) buffer[position].code else EOF

    private fun fill(): Boolean {
        if (position < length) return true
        length = reader.read(buffer).coerceAtLeast(0)
        position = 0
        return length > 0
    }

    private companion object {
        const val EOF = -1
        const val CR = '\r'.code
        const val LF = '\n'.code
        const val QUOTE = '"'.code
        const val COMMA = ','.code
    }
}
