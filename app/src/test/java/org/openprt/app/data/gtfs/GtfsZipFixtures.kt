package org.openprt.app.data.gtfs

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Text files of the small fixture feed under test resources `gtfs/feed/`. */
val fixtureFeedFiles: Map<String, String> =
    listOf(
        "agency.txt",
        "calendar.txt",
        "calendar_dates.txt",
        "routes.txt",
        "stop_times.txt",
        "stops.txt",
        "trips.txt"
    ).associateWith { name ->
        object {}.javaClass.getResource("/gtfs/feed/$name")!!.readText()
    }

/** Zips [files] (name to contents) the way a GTFS feed is published. */
fun zipOf(files: Map<String, String>): ByteArray {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
        files.forEach { (name, text) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(text.toByteArray())
            zip.closeEntry()
        }
    }
    return bytes.toByteArray()
}
