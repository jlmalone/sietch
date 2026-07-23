package vision.salient.sietch.core.ipfs

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlinx.coroutines.cancel
import java.nio.file.Path
import kotlin.io.path.name

/**
 * Thin HTTP client for the Kubo IPFS API.
 *
 * All CID computation is delegated to Kubo so we get 100% IPFS-compatible
 * CIDs (UnixFS DAG chunking) without reimplementing in Kotlin.
 *
 * @param apiUrl Base URL of the Kubo API, e.g. "http://localhost:5001"
 */
class IpfsClient(private val apiUrl: String) {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        engine {
            // Large media files (40-90 GB) can take 30+ minutes to stream to Kubo
            requestTimeout = 3_600_000 // 1 hour
        }
    }

    // Ensure JVM can exit even if close() doesn't fully terminate CIO threads
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { client.close() }
        })
    }

    @Serializable
    private data class AddResponse(
        val Name: String,
        val Hash: String,
        val Size: String
    )

    @Serializable
    private data class IdResponse(
        val ID: String,
        val AgentVersion: String? = null
    )

    /**
     * Add a file to IPFS. Returns CIDv1.
     * The file is uploaded to the Kubo node and pinned by default.
     * Streams the file to avoid loading it entirely into memory.
     */
    suspend fun add(file: Path, pin: Boolean = true): String {
        val fileSize = Files.size(file)
        val response = client.post("$apiUrl/api/v0/add") {
            parameter("pin", pin.toString())
            parameter("cid-version", "1")
            setBody(MultiPartFormDataContent(formData {
                append("file", ChannelProvider(fileSize) {
                    ByteReadChannel(Files.newInputStream(file).asSource().buffered())
                }, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                })
            }))
        }
        val body = response.bodyAsText()
        val parsed = json.decodeFromString<AddResponse>(body)
        return parsed.Hash
    }

    /**
     * Compute the CID for a file without storing it on the IPFS node.
     * Uses Kubo's --only-hash mode for hash-only computation.
     * Streams the file to avoid loading it entirely into memory.
     */
    suspend fun computeCid(file: Path): String {
        val fileSize = Files.size(file)
        val response = client.post("$apiUrl/api/v0/add") {
            parameter("only-hash", "true")
            parameter("cid-version", "1")
            setBody(MultiPartFormDataContent(formData {
                append("file", ChannelProvider(fileSize) {
                    ByteReadChannel(Files.newInputStream(file).asSource().buffered())
                }, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                })
            }))
        }
        val body = response.bodyAsText()
        val parsed = json.decodeFromString<AddResponse>(body)
        return parsed.Hash
    }

    /**
     * Retrieve file content by CID from the IPFS network.
     */
    suspend fun cat(cid: String): ByteArray {
        val response = client.post("$apiUrl/api/v0/cat") {
            parameter("arg", cid)
        }
        return response.readRawBytes()
    }

    /**
     * Pin content on this IPFS node so it isn't garbage collected.
     */
    suspend fun pin(cid: String) {
        client.post("$apiUrl/api/v0/pin/add") {
            parameter("arg", cid)
        }
    }

    /**
     * Unpin content on this IPFS node, allowing garbage collection.
     */
    suspend fun unpin(cid: String) {
        client.post("$apiUrl/api/v0/pin/rm") {
            parameter("arg", cid)
        }
    }

    /**
     * Check if the Kubo node is reachable and responding.
     */
    suspend fun isAvailable(): Boolean {
        return try {
            val response = client.post("$apiUrl/api/v0/id")
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the peer ID and agent version of the connected Kubo node.
     */
    suspend fun nodeId(): Pair<String, String> {
        val response = client.post("$apiUrl/api/v0/id")
        val body = response.bodyAsText()
        val parsed = json.decodeFromString<IdResponse>(body)
        return parsed.ID to (parsed.AgentVersion ?: "unknown")
    }

    /**
     * Construct a gateway URL for a CID.
     * This doesn't make a network call — just builds the URL.
     * If filename is provided, appends ?filename= so browsers save with a real name.
     */
    fun gatewayUrl(cid: String, filename: String? = null): String {
        val gatewayBase = apiUrl.replace(":5001", ":8080")
        val base = "$gatewayBase/ipfs/$cid"
        return if (filename != null) "$base?filename=$filename" else base
    }

    /**
     * Fetch content by CID via the IPFS gateway and stream it directly to a file.
     * Uses HTTP GET to the gateway (port 8080) — handles large files without loading into memory.
     *
     * @return the number of bytes written
     */
    suspend fun fetchToFile(cid: String, outputPath: Path): Long {
        val gatewayBase = apiUrl.replace(":5001", ":8080")
        val response = client.get("$gatewayBase/ipfs/$cid")
        val channel = response.bodyAsChannel()
        var totalBytes = 0L
        Files.newOutputStream(outputPath).use { out ->
            val buffer = ByteArray(8192)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer)
                if (read > 0) {
                    out.write(buffer, 0, read)
                    totalBytes += read
                }
            }
        }
        return totalBytes
    }

    /**
     * Compute CID via the `ipfs` CLI binary instead of the HTTP API.
     * Zero memory leak risk — each invocation is a separate process.
     * Preferred for bulk scanning of large drives (100K+ files).
     *
     * @param ipfsBinary Path to the ipfs binary (default: "ipfs", found via PATH)
     */
    fun computeCidViaCli(file: Path, ipfsBinary: String = "ipfs"): String {
        val process = ProcessBuilder(
            ipfsBinary, "add", "--only-hash", "--cid-version=1", "-Q", file.toString()
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("ipfs add failed (exit $exitCode): $output")
        }
        return output
    }

    fun close() {
        client.close()
        // Cancel the client's coroutine scope to stop lingering CIO selector threads
        // This prevents the JVM from hanging after the main task completes
        runCatching { client.coroutineContext.cancel() }
    }
}
