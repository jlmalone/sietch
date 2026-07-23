package vision.salient.sietch.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.runBlocking
import vision.salient.sietch.core.ipfs.IpfsClient
import vision.salient.sietch.core.registry.ContentLocationRegistry
import vision.salient.sietch.core.resolver.ContentResolver
import vision.salient.sietch.core.resolver.ResolvedContent
import java.nio.file.Path

class ResolveCommand : CliktCommand(
    name = "resolve",
    help = "Resolve a CID to a file location."
) {
    private val cid by argument(help = "The CID to resolve")
    private val db by option("--db", help = "Registry database path").default("sietch_registry.db")
    private val ipfs by option("--ipfs", help = "Kubo API URL")
    private val machine by option("--machine", "-m", help = "Current machine name").default("local")

    override fun run() = runBlocking {
        val registry = ContentLocationRegistry(Path.of(db))
        val ipfsClient = ipfs?.let { IpfsClient(it) }
        val resolver = ContentResolver(registry, ipfsClient, machine)

        when (val result = resolver.resolve(cid)) {
            is ResolvedContent.LocalFile -> {
                echo("Resolved: LOCAL FILE")
                echo("  Path: ${result.path}")
            }
            is ResolvedContent.RemoteGateway -> {
                echo("Resolved: REMOTE (via IPFS gateway)")
                echo("  URL: ${result.url}")
            }
            is ResolvedContent.NotAvailable -> {
                echo("Not available locally")
                if (result.knownLocations.isNotEmpty()) {
                    echo("Known locations:")
                    for (loc in result.knownLocations) {
                        echo("  ${loc.machineName}: ${loc.filePath}")
                    }
                } else {
                    echo("No known locations in registry")
                }
            }
        }

        registry.close()
        ipfsClient?.close()
        Unit
    }
}
