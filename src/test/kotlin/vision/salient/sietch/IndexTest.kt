package vision.salient.sietch

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class IndexTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `walkTree visits all files recursively`() {
        val root = tempDir.toFile()
        File(root, "a.txt").writeText("hello")
        File(root, "sub").mkdir()
        File(root, "sub/b.txt").writeText("world")
        File(root, "sub/deep").mkdir()
        File(root, "sub/deep/c.txt").writeText("!")

        val visited = mutableListOf<String>()
        walkTree(root) { visited.add(it.name) }

        assertEquals(3, visited.size)
        assertTrue(visited.containsAll(listOf("a.txt", "b.txt", "c.txt")))
    }

    @Test
    fun `walkTree handles empty directories`() {
        val root = tempDir.toFile()
        File(root, "empty").mkdir()

        val visited = mutableListOf<String>()
        walkTree(root) { visited.add(it.name) }

        assertTrue(visited.isEmpty())
    }

    @Test
    fun `computeHash SHA-256 produces consistent results`() {
        val file = File(tempDir.toFile(), "test.txt")
        file.writeText("hello world")

        val hash1 = computeHash(file, "SHA-256")
        val hash2 = computeHash(file, "SHA-256")

        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length) // SHA-256 = 64 hex chars
    }

    @Test
    fun `computeHash MD5 produces consistent results`() {
        val file = File(tempDir.toFile(), "test.txt")
        file.writeText("hello world")

        val hash = computeHash(file, "MD5")

        assertEquals(32, hash.length) // MD5 = 32 hex chars
    }

    @Test
    fun `computeHash SHA-256 known value`() {
        val file = File(tempDir.toFile(), "known.txt")
        file.writeText("hello world")

        val hash = computeHash(file, "SHA-256")

        // Known SHA-256 of "hello world"
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", hash)
    }

    @Test
    fun `computeHash MD5 known value`() {
        val file = File(tempDir.toFile(), "known.txt")
        file.writeText("hello world")

        val hash = computeHash(file, "MD5")

        // Known MD5 of "hello world"
        assertEquals("5eb63bbbe01eeed093cb22bb8f5acdc3", hash)
    }

    @Test
    fun `formatSize formats bytes correctly`() {
        assertEquals("0 B", formatSize(0))
        assertEquals("512 B", formatSize(512))
        assertEquals("1.0 KB", formatSize(1024))
        assertEquals("1.5 MB", formatSize(1_572_864))
        assertEquals("2.0 GB", formatSize(2_147_483_648))
        assertEquals("1.0 TB", formatSize(1_099_511_627_776))
    }

    @Test
    fun `IndexCommand produces valid output file`() {
        val root = tempDir.toFile()
        val dataDir = File(root, "data").apply { mkdir() }
        File(dataDir, "file1.txt").writeText("content one")
        File(dataDir, "file2.txt").writeText("content two")

        val outputFile = File(root, "catalog.txt")

        IndexCommand().parse(listOf(
            dataDir.absolutePath,
            "--output", outputFile.absolutePath,
            "--hash", "sha256"
        ))

        assertTrue(outputFile.exists())
        val lines = outputFile.readLines()

        // Header lines
        assertTrue(lines[0].startsWith("# Sietch catalog:"))
        assertTrue(lines[1].startsWith("# Date:"))
        assertTrue(lines[2].contains("sha256"))
        assertTrue(lines[3].contains("Format:"))

        // Data lines
        val dataLines = lines.drop(4)
        assertEquals(2, dataLines.size)
        for (line in dataLines) {
            val parts = line.split("\t")
            assertEquals(3, parts.size) // path, hash, size
            assertEquals(64, parts[1].length) // SHA-256 hex
            assertTrue(parts[2].toLong() > 0)
        }
    }

    @Test
    fun `IndexCommand with hash none skips hashing`() {
        val root = tempDir.toFile()
        val dataDir = File(root, "data").apply { mkdir() }
        File(dataDir, "file.txt").writeText("data")

        val outputFile = File(root, "catalog.txt")

        IndexCommand().parse(listOf(
            dataDir.absolutePath,
            "--output", outputFile.absolutePath,
            "--hash", "none"
        ))

        val dataLines = outputFile.readLines().drop(4)
        assertEquals(1, dataLines.size)
        val parts = dataLines[0].split("\t")
        assertEquals("-", parts[1]) // No hash
    }
}
