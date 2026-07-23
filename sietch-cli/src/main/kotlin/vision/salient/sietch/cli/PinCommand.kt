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

class PinCommand : CliktCommand(
    name = "pin",
    help = "Add a file to IPFS (stored + pinned on the Kubo node) and register its location."
) {
    private val filePath by argument(help = "File or directory to pin")
    private val ipfs by option("--ipfs", help = "Kubo API URL").required()
    private val machine by option("--machine", "-m", help = "Machine name for location tracking").required()
    private val db by option("--db", help = "Registry database path").default("sietch_registry.db")

    override fun run() = runBlocking {
        val path = Path.of(filePath)
        if (!Files.exists(path)) {
            echo("Error: '$filePath' does not exist", err = true)
            return@runBlocking
        }

        val ipfsClient = IpfsClient(ipfs)
        if (!ipfsClient.isAvailable()) {
            echo("Error: Kubo node at $ipfs is not reachable", err = true)
            ipfsClient.close()
            return@runBlocking
        }

        val registry = ContentLocationRegistry(Path.of(db))

        if (Files.isDirectory(path)) {
            // Pin all files in directory
            val files = mutableListOf<Path>()
            Files.walk(path).filter { Files.isRegularFile(it) }.forEach { files.add(it) }

            echo("Pinning ${files.size} files from ${path.toAbsolutePath()}...")
            echo()

            var count = 0
            var totalSize = 0L
            for (file in files) {
                try {
                    val size = Files.size(file)
                    val cid = ipfsClient.add(file, pin = true)
                    registry.register(cid, machine, file.toAbsolutePath().toString(), size)
                    count++
                    totalSize += size
                    echo("  $cid  ${formatSize(size).padStart(10)}  ${file.fileName}")
                } catch (e: Exception) {
                    echo("  ERROR: ${file.fileName} — ${e.message}", err = true)
                }
            }

            echo()
            echo("Pinned $count files (${formatSize(totalSize)}) on IPFS node")
        } else {
            // Pin single file
            val size = Files.size(path)
            echo("Pinning: ${path.toAbsolutePath()} (${formatSize(size)})")

            val cid = ipfsClient.add(path, pin = true)
            registry.register(cid, machine, path.toAbsolutePath().toString(), size)

            val filename = path.fileName.toString()
            echo()
            echo("CID:      $cid")
            echo("Open:     ${ipfsClient.gatewayUrl(cid, filename)}")
            echo("Pinned and registered.")
        }

        registry.close()
        ipfsClient.close()
    }
}
