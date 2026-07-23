package vision.salient.sietch.core

import vision.salient.sietch.core.ipfs.IpfsClient
import vision.salient.sietch.core.registry.ContentLocationRegistry
import java.io.File
import java.io.PrintWriter
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * CID-aware catalog entry — extends the existing CatalogEntry pattern with IPFS CIDs.
 */
data class CidCatalogEntry(
    val path: String,
    val cid: String,
    val sha256: String,
    val size: Long
)

/**
 * CID-aware catalog — same structure as SietchCatalog but with CIDs.
 */
data class CidCatalog(
    val rootPath: String,
    val date: String,
    val machineName: String,
    val entries: List<CidCatalogEntry>
)

/**
 * Result of a streaming CID indexing operation.
 */
data class CidIndexResult(
    val rootPath: String,
    val date: String,
    val machineName: String,
    val fileCount: Long,
    val totalSize: Long
)

/**
 * Index a directory, computing both SHA-256 hashes and IPFS CIDs.
 * Registers every file in the ContentLocationRegistry.
 *
 * Streams results: walks the tree and processes each file immediately,
 * writing entries to the output file incrementally to avoid OOM on large drives.
 *
 * @param dir Directory to index
 * @param ipfsClient Kubo API client for CID computation
 * @param registry Location registry to populate
 * @param machineName Name of the machine (for location tracking)
 * @param outputFile File to write catalog entries to (streamed, not buffered)
 * @param progressCallback Optional callback invoked after each file with (count, path)
 */
fun indexDirectoryWithCidsStreaming(
    dir: File,
    ipfsClient: IpfsClient,
    registry: ContentLocationRegistry,
    machineName: String,
    outputFile: File,
    ipfsBinary: String = "ipfs",
    excludePatterns: List<String> = emptyList(),
    useSietchIgnore: Boolean = false,
    progressCallback: ((Long, String) -> Unit)? = null
): CidIndexResult {
    val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val rootPath = dir.toPath()
    var count = 0L
    var totalSize = 0L

    outputFile.printWriter(Charsets.UTF_8).use { writer ->
        writer.println("# Sietch CID catalog: ${dir.absolutePath}")
        writer.println("# Date: $date")
        writer.println("# Machine: $machineName")
        writer.println("# Format: path\\tcid\\tsha256\\tsize")

        // Walk and process each file immediately — no collection into list
        walkTree(dir, excludePatterns, useSietchIgnore) { file ->
            try {
                val relativePath = rootPath.relativize(file.toPath()).toString()
                val sha256 = computeHash(file, "SHA-256")
                // Use CLI to avoid Ktor HTTP client memory leak on bulk scans
                val cid = ipfsClient.computeCidViaCli(file.toPath(), ipfsBinary)
                val size = file.length()

                writer.println("$relativePath\t$cid\t$sha256\t$size")
                writer.flush()

                registry.register(
                    cid = cid,
                    machine = machineName,
                    path = file.absolutePath,
                    fileSize = size
                )

                count++
                totalSize += size
                progressCallback?.invoke(count, relativePath)
            } catch (e: Exception) {
                System.err.println("  Warning: failed to index ${file.absolutePath}: ${e.message}")
            }
        }
    }

    return CidIndexResult(
        rootPath = dir.absolutePath,
        date = date,
        machineName = machineName,
        fileCount = count,
        totalSize = totalSize
    )
}

/**
 * Index a directory, computing both SHA-256 hashes and IPFS CIDs.
 * Registers every file in the ContentLocationRegistry.
 *
 * NOTE: Buffers all entries in memory. For large drives (>100K files),
 * use indexDirectoryWithCidsStreaming() instead.
 *
 * @param dir Directory to index
 * @param ipfsClient Kubo API client for CID computation
 * @param registry Location registry to populate
 * @param machineName Name of the machine (for location tracking)
 * @param progressCallback Optional callback invoked after each file with (count, path)
 */
suspend fun indexDirectoryWithCids(
    dir: File,
    ipfsClient: IpfsClient,
    registry: ContentLocationRegistry,
    machineName: String,
    excludePatterns: List<String> = emptyList(),
    useSietchIgnore: Boolean = false,
    progressCallback: ((Long, String) -> Unit)? = null
): CidCatalog {
    val entries = mutableListOf<CidCatalogEntry>()
    val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val rootPath = dir.toPath()
    var count = 0L

    // Walk and process each file immediately — no pre-collection into list
    walkTree(dir, excludePatterns, useSietchIgnore) { file ->
        try {
            val relativePath = rootPath.relativize(file.toPath()).toString()
            val sha256 = computeHash(file, "SHA-256")
            val cid = kotlinx.coroutines.runBlocking { ipfsClient.computeCid(file.toPath()) }
            val size = file.length()

            entries.add(CidCatalogEntry(
                path = relativePath,
                cid = cid,
                sha256 = sha256,
                size = size
            ))

            registry.register(
                cid = cid,
                machine = machineName,
                path = file.absolutePath,
                fileSize = size
            )

            count++
            progressCallback?.invoke(count, relativePath)
        } catch (e: Exception) {
            System.err.println("  Warning: failed to index ${file.absolutePath}: ${e.message}")
        }
    }

    return CidCatalog(
        rootPath = dir.absolutePath,
        date = date,
        machineName = machineName,
        entries = entries
    )
}

/**
 * Write a CID catalog to a file. Backward-compatible with existing format, plus CID column.
 */
fun writeCidCatalog(catalog: CidCatalog, outputFile: File) {
    outputFile.printWriter(Charsets.UTF_8).use { writer ->
        writer.println("# Sietch CID catalog: ${catalog.rootPath}")
        writer.println("# Date: ${catalog.date}")
        writer.println("# Machine: ${catalog.machineName}")
        writer.println("# Format: path\\tcid\\tsha256\\tsize")
        for (entry in catalog.entries) {
            writer.println("${entry.path}\t${entry.cid}\t${entry.sha256}\t${entry.size}")
        }
    }
}

/**
 * Parse a CID catalog file back into a CidCatalog object.
 */
fun parseCidCatalog(catalogFile: File): CidCatalog {
    val lines = catalogFile.readLines()
    var rootPath = ""
    var date = ""
    var machineName = ""
    val entries = mutableListOf<CidCatalogEntry>()

    for (line in lines) {
        when {
            line.startsWith("# Sietch CID catalog:") ->
                rootPath = line.substringAfter("# Sietch CID catalog:").trim()
            line.startsWith("# Date:") ->
                date = line.substringAfter("# Date:").trim()
            line.startsWith("# Machine:") ->
                machineName = line.substringAfter("# Machine:").trim()
            line.startsWith("#") -> continue
            line.isNotBlank() -> {
                val parts = line.split("\t")
                if (parts.size >= 4) {
                    entries.add(CidCatalogEntry(
                        path = parts[0],
                        cid = parts[1],
                        sha256 = parts[2],
                        size = parts[3].toLongOrNull() ?: 0
                    ))
                }
            }
        }
    }

    return CidCatalog(
        rootPath = rootPath,
        date = date,
        machineName = machineName,
        entries = entries
    )
}
