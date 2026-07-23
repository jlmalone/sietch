package vision.salient.sietch.core

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher

/**
 * Parses and evaluates a single .sietchignore file using gitignore syntax.
 *
 * Supported syntax:
 * - `*.tmp` — glob match on filename
 * - `build/` — trailing slash = directory only (matched by name)
 * - `!important.tmp` — negation (re-include a previously excluded pattern)
 * - `#` — comment lines
 * - Blank lines ignored
 * - Leading `/` anchors the pattern to the .sietchignore's directory
 * - `**` in patterns matches any number of directories
 *
 * Patterns without `/` (other than trailing) match against filename only (like gitignore).
 * Patterns with `/` match against the relative path from the .sietchignore's directory.
 */
class SietchIgnore(
    val rules: List<IgnoreRule>,
    val sourceFile: File? = null
) {
    data class IgnoreRule(
        val pattern: String,
        val negated: Boolean,
        val directoryOnly: Boolean,
        val isPathPattern: Boolean,
        val nameMatcher: PathMatcher,   // matches against filename only
        val pathMatcher: PathMatcher?   // matches against relative path (null for name-only patterns)
    )

    companion object {
        /** Parse a .sietchignore file. */
        fun parse(file: File): SietchIgnore {
            if (!file.exists()) return SietchIgnore(emptyList(), file)
            return parse(file.readLines(), file)
        }

        /** Parse lines of gitignore-style content. */
        fun parse(lines: List<String>, sourceFile: File? = null): SietchIgnore {
            val rules = mutableListOf<IgnoreRule>()
            for (rawLine in lines) {
                val line = rawLine.trimEnd()
                if (line.isBlank() || line.startsWith("#")) continue

                var pattern = line
                val negated = pattern.startsWith("!")
                if (negated) pattern = pattern.substring(1)

                val directoryOnly = pattern.endsWith("/")
                if (directoryOnly) pattern = pattern.dropLast(1)

                val anchored = pattern.startsWith("/")
                if (anchored) pattern = pattern.substring(1)

                // A pattern is path-based if it contains / (after stripping leading/trailing)
                val isPathPattern = pattern.contains("/") || anchored

                try {
                    // Name matcher: always matches against the bare filename
                    val nameGlob = if (isPathPattern) {
                        // Path patterns: extract the last component for name matching
                        pattern.substringAfterLast("/")
                    } else {
                        pattern
                    }
                    val nameMatcher = FileSystems.getDefault().getPathMatcher("glob:$nameGlob")

                    // Path matcher: for path patterns, match against the full relative path
                    val pathMatcher = if (isPathPattern) {
                        val pathGlob = if (pattern.contains("**")) pattern else pattern
                        FileSystems.getDefault().getPathMatcher("glob:$pathGlob")
                    } else {
                        null // Name-only patterns don't need a path matcher
                    }

                    rules.add(IgnoreRule(
                        pattern = line,
                        negated = negated,
                        directoryOnly = directoryOnly,
                        isPathPattern = isPathPattern,
                        nameMatcher = nameMatcher,
                        pathMatcher = pathMatcher
                    ))
                } catch (_: Exception) {
                    System.err.println("  Warning: invalid .sietchignore pattern: $line")
                }
            }
            return SietchIgnore(rules, sourceFile)
        }

        /** Create an empty (no-op) ignore instance. */
        fun empty(): SietchIgnore = SietchIgnore(emptyList())
    }

    val isEmpty: Boolean get() = rules.isEmpty()

    /**
     * Check if a path should be ignored.
     * @param relativePath Path relative to the .sietchignore's directory
     * @param isDirectory Whether the path is a directory
     * @return true if the path should be ignored
     */
    fun isIgnored(relativePath: Path, isDirectory: Boolean = false): Boolean {
        var ignored = false
        for (rule in rules) {
            if (rule.directoryOnly && !isDirectory) continue

            val matches = if (rule.isPathPattern) {
                rule.pathMatcher?.matches(relativePath) ?: false
            } else {
                // Name-only pattern: match against the filename component
                val filename = relativePath.fileName ?: continue
                rule.nameMatcher.matches(filename)
            }

            if (matches) {
                ignored = !rule.negated
            }
        }
        return ignored
    }

    /**
     * Check if a filename should be ignored (simple name-only check).
     * @param filename Just the file/directory name (not a path)
     * @param isDirectory Whether this is a directory
     * @return true if the name should be ignored
     */
    fun isIgnoredByName(filename: Path, isDirectory: Boolean = false): Boolean {
        var ignored = false
        for (rule in rules) {
            if (rule.directoryOnly && !isDirectory) continue
            if (rule.isPathPattern) continue // path patterns don't apply to name-only checks

            if (rule.nameMatcher.matches(filename)) {
                ignored = !rule.negated
            }
        }
        return ignored
    }
}

/**
 * Hierarchical chain of SietchIgnore instances.
 * Global rules are checked first, then parent directories, then the current directory.
 * Later rules (more specific) override earlier ones. Negation in a child .sietchignore
 * can re-include something excluded by a parent.
 */
class SietchIgnoreChain private constructor(
    private val layers: MutableList<SietchIgnore>
) {
    companion object {
        /**
         * Create a chain starting with a global ignore file and programmatic patterns.
         * @param globalIgnoreFile The global ~/.sietch/ignore file (may not exist)
         * @param programmaticPatterns Additional patterns from config/API (e.g., CHOAM excludePatterns)
         */
        fun create(
            globalIgnoreFile: File? = null,
            programmaticPatterns: List<String> = emptyList()
        ): SietchIgnoreChain {
            val layers = mutableListOf<SietchIgnore>()

            // Layer 0: Programmatic patterns (lowest priority)
            if (programmaticPatterns.isNotEmpty()) {
                layers.add(SietchIgnore.parse(programmaticPatterns))
            }

            // Layer 1: Global ignore file
            if (globalIgnoreFile != null && globalIgnoreFile.exists()) {
                val global = SietchIgnore.parse(globalIgnoreFile)
                if (!global.isEmpty) layers.add(global)
            }

            return SietchIgnoreChain(layers)
        }
    }

    /** Push a directory-level .sietchignore onto the chain. */
    fun push(ignore: SietchIgnore) {
        if (!ignore.isEmpty) layers.add(ignore)
    }

    /** Pop the most recently pushed directory-level .sietchignore. */
    fun pop(ignore: SietchIgnore) {
        if (layers.isNotEmpty() && layers.last() === ignore) {
            layers.removeAt(layers.lastIndex)
        }
    }

    /** Check if a relative path should be ignored by any layer. */
    fun isIgnored(relativePath: Path, isDirectory: Boolean = false): Boolean {
        var ignored = false
        for (layer in layers) {
            for (rule in layer.rules) {
                if (rule.directoryOnly && !isDirectory) continue
                val matches = if (rule.isPathPattern) {
                    rule.pathMatcher?.matches(relativePath) ?: false
                } else {
                    val filename = relativePath.fileName ?: continue
                    rule.nameMatcher.matches(filename)
                }
                if (matches) {
                    ignored = !rule.negated
                }
            }
        }
        return ignored
    }

    /** Simple name-only check across all layers. */
    fun isIgnoredByName(filename: Path, isDirectory: Boolean = false): Boolean {
        var ignored = false
        for (layer in layers) {
            for (rule in layer.rules) {
                if (rule.directoryOnly && !isDirectory) continue
                if (rule.isPathPattern) continue
                if (rule.nameMatcher.matches(filename)) {
                    ignored = !rule.negated
                }
            }
        }
        return ignored
    }

    /** The number of active layers. */
    val size: Int get() = layers.size
}

/** Default global ignore file path. */
val GLOBAL_SIETCH_IGNORE = File(System.getProperty("user.home"), ".sietch/ignore")

/**
 * Ensure the global ignore file exists with sensible defaults.
 * Creates ~/.sietch/ignore seeded from DEFAULT_EXCLUDE_PATTERNS if it doesn't exist.
 * @return The global ignore file (may be newly created or pre-existing)
 */
fun ensureGlobalIgnore(): File {
    val dir = File(System.getProperty("user.home"), ".sietch")
    dir.mkdirs()
    val file = File(dir, "ignore")
    if (!file.exists()) {
        file.writeText(buildString {
            appendLine("# Sietch global ignore — applies to all indexing operations")
            appendLine("# Syntax is identical to .gitignore")
            appendLine()
            appendLine("# macOS metadata")
            appendLine(".DS_Store")
            appendLine(".Spotlight-V100/")
            appendLine(".fseventsd/")
            appendLine(".Trashes/")
            appendLine(".TemporaryItems/")
            appendLine(".DocumentRevisions-V100/")
            appendLine()
            appendLine("# macOS resource forks (AppleDouble on exFAT/NTFS)")
            appendLine("._*")
            appendLine()
            appendLine("# Windows metadata")
            appendLine("Thumbs.db")
            appendLine()
            appendLine("# Temp/partial files")
            appendLine("*.tmp")
            appendLine("*.part")
        })
    }
    return file
}
