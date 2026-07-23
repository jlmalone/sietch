package vision.salient.sietch.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.coroutines.runBlocking
import vision.salient.sietch.core.formatSize
import vision.salient.sietch.core.ipfs.IpfsClient
import vision.salient.sietch.core.parseCidCatalog
import vision.salient.sietch.core.registry.ContentLocationRegistry
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class PullCommand : CliktCommand(
    name = "pull",
    help = "Reconstruct a directory tree from a CID catalog by fetching each file from IPFS."
) {
    private val catalogPath by argument(help = "Path to a CID catalog file")
    private val output by option("--output", "-o", help = "Output directory to reconstruct into").required()
    private val ipfs by option("--ipfs", help = "Kubo API URL").required()
    private val db by option("--db", help = "Registry database path").default("sietch_registry.db")
    private val machine by option("--machine", "-m", help = "Machine name for registering fetched locations").default("local")

    override fun run() = runBlocking {
        val catalogFile = File(catalogPath)
        if (!catalogFile.exists()) {
            echo("Error: catalog file '$catalogPath' not found", err = true)
            return@runBlocking
        }

        val catalog = parseCidCatalog(catalogFile)
        if (catalog.entries.isEmpty()) {
            echo("Catalog is empty — nothing to pull")
            return@runBlocking
        }

        val ipfsClient = IpfsClient(ipfs)
        if (!ipfsClient.isAvailable()) {
            echo("Error: Kubo node at $ipfs is not reachable", err = true)
            ipfsClient.close()
            return@runBlocking
        }

        val outputDir = Path.of(output)
        Files.createDirectories(outputDir)

        val registry = ContentLocationRegistry(Path.of(db))

        echo("Sietch pull — reconstructing directory tree")
        echo("Catalog:  $catalogPath (${catalog.entries.size} files from ${catalog.machineName})")
        echo("Source:   ${catalog.rootPath}")
        echo("Target:   ${outputDir.toAbsolutePath()}")
        echo()

        var fetched = 0
        var skipped = 0
        var errors = 0
        var totalBytes = 0L

        for (entry in catalog.entries) {
            val targetPath = outputDir.resolve(entry.path)

            // Skip if already exists and same size
            if (Files.exists(targetPath) && Files.size(targetPath) == entry.size) {
                skipped++
                continue
            }

            // Create parent directories
            Files.createDirectories(targetPath.parent)

            try {
                val bytes = ipfsClient.fetchToFile(entry.cid, targetPath)
                registry.register(entry.cid, machine, targetPath.toAbsolutePath().toString(), bytes)
                fetched++
                totalBytes += bytes
                echo("  ${formatSize(bytes).padStart(10)}  ${entry.path}")
            } catch (e: Exception) {
                echo("  ERROR: ${entry.path} — ${e.message}", err = true)
                errors++
            }
        }

        echo()
        echo("Done. $fetched fetched (${formatSize(totalBytes)}), $skipped already existed, $errors errors")
        echo("Tree:  ${outputDir.toAbsolutePath()}")

        registry.close()
        ipfsClient.close()
    }
}
