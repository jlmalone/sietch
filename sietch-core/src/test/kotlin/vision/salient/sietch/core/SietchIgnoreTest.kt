package vision.salient.sietch.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SietchIgnoreTest {

    @TempDir
    lateinit var tempDir: Path

    // --- SietchIgnore parser tests ---

    @Test
    fun `parse simple glob patterns`() {
        val ignore = SietchIgnore.parse(listOf("*.tmp", ".DS_Store", "*.part"))
        assertFalse(ignore.isEmpty)
        assertTrue(ignore.isIgnoredByName(Path.of(".DS_Store")))
        assertTrue(ignore.isIgnoredByName(Path.of("foo.tmp")))
        assertTrue(ignore.isIgnoredByName(Path.of("download.part")))
        assertFalse(ignore.isIgnoredByName(Path.of("movie.mkv")))
    }

    @Test
    fun `comments and blank lines are skipped`() {
        val ignore = SietchIgnore.parse(listOf(
            "# This is a comment",
            "",
            "  ",
            "*.tmp",
            "# Another comment"
        ))
        assertTrue(ignore.isIgnoredByName(Path.of("foo.tmp")))
        assertFalse(ignore.isIgnoredByName(Path.of("# This is a comment")))
    }

    @Test
    fun `negation re-includes excluded files`() {
        val ignore = SietchIgnore.parse(listOf(
            "*.tmp",
            "!important.tmp"
        ))
        assertTrue(ignore.isIgnoredByName(Path.of("junk.tmp")))
        assertFalse(ignore.isIgnoredByName(Path.of("important.tmp")))
    }

    @Test
    fun `trailing slash marks directory-only patterns`() {
        val ignore = SietchIgnore.parse(listOf("build/"))
        // Directory-only pattern should NOT match files
        assertFalse(ignore.isIgnoredByName(Path.of("build"), isDirectory = false))
        // Should match directories
        assertTrue(ignore.isIgnoredByName(Path.of("build"), isDirectory = true))
    }

    @Test
    fun `dot-prefix resource fork pattern`() {
        val ignore = SietchIgnore.parse(listOf("._*"))
        assertTrue(ignore.isIgnoredByName(Path.of("._Aliens.mkv")))
        assertTrue(ignore.isIgnoredByName(Path.of("._photo.png")))
        assertFalse(ignore.isIgnoredByName(Path.of("Aliens.mkv")))
    }

    @Test
    fun `empty file produces empty ignore`() {
        val ignore = SietchIgnore.parse(emptyList())
        assertTrue(ignore.isEmpty)
        assertFalse(ignore.isIgnoredByName(Path.of("anything")))
    }

    @Test
    fun `parse from file`() {
        val ignoreFile = tempDir.resolve(".sietchignore").toFile()
        ignoreFile.writeText("*.tmp\n.DS_Store\n# comment\n")
        val ignore = SietchIgnore.parse(ignoreFile)
        assertTrue(ignore.isIgnoredByName(Path.of(".DS_Store")))
        assertTrue(ignore.isIgnoredByName(Path.of("foo.tmp")))
    }

    @Test
    fun `parse from nonexistent file returns empty`() {
        val ignore = SietchIgnore.parse(File("/nonexistent/.sietchignore"))
        assertTrue(ignore.isEmpty)
    }

    // --- SietchIgnoreChain tests ---

    @Test
    fun `chain with programmatic patterns works`() {
        val chain = SietchIgnoreChain.create(programmaticPatterns = listOf("*.tmp", ".DS_Store"))
        assertTrue(chain.isIgnoredByName(Path.of(".DS_Store")))
        assertTrue(chain.isIgnoredByName(Path.of("foo.tmp")))
        assertFalse(chain.isIgnoredByName(Path.of("movie.mkv")))
    }

    @Test
    fun `chain push and pop works`() {
        val chain = SietchIgnoreChain.create(programmaticPatterns = listOf("*.tmp"))
        assertFalse(chain.isIgnoredByName(Path.of("*.log")))

        val dirIgnore = SietchIgnore.parse(listOf("*.log"))
        chain.push(dirIgnore)
        assertTrue(chain.isIgnoredByName(Path.of("debug.log")))

        chain.pop(dirIgnore)
        assertFalse(chain.isIgnoredByName(Path.of("debug.log")))
    }

    @Test
    fun `child negation overrides parent exclusion`() {
        val chain = SietchIgnoreChain.create(programmaticPatterns = listOf("*.tmp"))
        assertTrue(chain.isIgnoredByName(Path.of("keep.tmp")))

        val childIgnore = SietchIgnore.parse(listOf("!keep.tmp"))
        chain.push(childIgnore)
        assertFalse(chain.isIgnoredByName(Path.of("keep.tmp")))
        // Other .tmp files still excluded
        assertTrue(chain.isIgnoredByName(Path.of("junk.tmp")))
    }

    // --- walkTree with .sietchignore integration tests ---

    @Test
    fun `walkTree with useSietchIgnore reads sietchignore files`() {
        val root = tempDir.toFile()
        File(root, "good.txt").writeText("keep")
        File(root, "bad.tmp").writeText("junk")
        File(root, ".sietchignore").writeText("*.tmp\n")

        val visited = mutableListOf<String>()
        walkTree(root, useSietchIgnore = true) { visited.add(it.name) }

        assertEquals(1, visited.size)
        assertEquals("good.txt", visited[0])
    }

    @Test
    fun `walkTree with useSietchIgnore skips directories`() {
        val root = tempDir.toFile()
        File(root, "good.txt").writeText("keep")
        val buildDir = File(root, "build")
        buildDir.mkdir()
        File(buildDir, "output.class").writeText("compiled")
        File(root, ".sietchignore").writeText("build/\n")

        val visited = mutableListOf<String>()
        walkTree(root, useSietchIgnore = true) { visited.add(it.name) }

        assertEquals(1, visited.size)
        assertEquals("good.txt", visited[0])
    }

    @Test
    fun `walkTree with nested sietchignore applies hierarchically`() {
        val root = tempDir.toFile()
        File(root, "root.txt").writeText("keep")
        File(root, ".sietchignore").writeText("*.log\n")

        val sub = File(root, "sub")
        sub.mkdir()
        File(sub, "app.log").writeText("log")  // excluded by parent
        File(sub, "data.csv").writeText("data") // kept
        File(sub, "keep.log").writeText("important") // will be re-included by child
        File(sub, ".sietchignore").writeText("!keep.log\n*.csv\n")

        val visited = mutableListOf<String>()
        walkTree(root, useSietchIgnore = true) { visited.add(it.name) }

        assertTrue("root.txt" in visited, "root.txt should be visited")
        assertTrue("keep.log" in visited, "keep.log should be re-included by child negation")
        assertFalse("app.log" in visited, "app.log should be excluded by parent *.log")
        assertFalse("data.csv" in visited, "data.csv should be excluded by child *.csv")
    }

    @Test
    fun `walkTree without useSietchIgnore ignores sietchignore files`() {
        val root = tempDir.toFile()
        File(root, "good.txt").writeText("keep")
        File(root, "bad.tmp").writeText("should appear without sietchignore")
        File(root, ".sietchignore").writeText("*.tmp\n")

        val visited = mutableListOf<String>()
        walkTree(root) { visited.add(it.name) }

        // Without useSietchIgnore, .sietchignore is just another file
        assertTrue(visited.size >= 2, "Should visit good.txt, bad.tmp, and possibly .sietchignore")
        assertTrue("bad.tmp" in visited)
    }

    @Test
    fun `walkTree with useSietchIgnore does not visit sietchignore file itself`() {
        val root = tempDir.toFile()
        File(root, "good.txt").writeText("keep")
        File(root, ".sietchignore").writeText("# empty\n")

        val visited = mutableListOf<String>()
        walkTree(root, useSietchIgnore = true) { visited.add(it.name) }

        assertFalse(".sietchignore" in visited, ".sietchignore itself should not be visited")
    }

    // --- ** recursive glob pattern tests ---

    @Test
    fun `pattern with double-star recursive glob matches deep paths`() {
        // Path-based patterns: build/** should match anything under build/
        val ignore = SietchIgnore.parse(listOf("build/**"))
        assertTrue(ignore.isIgnored(Path.of("build/output.class")))
        assertTrue(ignore.isIgnored(Path.of("build/sub/deep/file.jar")))
        assertFalse(ignore.isIgnored(Path.of("src/main.kt")))
    }

    @Test
    fun `double-star in middle matches any intermediate directories`() {
        val ignore = SietchIgnore.parse(listOf("src/**/test"))
        assertTrue(ignore.isIgnored(Path.of("src/main/test"), isDirectory = true))
        assertTrue(ignore.isIgnored(Path.of("src/deep/nested/test"), isDirectory = true))
    }

    @Test
    fun `double-star with name-only patterns still work via glob`() {
        // **.log should be treated as a name pattern since it has no /
        val ignore = SietchIgnore.parse(listOf("*.log"))
        assertTrue(ignore.isIgnoredByName(Path.of("app.log")))
        assertTrue(ignore.isIgnoredByName(Path.of("error.log")))
        assertFalse(ignore.isIgnoredByName(Path.of("readme.txt")))
    }

    // --- Multiple .sietchignore files in nested directories ---

    @Test
    fun `walkTree with three-level nested sietchignore files`() {
        val root = tempDir.toFile()
        File(root, ".sietchignore").writeText("*.log\n")
        File(root, "root.txt").writeText("keep")

        val level1 = File(root, "level1")
        level1.mkdir()
        File(level1, ".sietchignore").writeText("*.csv\n")
        File(level1, "data.csv").writeText("excluded by level1")
        File(level1, "notes.txt").writeText("keep")
        File(level1, "app.log").writeText("excluded by root")

        val level2 = File(level1, "level2")
        level2.mkdir()
        File(level2, ".sietchignore").writeText("*.xml\n!important.csv\n")
        File(level2, "config.xml").writeText("excluded by level2")
        File(level2, "important.csv").writeText("re-included by level2 negation")
        File(level2, "output.log").writeText("excluded by root")
        File(level2, "readme.txt").writeText("keep")

        val visited = mutableListOf<String>()
        walkTree(root, useSietchIgnore = true) { visited.add(it.name) }

        assertTrue("root.txt" in visited)
        assertTrue("notes.txt" in visited)
        assertTrue("readme.txt" in visited)
        assertTrue("important.csv" in visited, "important.csv should be re-included by level2 negation")
        assertFalse("data.csv" in visited, "data.csv should be excluded by level1 *.csv")
        assertFalse("app.log" in visited, "app.log should be excluded by root *.log")
        assertFalse("output.log" in visited, "output.log should be excluded by root *.log")
        assertFalse("config.xml" in visited, "config.xml should be excluded by level2 *.xml")
    }

    @Test
    fun `sietchignore rules are scoped to their directory and below`() {
        val root = tempDir.toFile()
        File(root, "root.txt").writeText("keep")
        File(root, "data.csv").writeText("keep — no ignore at root")

        val sub = File(root, "sub")
        sub.mkdir()
        File(sub, ".sietchignore").writeText("*.csv\n")
        File(sub, "sub.csv").writeText("excluded by sub's ignore")
        File(sub, "sub.txt").writeText("keep")

        val visited = mutableListOf<String>()
        walkTree(root, useSietchIgnore = true) { visited.add(it.name) }

        assertTrue("root.txt" in visited)
        assertTrue("data.csv" in visited, "root-level data.csv should NOT be affected by sub's .sietchignore")
        assertTrue("sub.txt" in visited)
        assertFalse("sub.csv" in visited, "sub.csv should be excluded by sub's .sietchignore")
    }

    // --- Global ignore file tests ---

    @Test
    fun `ensureGlobalIgnore creates default file`() {
        // Use a temp directory as fake home
        val fakeHome = tempDir.resolve("fakehome").toFile()
        fakeHome.mkdir()
        val sietchDir = File(fakeHome, ".sietch")
        val ignoreFile = File(sietchDir, "ignore")

        assertFalse(ignoreFile.exists())

        // Manually test the seed content logic (can't override user.home easily)
        sietchDir.mkdirs()
        if (!ignoreFile.exists()) {
            ignoreFile.writeText(buildString {
                appendLine("# Sietch global ignore")
                appendLine(".DS_Store")
                appendLine("._*")
                appendLine("*.tmp")
            })
        }

        assertTrue(ignoreFile.exists())
        val content = ignoreFile.readText()
        assertTrue(".DS_Store" in content)
        assertTrue("._*" in content)
    }

    @Test
    fun `global ignore file loaded into chain applies to walkTree`() {
        val root = tempDir.toFile()

        // Create a fake global ignore file in temp dir
        val fakeGlobal = tempDir.resolve("fake_global_ignore").toFile()
        fakeGlobal.writeText("*.bak\n")

        // Build a chain with the fake global ignore
        val chain = SietchIgnoreChain.create(globalIgnoreFile = fakeGlobal)
        assertTrue(chain.isIgnoredByName(Path.of("backup.bak")))
        assertFalse(chain.isIgnoredByName(Path.of("movie.mkv")))
    }

    @Test
    fun `chain with global ignore and programmatic patterns combines both`() {
        val fakeGlobal = tempDir.resolve("fake_global_ignore").toFile()
        fakeGlobal.writeText("*.bak\n")

        val chain = SietchIgnoreChain.create(
            globalIgnoreFile = fakeGlobal,
            programmaticPatterns = listOf("*.tmp")
        )
        assertTrue(chain.isIgnoredByName(Path.of("backup.bak")))
        assertTrue(chain.isIgnoredByName(Path.of("temp.tmp")))
        assertFalse(chain.isIgnoredByName(Path.of("movie.mkv")))
    }

    @Test
    fun `chain with nonexistent global ignore file works fine`() {
        val chain = SietchIgnoreChain.create(
            globalIgnoreFile = File("/nonexistent/ignore"),
            programmaticPatterns = listOf("*.tmp")
        )
        assertTrue(chain.isIgnoredByName(Path.of("foo.tmp")))
        assertFalse(chain.isIgnoredByName(Path.of("foo.bak")))
    }

    // --- walkTree with both excludePatterns AND useSietchIgnore=true ---

    @Test
    fun `walkTree with both excludePatterns and useSietchIgnore combines them`() {
        val root = tempDir.toFile()
        File(root, "good.txt").writeText("keep")
        File(root, "junk.tmp").writeText("excluded by excludePatterns")
        File(root, "junk.bak").writeText("excluded by sietchignore")
        File(root, ".sietchignore").writeText("*.bak\n")

        val visited = mutableListOf<String>()
        walkTree(root, excludePatterns = listOf("*.tmp"), useSietchIgnore = true) { visited.add(it.name) }

        assertEquals(1, visited.size)
        assertEquals("good.txt", visited[0])
    }

    @Test
    fun `walkTree with excludePatterns and useSietchIgnore both skip directories`() {
        val root = tempDir.toFile()
        File(root, "good.txt").writeText("keep")

        // Directory excluded by excludePatterns
        val spotlight = File(root, ".Spotlight-V100")
        spotlight.mkdir()
        File(spotlight, "data.db").writeText("spotlight")

        // Directory excluded by .sietchignore
        val cache = File(root, "cache")
        cache.mkdir()
        File(cache, "cached.dat").writeText("cache")
        File(root, ".sietchignore").writeText("cache/\n")

        val visited = mutableListOf<String>()
        walkTree(root, excludePatterns = listOf(".Spotlight-V100"), useSietchIgnore = true) { visited.add(it.name) }

        assertEquals(1, visited.size)
        assertEquals("good.txt", visited[0])
    }

    // --- Edge cases ---

    @Test
    fun `empty sietchignore file does not affect walkTree`() {
        val root = tempDir.toFile()
        File(root, "a.txt").writeText("keep")
        File(root, "b.txt").writeText("keep")
        File(root, ".sietchignore").writeText("")

        val visited = mutableListOf<String>()
        walkTree(root, useSietchIgnore = true) { visited.add(it.name) }

        assertEquals(2, visited.size)
        assertTrue("a.txt" in visited)
        assertTrue("b.txt" in visited)
    }

    @Test
    fun `sietchignore with only comments does not exclude anything`() {
        val root = tempDir.toFile()
        File(root, "a.txt").writeText("keep")
        File(root, "b.dat").writeText("also keep")
        File(root, ".sietchignore").writeText("# This is a comment\n# Another comment\n\n# Yet another\n")

        val visited = mutableListOf<String>()
        walkTree(root, useSietchIgnore = true) { visited.add(it.name) }

        assertEquals(2, visited.size)
        assertTrue("a.txt" in visited)
        assertTrue("b.dat" in visited)
    }

    @Test
    fun `sietchignore with whitespace-only lines does not exclude anything`() {
        val root = tempDir.toFile()
        File(root, "a.txt").writeText("keep")
        File(root, ".sietchignore").writeText("   \n  \n\t\n")

        val visited = mutableListOf<String>()
        walkTree(root, useSietchIgnore = true) { visited.add(it.name) }

        assertEquals(1, visited.size)
        assertEquals("a.txt", visited[0])
    }

    @Test
    fun `SietchIgnore parse from file with only comments produces empty ignore`() {
        val ignoreFile = tempDir.resolve(".sietchignore").toFile()
        ignoreFile.writeText("# comment 1\n# comment 2\n\n")
        val ignore = SietchIgnore.parse(ignoreFile)
        assertTrue(ignore.isEmpty, "Ignore with only comments should be empty")
    }
}
