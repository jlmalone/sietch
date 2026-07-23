package vision.salient.sietch.core.registry

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

/**
 * SQLite-backed registry that maps CID → list of known file locations across machines.
 *
 * This is the core data structure that enables content-addressed file resolution:
 * given a CID, find all machines and paths where that content exists.
 *
 * @param dbPath Path to the SQLite database file
 */
class ContentLocationRegistry(dbPath: Path) {

    private val connection: Connection

    init {
        Class.forName("org.sqlite.JDBC")  // Ensure driver loaded
        connection = DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}")
        connection.createStatement().use { it.execute("PRAGMA journal_mode=WAL") }
        createSchema()
    }

    private fun createSchema() {
        connection.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS content_locations (
                    cid TEXT NOT NULL,
                    machine_name TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    file_size INTEGER,
                    verified_at TEXT,
                    registered_at TEXT NOT NULL DEFAULT (datetime('now')),
                    PRIMARY KEY (cid, machine_name, file_path)
                )
            """.trimIndent())

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_cl_machine
                ON content_locations(machine_name)
            """.trimIndent())

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_cl_cid
                ON content_locations(cid)
            """.trimIndent())

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_cl_filepath
                ON content_locations(file_path)
            """.trimIndent())
        }
    }

    /**
     * Register a content location: this CID exists at this path on this machine.
     * Uses INSERT OR REPLACE so re-registering the same location updates timestamps.
     */
    fun register(cid: String, machine: String, path: String, fileSize: Long? = null) {
        val sql = """
            INSERT OR REPLACE INTO content_locations (cid, machine_name, file_path, file_size, verified_at, registered_at)
            VALUES (?, ?, ?, ?, ?, datetime('now'))
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cid)
            stmt.setString(2, machine)
            stmt.setString(3, path)
            if (fileSize != null) stmt.setLong(4, fileSize) else stmt.setNull(4, java.sql.Types.INTEGER)
            stmt.setString(5, Instant.now().toString())
            stmt.executeUpdate()
        }
    }

    /**
     * Get all known locations for a CID.
     */
    fun getLocations(cid: String): List<ContentLocation> {
        val sql = "SELECT cid, machine_name, file_path, file_size, verified_at FROM content_locations WHERE cid = ?"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cid)
            val rs = stmt.executeQuery()
            val results = mutableListOf<ContentLocation>()
            while (rs.next()) {
                results.add(ContentLocation(
                    cid = rs.getString("cid"),
                    machineName = rs.getString("machine_name"),
                    filePath = rs.getString("file_path"),
                    fileSize = rs.getLong("file_size").takeIf { !rs.wasNull() },
                    verifiedAt = rs.getString("verified_at")?.let { Instant.parse(it) }
                ))
            }
            results
        }
    }

    /**
     * Get all content registered on a specific machine.
     */
    fun getByMachine(machine: String): List<ContentLocation> {
        val sql = "SELECT cid, machine_name, file_path, file_size, verified_at FROM content_locations WHERE machine_name = ?"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, machine)
            val rs = stmt.executeQuery()
            val results = mutableListOf<ContentLocation>()
            while (rs.next()) {
                results.add(ContentLocation(
                    cid = rs.getString("cid"),
                    machineName = rs.getString("machine_name"),
                    filePath = rs.getString("file_path"),
                    fileSize = rs.getLong("file_size").takeIf { !rs.wasNull() },
                    verifiedAt = rs.getString("verified_at")?.let { Instant.parse(it) }
                ))
            }
            results
        }
    }

    /**
     * Remove a specific location entry.
     */
    fun remove(cid: String, machine: String, path: String? = null) {
        if (path != null) {
            connection.prepareStatement(
                "DELETE FROM content_locations WHERE cid = ? AND machine_name = ? AND file_path = ?"
            ).use { stmt ->
                stmt.setString(1, cid)
                stmt.setString(2, machine)
                stmt.setString(3, path)
                stmt.executeUpdate()
            }
        } else {
            connection.prepareStatement(
                "DELETE FROM content_locations WHERE cid = ? AND machine_name = ?"
            ).use { stmt ->
                stmt.setString(1, cid)
                stmt.setString(2, machine)
                stmt.executeUpdate()
            }
        }
    }

    /**
     * Touch the verified_at timestamp for a location (confirms file still exists there).
     */
    fun updateVerified(cid: String, machine: String) {
        val sql = "UPDATE content_locations SET verified_at = ? WHERE cid = ? AND machine_name = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, Instant.now().toString())
            stmt.setString(2, cid)
            stmt.setString(3, machine)
            stmt.executeUpdate()
        }
    }

    /**
     * Reverse lookup: get CID for a known file path.
     * Optionally filter by machine name for disambiguation.
     * Returns the first matching CID, or null if no match.
     */
    fun getCidForPath(filePath: String, machine: String? = null): String? {
        val sql = if (machine != null) {
            "SELECT cid FROM content_locations WHERE file_path = ? AND machine_name = ? LIMIT 1"
        } else {
            "SELECT cid FROM content_locations WHERE file_path = ? LIMIT 1"
        }
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, filePath)
            if (machine != null) stmt.setString(2, machine)
            val rs = stmt.executeQuery()
            if (rs.next()) rs.getString("cid") else null
        }
    }

    /**
     * Get total count of unique CIDs in the registry.
     */
    fun countCids(): Long {
        return connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT COUNT(DISTINCT cid) FROM content_locations")
            if (rs.next()) rs.getLong(1) else 0L
        }
    }

    /**
     * Get total count of location entries.
     */
    fun countLocations(): Long {
        return connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT COUNT(*) FROM content_locations")
            if (rs.next()) rs.getLong(1) else 0L
        }
    }

    fun close() {
        connection.close()
    }
}

data class ContentLocation(
    val cid: String,
    val machineName: String,
    val filePath: String,
    val fileSize: Long? = null,
    val verifiedAt: Instant? = null
)
