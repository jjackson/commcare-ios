package org.commcare.core.interfaces

/**
 * iOS cryptography implementation.
 * Uses CommonCrypto via Kotlin/Native interop.
 *
 * Note: Full CommonCrypto interop requires cinterop configuration.
 * This implementation provides the structure; the actual interop calls
 * will be wired up when building on macOS with Xcode.
 */
actual object PlatformCrypto {

    actual fun sha256(data: ByteArray): ByteArray {
        // Will use CC_SHA256 via cinterop
        TODO("iOS SHA-256 implementation requires CommonCrypto cinterop")
    }

    actual fun md5(data: ByteArray): ByteArray {
        // Will use CC_MD5 via cinterop
        TODO("iOS MD5 implementation requires CommonCrypto cinterop")
    }

    actual fun randomBytes(size: Int): ByteArray {
        // Will use SecRandomCopyBytes via cinterop
        TODO("iOS randomBytes implementation requires Security framework cinterop")
    }

    actual fun aesEncrypt(data: ByteArray, key: ByteArray): ByteArray {
        // Will use CCCrypt with kCCAlgorithmAES via cinterop
        TODO("iOS AES encrypt implementation requires CommonCrypto cinterop")
    }

    actual fun aesDecrypt(data: ByteArray, key: ByteArray): ByteArray {
        // Will use CCCrypt with kCCAlgorithmAES via cinterop
        TODO("iOS AES decrypt implementation requires CommonCrypto cinterop")
    }

    actual fun generateAesKey(bits: Int): ByteArray {
        return randomBytes(bits / 8)
    }
}
