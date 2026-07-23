package vision.salient.sietch

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.PrintWriter
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class IndexCommand : CliktCommand(
    name = "sietch",
    help = "Walk a directory tree and catalog every file with path, hash, and size."
) {
    private val scanPath by argument(help = "Directory to scan")
    private val output by option("--output", "-o", help = "Output file path (default: auto-generated)")
    private val hashAlgorithm by option("--hash", "-h", help = "Hash algorithm")
        .choice("sha256", "md5", "none").default("sha256")

    override fun run() {
        val root = File(scanPath)
        if (!root.exists() || !root.isDirectory) {
            echo("Error: '$scanPath' is not a valid directory", err = true)
            return
        }

        val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val driveName = root.absolutePath.replace("/", "_").replace("\\", "_").trimEnd('_')
        val outputFile = File(output ?: "${driveName}_$date.txt")

        echo("Sietch v2.0 — File Indexer")
        echo("Scanning: ${root.absolutePath}")
        echo("Hash:     ${if (hashAlgorithm == "none") "disabled" else hashAlgorithm.uppercase()}")
        echo("Output:   ${outputFile.absolutePath}")
        echo()

        var fileCount = 0L
        var totalSize = 0L
        var errorCount = 0L

        PrintWriter(outputFile, Charsets.UTF_8).use { writer ->
            writer.println("# Sietch catalog: ${root.absolutePath}")
            writer.println("# Date: $date")
            writer.println("# Hash: $hashAlgorithm")
            writer.println("# Format: path\\thash\\tsize")

            walkTree(root) { file ->
                try {
                    val hash = when (hashAlgorithm) {
                        "sha256" -> computeHash(file, "SHA-256")
                        "md5" -> computeHash(file, "MD5")
                        else -> "-"
                    }
                    writer.println("${file.absolutePath}\t$hash\t${file.length()}")
                    fileCount++
                    totalSize += file.length()
                    if (fileCount % 1000 == 0L) {
                        echo("  ... $fileCount files indexed")
                    }
                } catch (e: Exception) {
                    writer.println("${file.absolutePath}\tERROR:${e.message}\t${file.length()}")
                    errorCount++
                }
            }
        }

        echo()
        echo("Done. $fileCount files indexed (${formatSize(totalSize)})")
        if (errorCount > 0) echo("  $errorCount files had errors (permission denied, etc.)")
        echo("Catalog: ${outputFile.absolutePath}")
    }
}

/**
 * Walk a directory tree using NIO FileVisitor.
 * Does NOT follow symlinks — prevents infinite loops from circular links.
 * Gracefully skips directories and files that can't be read (NTFS on macOS, permission denied, etc.)
 */
fun walkTree(dir: File, action: (File) -> Unit) {
    Files.walkFileTree(dir.toPath(), setOf<FileVisitOption>(), Int.MAX_VALUE, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (attrs.isRegularFile) action(file.toFile())
            return FileVisitResult.CONTINUE
        }
        override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult {
            System.err.println("  Warning: skipping ${file}: ${exc?.message}")
            return FileVisitResult.CONTINUE
        }
        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            if (exc != null) System.err.println("  Warning: error reading directory ${dir}: ${exc.message}")
            return FileVisitResult.CONTINUE
        }
    })
}

fun computeHash(file: File, algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm)
    val buffer = ByteArray(8192)
    FileInputStream(file).use { fis ->
        var bytesRead: Int
        while (fis.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_099_511_627_776 -> "%.1f TB".format(bytes / 1_099_511_627_776.0)
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

fun main(args: Array<String>) {
    IndexCommand().main(args)
}
