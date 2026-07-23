package vision.salient.sietch.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.coroutines.runBlocking
import vision.salient.sietch.core.formatSize
import vision.salient.sietch.core.ipfs.IpfsClient
import vision.salient.sietch.core.registry.ContentLocationRegistry
import java.nio.file.Files
import java.nio.file.Path

class FetchCommand : CliktCommand(
    name = "fetch",
    help = "Fetch content by CID from the IPFS gateway and save to a local file."
) {
    private val cid by argument(help = "The CID to fetch")
    private val output by option("--output", "-o", help = "Output file path").required()
    private val ipfs by option("--ipfs", help = "Kubo API URL").required()
    private val db by option("--db", help = "Registry database path").default("sietch_registry.db")
    private val machine by option("--machine", "-m", help = "Current machine name").default("local")

    override fun run() = runBlocking {
        val outputPath = Path.of(output)
        val ipfsClient = IpfsClient(ipfs)

        if (!ipfsClient.isAvailable()) {
            echo("Error: Kubo node at $ipfs is not reachable", err = true)
            ipfsClient.close()
            return@runBlocking
        }

        // Show known locations from registry if available
        val dbPath = Path.of(db)
        if (Files.exists(dbPath)) {
            val registry = ContentLocationRegistry(dbPath)
            val locations = registry.getLocations(cid)
            if (locations.isNotEmpty()) {
                echo("Known locations:")
                for (loc in locations) {
                    val sizeStr = loc.fileSize?.let { formatSize(it) } ?: "?"
                    echo("  ${loc.machineName}: ${loc.filePath} ($sizeStr)")
                }
                echo()
            }
            registry.close()
        }

        echo("Fetching $cid from IPFS gateway...")
        echo("Gateway: ${ipfsClient.gatewayUrl(cid)}")

        val bytes = ipfsClient.fetchToFile(cid, outputPath)

        echo()
        echo("Saved: ${outputPath.toAbsolutePath()} (${formatSize(bytes)})")

        // Register the new local location
        if (Files.exists(dbPath)) {
            val registry = ContentLocationRegistry(dbPath)
            registry.register(cid, machine, outputPath.toAbsolutePath().toString(), bytes)
            echo("Registered new location: $machine:${outputPath.toAbsolutePath()}")
            registry.close()
        }

        ipfsClient.close()
    }
}
