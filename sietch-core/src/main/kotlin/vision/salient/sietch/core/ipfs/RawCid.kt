package vision.salient.sietch.core.ipfs

import java.security.MessageDigest

/**
 * Deterministic CIDv1/raw/sha2-256 support for protocol records whose exact
 * bytes must have the same identifier before and after Kubo publication.
 */
object RawCid {
    private const val CID_VERSION = 0x01
    private const val RAW_CODEC = 0x55
    private const val SHA2_256 = 0x12
    private const val SHA2_256_LENGTH = 32
    private const val BASE32_ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"

    fun fromBytes(bytes: ByteArray): String = fromSha256(
        MessageDigest.getInstance("SHA-256").digest(bytes)
    )

    fun fromSha256(digest: ByteArray): String {
        require(digest.size == SHA2_256_LENGTH) { "A raw CID requires a 32-byte SHA-256 digest" }
        val cidBytes = byteArrayOf(
            CID_VERSION.toByte(),
            RAW_CODEC.toByte(),
            SHA2_256.toByte(),
            SHA2_256_LENGTH.toByte()
        ) + digest
        return "b" + encodeBase32(cidBytes)
    }

    private fun encodeBase32(bytes: ByteArray): String = buildString((bytes.size * 8 + 4) / 5) {
        var buffer = 0
        var bitCount = 0
        bytes.forEach { byte ->
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bitCount += 8
            while (bitCount >= 5) {
                bitCount -= 5
                append(BASE32_ALPHABET[(buffer shr bitCount) and 0x1f])
            }
            buffer = buffer and ((1 shl bitCount) - 1)
        }
        if (bitCount > 0) {
            append(BASE32_ALPHABET[(buffer shl (5 - bitCount)) and 0x1f])
        }
    }
}
