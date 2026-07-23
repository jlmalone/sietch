package vision.salient.sietch.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.coroutines.runBlocking
import vision.salient.sietch.core.*
import vision.salient.sietch.core.ipfs.IpfsClient
import vision.salient.sietch.core.registry.ContentLocationRegistry
import java.io.File
import java.nio.file.Path

class IndexCommand : CliktCommand(
    name = "index",
    help = "Index a directory — compute IPFS CIDs and register content locations."
) {
    private val scanPath by argument(help = "Directory to scan")
    private val machine by option("--machine", "-m", help = "Machine name for location tracking").required()
    private val ipfs by option("--ipfs", help = "Kubo API URL (e.g. http://localhost:5001)").required()
    private val output by option("--output", "-o", help = "Output catalog file (optional)")
    private val db by option("--db", help = "Registry database path").default("sietch_registry.db")
    private val noSietchIgnore by option("--no-sietchignore", help = "Disable .sietchignore file processing (index everything)").flag()

    override fun run() = runBlocking {
        val root = File(scanPath)
        if (!root.exists() || !root.isDirectory) {
            echo("Error: '$scanPath' is not a valid directory", err = true)
            return@runBlocking
        }

        val ipfsClient = IpfsClient(ipfs)
        if (!ipfsClient.isAvailable()) {
            echo("Error: Kubo node at $ipfs is not reachable", err = true)
            ipfsClient.close()
            return@runBlocking
        }

        val (peerId, agent) = ipfsClient.nodeId()
        echo("Sietch v2.1 — IPFS Content Indexer")
        echo("Scanning:  ${root.absolutePath}")
        echo("Machine:   $machine")
        echo("IPFS node: $peerId ($agent)")
        echo()

        val registry = ContentLocationRegistry(Path.of(db))

        // Ensure global ignore file exists (seeds defaults on first run)
        if (!noSietchIgnore) ensureGlobalIgnore()

        val catalog = indexDirectoryWithCids(
            dir = root,
            ipfsClient = ipfsClient,
            registry = registry,
            machineName = machine,
            useSietchIgnore = !noSietchIgnore,
            progressCallback = { count, path ->
                if (count % 100 == 0L) {
                    echo("  ... $count files indexed")
                }
            }
        )

        echo()
        echo("Done. ${catalog.entries.size} files indexed")
        echo("Registry: $db (${registry.countCids()} unique CIDs, ${registry.countLocations()} locations)")

        if (output != null) {
            val outputFile = File(output!!)
            writeCidCatalog(catalog, outputFile)
            echo("Catalog:  ${outputFile.absolutePath}")
        }

        registry.close()
        ipfsClient.close()
    }
}
