package vision.salient.sietch.core.resolver

import vision.salient.sietch.core.ipfs.IpfsClient
import vision.salient.sietch.core.registry.ContentLocation
import vision.salient.sietch.core.registry.ContentLocationRegistry
import java.nio.file.Files
import java.nio.file.Path

/**
 * Given a CID, find and return the content.
 *
 * Resolution order:
 * 1. Check registry for locations on current machine → direct file access
 * 2. Check registry for locations on other machines → construct IPFS gateway URL
 * 3. Try ipfsClient.cat(cid) as last resort (full IPFS network retrieval)
 *
 * @param registry The content location registry to query
 * @param ipfsClient Optional IPFS client for gateway/network retrieval
 * @param localMachineName The name of the machine we're running on
 */
class ContentResolver(
    private val registry: ContentLocationRegistry,
    private val ipfsClient: IpfsClient?,
    private val localMachineName: String
) {

    /**
     * Resolve a CID to accessible content.
     */
    suspend fun resolve(cid: String): ResolvedContent {
        val locations = registry.getLocations(cid)

        // 1. Check for local file
        val localLocations = locations.filter { it.machineName == localMachineName }
        for (loc in localLocations) {
            val path = Path.of(loc.filePath)
            if (Files.exists(path) && Files.isReadable(path)) {
                return ResolvedContent.LocalFile(path)
            }
        }

        // 2. Check for remote locations → gateway URL
        val remoteLocations = locations.filter { it.machineName != localMachineName }
        if (remoteLocations.isNotEmpty() && ipfsClient != null) {
            return ResolvedContent.RemoteGateway(ipfsClient.gatewayUrl(cid))
        }

        // 3. Try IPFS network retrieval
        if (ipfsClient != null) {
            return try {
                if (ipfsClient.isAvailable()) {
                    ResolvedContent.RemoteGateway(ipfsClient.gatewayUrl(cid))
                } else {
                    ResolvedContent.NotAvailable(cid, locations)
                }
            } catch (e: Exception) {
                ResolvedContent.NotAvailable(cid, locations)
            }
        }

        return ResolvedContent.NotAvailable(cid, locations)
    }

    /**
     * Resolve with legacy path fallback for the migration period.
     * If a CID is available, use content-addressed resolution.
     * If only a legacy path is available, try to access it directly.
     */
    suspend fun resolveWithFallback(cid: String?, legacyPath: String?): ResolvedContent {
        // Prefer CID-based resolution
        if (cid != null) {
            val result = resolve(cid)
            if (result !is ResolvedContent.NotAvailable) {
                return result
            }
        }

        // Fall back to legacy path
        if (legacyPath != null) {
            val path = Path.of(legacyPath)
            if (Files.exists(path) && Files.isReadable(path)) {
                return ResolvedContent.LocalFile(path)
            }
        }

        return ResolvedContent.NotAvailable(
            cid ?: "unknown",
            emptyList()
        )
    }
}

sealed class ResolvedContent {
    /** Content found as a local file on this machine */
    data class LocalFile(val path: Path) : ResolvedContent()

    /** Content available via IPFS gateway URL */
    data class RemoteGateway(val url: String) : ResolvedContent()

    /** Content not currently accessible */
    data class NotAvailable(
        val cid: String,
        val knownLocations: List<ContentLocation>
    ) : ResolvedContent()
}
