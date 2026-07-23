package vision.salient.sietch.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalkTreeExcludeTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `walkTree with empty excludePatterns visits everything`() {
        val root = tempDir.toFile()
        File(root, "a.txt").writeText("hello")
        File(root, ".DS_Store").writeText("junk")
        File(root, "sub").mkdir()
        File(root, "sub/b.txt").writeText("world")

        val visited = mutableListOf<String>()
        walkTree(root, emptyList()) { visited.add(it.name) }

        assertEquals(3, visited.size)
        assertTrue(visited.containsAll(listOf("a.txt", ".DS_Store", "b.txt")))
    }

    @Test
    fun `walkTree skips matching files`() {
        val root = tempDir.toFile()
        File(root, "a.txt").writeText("hello")
        File(root, ".DS_Store").writeText("junk")
        File(root, "temp.tmp").writeText("temp")
        File(root, "sub").mkdir()
        File(root, "sub/b.txt").writeText("world")
        File(root, "sub/Thumbs.db").writeText("thumbs")

        val visited = mutableListOf<String>()
        walkTree(root, listOf(".DS_Store", "*.tmp", "Thumbs.db")) { visited.add(it.name) }

        assertEquals(2, visited.size)
        assertTrue(visited.containsAll(listOf("a.txt", "b.txt")))
    }

    @Test
    fun `walkTree skips matching directories`() {
        val root = tempDir.toFile()
        File(root, "a.txt").writeText("hello")

        // Create a .Spotlight-V100 directory with files inside
        val spotlight = File(root, ".Spotlight-V100")
        spotlight.mkdir()
        File(spotlight, "store.db").writeText("spotlight data")
        File(spotlight, "deep").mkdir()
        File(spotlight, "deep/index.dat").writeText("index")

        // Create .fseventsd directory
        val fseventsd = File(root, ".fseventsd")
        fseventsd.mkdir()
        File(fseventsd, "0000000001").writeText("event")

        // Normal subdirectory
        File(root, "movies").mkdir()
        File(root, "movies/Aliens.mkv").writeText("content")

        val visited = mutableListOf<String>()
        walkTree(root, listOf(".Spotlight-V100", ".fseventsd")) { visited.add(it.name) }

        assertEquals(2, visited.size)
        assertTrue(visited.containsAll(listOf("a.txt", "Aliens.mkv")))
    }

    @Test
    fun `walkTree with no patterns parameter visits everything (backward compat)`() {
        val root = tempDir.toFile()
        File(root, "a.txt").writeText("hello")
        File(root, ".DS_Store").writeText("junk")

        val visited = mutableListOf<String>()
        walkTree(root) { visited.add(it.name) }

        assertEquals(2, visited.size)
    }

    @Test
    fun `indexDirectoryRelative with excludePatterns omits matched files`() {
        val root = tempDir.toFile()
        File(root, "movie.mkv").writeText("movie data")
        File(root, ".DS_Store").writeText("junk")
        File(root, "sub").mkdir()
        File(root, "sub/doc.pdf").writeText("pdf data")
        File(root, "sub/temp.tmp").writeText("temp")

        val catalog = indexDirectoryRelative(root, "none", listOf(".DS_Store", "*.tmp"))

        assertEquals(2, catalog.entries.size)
        val paths = catalog.entries.map { it.path }
        assertTrue("movie.mkv" in paths)
        assertTrue(paths.any { it.endsWith("doc.pdf") })
        assertTrue(paths.none { it.contains(".DS_Store") })
        assertTrue(paths.none { it.endsWith(".tmp") })
    }

    @Test
    fun `walkTree skips both directories and files in combined pattern`() {
        val root = tempDir.toFile()
        File(root, "good.txt").writeText("keep")
        File(root, ".DS_Store").writeText("junk")

        val trashes = File(root, ".Trashes")
        trashes.mkdir()
        File(trashes, "garbage.bin").writeText("trash")

        val visited = mutableListOf<String>()
        walkTree(root, DEFAULT_EXCLUDE_PATTERNS) { visited.add(it.name) }

        assertEquals(1, visited.size)
        assertEquals("good.txt", visited[0])
    }
}
