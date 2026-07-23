package vision.salient.sietch.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import vision.salient.sietch.core.*
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CatalogCommand : CliktCommand(
    name = "catalog",
    help = "Export a backward-compatible flat-file catalog (existing Sietch format + optional CID column)."
) {
    private val scanPath by argument(help = "Directory to catalog")
    private val output by option("--output", "-o", help = "Output file path")
    private val hash by option("--hash", "-h", help = "Hash algorithm")
        .choice("sha256", "md5", "none").default("sha256")
    private val noSietchIgnore by option("--no-sietchignore", help = "Disable .sietchignore file processing").flag()

    override fun run() {
        val root = File(scanPath)
        if (!root.exists() || !root.isDirectory) {
            echo("Error: '$scanPath' is not a valid directory", err = true)
            return
        }

        val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val driveName = root.absolutePath.replace("/", "_").replace("\\", "_").trimEnd('_')
        val outputFile = File(output ?: "${driveName}_$date.txt")

        echo("Sietch v2.1 — File Catalog")
        echo("Scanning: ${root.absolutePath}")
        echo("Hash:     ${if (hash == "none") "disabled" else hash.uppercase()}")
        echo("Output:   ${outputFile.absolutePath}")
        echo()

        if (!noSietchIgnore) ensureGlobalIgnore()
        val catalog = indexDirectory(root, hash, useSietchIgnore = !noSietchIgnore)
        writeCatalog(catalog, outputFile)

        val totalSize = catalog.entries.sumOf { it.size }
        echo("Done. ${catalog.entries.size} files cataloged (${formatSize(totalSize)})")
        echo("Catalog: ${outputFile.absolutePath}")
    }
}
