package vision.salient.sietch.core.ipfs

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RawCidTest {
    @Test
    fun `empty bytes match the published IPFS raw CID vector`() {
        assertEquals(
            "bafkreihdwdcefgh4dqkjv67uzcmw7ojee6xedzdetojuzjevtenxquvyku",
            RawCid.fromBytes(byteArrayOf())
        )
    }

    @Test
    fun `bytes and precomputed digest produce the same CID`() {
        val bytes = "media-dag/node/v1".encodeToByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)

        assertEquals(RawCid.fromBytes(bytes), RawCid.fromSha256(digest))
    }

    @Test
    fun `non sha256 digest is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            RawCid.fromSha256(ByteArray(31))
        }
    }

    @Test
    fun `gateway base is explicit and normalized`() {
        val client = IpfsClient("http://127.0.0.1:5001", "https://gateway.example.test/base/")
        try {
            assertEquals(
                "https://gateway.example.test/base/ipfs/bafk-test",
                client.gatewayUrl("bafk-test")
            )
        } finally {
            client.close()
        }
    }
}
