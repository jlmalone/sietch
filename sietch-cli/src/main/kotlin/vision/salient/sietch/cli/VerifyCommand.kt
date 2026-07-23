package vision.salient.sietch.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import vision.salient.sietch.core.registry.ContentLocationRegistry
import java.nio.file.Files
import java.nio.file.Path

class VerifyCommand : CliktCommand(
    name = "verify",
    help = "Verify that registered files still exist at their recorded locations."
) {
    private val machine by option("--machine", "-m", help = "Machine name to verify").required()
    private val db by option("--db", help = "Registry database path").default("sietch_registry.db")

    override fun run() {
        val registry = ContentLocationRegistry(Path.of(db))
        val locations = registry.getByMachine(machine)

        if (locations.isEmpty()) {
            echo("No content registered for machine '$machine'")
            registry.close()
            return
        }

        echo("Verifying ${locations.size} entries for '$machine'...")

        var verified = 0
        var missing = 0
        var errors = 0

        for (loc in locations) {
            try {
                val path = Path.of(loc.filePath)
                if (Files.exists(path) && Files.isReadable(path)) {
                    registry.updateVerified(loc.cid, loc.machineName)
                    verified++
                } else {
                    echo("  MISSING: ${loc.filePath} (CID: ${loc.cid})")
                    missing++
                }
            } catch (e: Exception) {
                echo("  ERROR: ${loc.filePath} — ${e.message}", err = true)
                errors++
            }
        }

        echo()
        echo("Results: $verified verified, $missing missing, $errors errors")

        registry.close()
    }
}
