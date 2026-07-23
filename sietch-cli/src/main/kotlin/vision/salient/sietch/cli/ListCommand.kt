package vision.salient.sietch.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import vision.salient.sietch.core.formatSize
import vision.salient.sietch.core.registry.ContentLocationRegistry
import java.nio.file.Path

class ListCommand : CliktCommand(
    name = "list",
    help = "List all content registered on a machine."
) {
    private val machine by option("--machine", "-m", help = "Machine name to list content for").required()
    private val db by option("--db", help = "Registry database path").default("sietch_registry.db")

    override fun run() {
        val registry = ContentLocationRegistry(Path.of(db))
        val locations = registry.getByMachine(machine)

        if (locations.isEmpty()) {
            echo("No content registered for machine '$machine'")
            registry.close()
            return
        }

        echo("Content on '$machine': ${locations.size} entries")
        echo()

        for (loc in locations) {
            val sizeStr = loc.fileSize?.let { formatSize(it) } ?: "?"
            val verified = loc.verifiedAt?.toString()?.take(10) ?: "never"
            echo("  ${loc.cid}  ${sizeStr.padStart(10)}  verified=$verified  ${loc.filePath}")
        }

        val totalSize = locations.mapNotNull { it.fileSize }.sum()
        echo()
        echo("Total: ${locations.size} files, ${formatSize(totalSize)}")

        registry.close()
    }
}
