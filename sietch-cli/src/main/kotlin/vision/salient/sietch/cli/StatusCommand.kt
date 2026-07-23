package vision.salient.sietch.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.runBlocking
import vision.salient.sietch.core.ipfs.IpfsClient
import vision.salient.sietch.core.registry.ContentLocationRegistry
import java.nio.file.Files
import java.nio.file.Path

class StatusCommand : CliktCommand(
    name = "status",
    help = "Show IPFS node status and registry summary."
) {
    private val ipfs by option("--ipfs", help = "Kubo API URL")
    private val db by option("--db", help = "Registry database path").default("sietch_registry.db")

    override fun run() = runBlocking {
        echo("Sietch v2.1 — Status")
        echo()

        // Registry status
        val dbPath = Path.of(db)
        if (Files.exists(dbPath)) {
            val registry = ContentLocationRegistry(dbPath)
            echo("Registry: $db")
            echo("  Unique CIDs: ${registry.countCids()}")
            echo("  Total locations: ${registry.countLocations()}")
            registry.close()
        } else {
            echo("Registry: not found at $db")
        }

        echo()

        // IPFS node status
        if (ipfs != null) {
            val ipfsClient = IpfsClient(ipfs!!)
            if (ipfsClient.isAvailable()) {
                val (peerId, agent) = ipfsClient.nodeId()
                echo("IPFS Node: $ipfs")
                echo("  Peer ID: $peerId")
                echo("  Agent:   $agent")
                echo("  Status:  ONLINE")
            } else {
                echo("IPFS Node: $ipfs")
                echo("  Status:  OFFLINE / UNREACHABLE")
            }
            ipfsClient.close()
        } else {
            echo("IPFS Node: not configured (use --ipfs to specify)")
        }
    }
}
