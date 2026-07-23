package vision.salient.sietch.core

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Default exclude patterns for macOS/system metadata files.
 * These change CIDs across runs and waste IPFS hashing time.
 */
val DEFAULT_EXCLUDE_PATTERNS = listOf(
    ".DS_Store", "Thumbs.db",
    ".Spotlight-V100", ".fseventsd", ".Trashes",
    ".TemporaryItems", ".DocumentRevisions-V100",
    "*.tmp", "*.part"
)

/**
 * Core Sietch library — file indexing, hashing, and catalog operations.
 * No CLI dependencies — safe to use as a library from other projects.
 */

data class CatalogEntry(
    val path: String,
    val hash: String,
    val size: Long
)

data class SietchCatalog(
    val rootPath: String,
    val date: String,
    val hashAlgorithm: String,
    val entries: List<CatalogEntry>
)

/**
 * Walk a directory tree using NIO FileVisitor.
 * Does NOT follow symlinks — prevents infinite loops from circular links.
 * Gracefully skips directories and files that can't be read (NTFS on macOS, permission denied, etc.)
 *
 * @param excludePatterns Glob patterns for files/directories to skip (e.g. ".DS_Store", ".Spotlight-V100", "*.tmp").
 *   Directory names matching a pattern cause SKIP_SUBTREE. File names matching a pattern skip the action callback.
 * @param useSietchIgnore When true, checks for .sietchignore files in each directory and applies
 *   gitignore-style rules hierarchically. Also loads ~/.sietch/ignore as a global ignore file.
 */
fun walkTree(
    dir: File,
    excludePatterns: List<String> = emptyList(),
    useSietchIgnore: Boolean = false,
    action: (File) -> Unit
) {
    val matchers = excludePatterns.map { pattern ->
        FileSystems.getDefault().getPathMatcher("glob:$pattern")
    }

    fun shouldExcludeByPattern(name: Path): Boolean = matchers.any { it.matches(name) }

    // Set up .sietchignore chain if enabled
    val ignoreChain: SietchIgnoreChain? = if (useSietchIgnore) {
        val globalFile = GLOBAL_SIETCH_IGNORE
        SietchIgnoreChain.create(
            globalIgnoreFile = globalFile,
            programmaticPatterns = excludePatterns
        )
    } else null

    val rootPath = dir.toPath()
    // Track pushed ignores per directory for cleanup
    val dirIgnoreStack = mutableMapOf<Path, SietchIgnore>()

    Files.walkFileTree(dir.toPath(), setOf<FileVisitOption>(), Int.MAX_VALUE, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (!attrs.isRegularFile) return FileVisitResult.CONTINUE
            // Skip .sietchignore files themselves
            if (file.fileName.toString() == ".sietchignore") return FileVisitResult.CONTINUE

            if (ignoreChain != null) {
                val relativePath = rootPath.relativize(file)
                if (ignoreChain.isIgnoredByName(file.fileName, false)) return FileVisitResult.CONTINUE
                if (ignoreChain.isIgnored(relativePath, false)) return FileVisitResult.CONTINUE
            } else {
                if (shouldExcludeByPattern(file.fileName)) return FileVisitResult.CONTINUE
            }

            action(file.toFile())
            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult {
            System.err.println("  Warning: skipping ${file}: ${exc?.message}")
            return FileVisitResult.CONTINUE
        }

        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (ignoreChain != null) {
                val relativePath = rootPath.relativize(dir)
                // Check name-based exclusion first
                if (dir != rootPath && ignoreChain.isIgnoredByName(dir.fileName, true)) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                // Check path-based exclusion
                if (dir != rootPath && ignoreChain.isIgnored(relativePath, true)) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                // Load .sietchignore in this directory
                val ignoreFile = dir.resolve(".sietchignore").toFile()
                if (ignoreFile.exists()) {
                    val ignore = SietchIgnore.parse(ignoreFile)
                    if (!ignore.isEmpty) {
                        ignoreChain.push(ignore)
                        dirIgnoreStack[dir] = ignore
                    }
                }
            } else {
                if (shouldExcludeByPattern(dir.fileName)) return FileVisitResult.SKIP_SUBTREE
            }
            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            if (exc != null) System.err.println("  Warning: error reading directory ${dir}: ${exc.message}")
            // Pop the .sietchignore for this directory if one was pushed
            val pushed = dirIgnoreStack.remove(dir)
            if (pushed != null) {
                ignoreChain?.pop(pushed)
            }
            return FileVisitResult.CONTINUE
        }
    })
}

/**
 * Compute a cryptographic hash of a file.
 * @param file The file to hash
 * @param algorithm "SHA-256" or "MD5"
 * @return lowercase hex string
 */
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

/**
 * Index a directory and return a catalog of all files.
 * @param dir Directory to scan
 * @param hashAlgorithm "sha256", "md5", or "none"
 * @return SietchCatalog with all file entries
 */
fun indexDirectory(
    dir: File,
    hashAlgorithm: String = "sha256",
    excludePatterns: List<String> = emptyList(),
    useSietchIgnore: Boolean = false
): SietchCatalog {
    val entries = mutableListOf<CatalogEntry>()
    val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    walkTree(dir, excludePatterns, useSietchIgnore) { file ->
        try {
            val hash = when (hashAlgorithm) {
                "sha256" -> computeHash(file, "SHA-256")
                "md5" -> computeHash(file, "MD5")
                else -> "-"
            }
            entries.add(CatalogEntry(
                path = file.absolutePath,
                hash = hash,
                size = file.length()
            ))
        } catch (e: Exception) {
            entries.add(CatalogEntry(
                path = file.absolutePath,
                hash = "ERROR:${e.message}",
                size = file.length()
            ))
        }
    }

    return SietchCatalog(
        rootPath = dir.absolutePath,
        date = date,
        hashAlgorithm = hashAlgorithm,
        entries = entries
    )
}

/**
 * Index a directory and return entries with relative paths.
 * Useful for comparison between different mount points.
 */
fun indexDirectoryRelative(
    dir: File,
    hashAlgorithm: String = "sha256",
    excludePatterns: List<String> = emptyList(),
    useSietchIgnore: Boolean = false
): SietchCatalog {
    val entries = mutableListOf<CatalogEntry>()
    val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val rootPath = dir.toPath()

    walkTree(dir, excludePatterns, useSietchIgnore) { file ->
        try {
            val relativePath = rootPath.relativize(file.toPath()).toString()
            val hash = when (hashAlgorithm) {
                "sha256" -> computeHash(file, "SHA-256")
                "md5" -> computeHash(file, "MD5")
                else -> "-"
            }
            entries.add(CatalogEntry(
                path = relativePath,
                hash = hash,
                size = file.length()
            ))
        } catch (e: Exception) {
            val relativePath = rootPath.relativize(file.toPath()).toString()
            entries.add(CatalogEntry(
                path = relativePath,
                hash = "ERROR:${e.message}",
                size = file.length()
            ))
        }
    }

    return SietchCatalog(
        rootPath = dir.absolutePath,
        date = date,
        hashAlgorithm = hashAlgorithm,
        entries = entries
    )
}

/**
 * Write a catalog to a file in Sietch format.
 */
fun writeCatalog(catalog: SietchCatalog, outputFile: File) {
    outputFile.printWriter(Charsets.UTF_8).use { writer ->
        writer.println("# Sietch catalog: ${catalog.rootPath}")
        writer.println("# Date: ${catalog.date}")
        writer.println("# Hash: ${catalog.hashAlgorithm}")
        writer.println("# Format: path\\thash\\tsize")
        for (entry in catalog.entries) {
            writer.println("${entry.path}\t${entry.hash}\t${entry.size}")
        }
    }
}

/**
 * Parse a Sietch catalog file into a SietchCatalog object.
 */
fun parseCatalog(catalogFile: File): SietchCatalog {
    val lines = catalogFile.readLines()
    var rootPath = ""
    var date = ""
    var hashAlgorithm = ""
    val entries = mutableListOf<CatalogEntry>()

    for (line in lines) {
        if (line.startsWith("# Sietch catalog:")) {
            rootPath = line.substringAfter("# Sietch catalog:").trim()
        } else if (line.startsWith("# Date:")) {
            date = line.substringAfter("# Date:").trim()
        } else if (line.startsWith("# Hash:")) {
            hashAlgorithm = line.substringAfter("# Hash:").trim()
        } else if (line.startsWith("#")) {
            continue
        } else if (line.isNotBlank()) {
            val parts = line.split("\t")
            if (parts.size >= 3) {
                entries.add(CatalogEntry(
                    path = parts[0],
                    hash = parts[1],
                    size = parts[2].toLongOrNull() ?: 0
                ))
            }
        }
    }

    return SietchCatalog(
        rootPath = rootPath,
        date = date,
        hashAlgorithm = hashAlgorithm,
        entries = entries
    )
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_099_511_627_776 -> "%.1f TB".format(bytes / 1_099_511_627_776.0)
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
