package vision.salient.sietch

import java.io.File
import java.sql.DriverManager

/**
 * Naib — "Leader of a Sietch" (Dune)
 * Inspects SQLite databases: lists tables, row counts, and schema.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: naib <path-to-sqlite-database>")
        return
    }

    val dbPath = args[0]
    val dbFile = File(dbPath)
    if (!dbFile.exists()) {
        System.err.println("Error: database not found: $dbPath")
        return
    }

    println("Naib — SQLite Inspector")
    println("Database: $dbPath (${formatSize(dbFile.length())})")
    println()

    DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
        val meta = conn.metaData
        val rs = meta.getTables(null, null, null, arrayOf("TABLE"))

        val tables = mutableListOf<String>()
        while (rs.next()) {
            tables.add(rs.getString("TABLE_NAME"))
        }

        if (tables.isEmpty()) {
            println("No tables found.")
            return
        }

        println("Tables (${tables.size}):")
        for (table in tables.sorted()) {
            val countRs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM \"$table\"")
            val count = if (countRs.next()) countRs.getLong(1) else 0
            println("  %-40s %,d rows".format(table, count))
        }
    }
}
