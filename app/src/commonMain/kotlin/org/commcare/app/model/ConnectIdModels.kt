package org.commcare.app.model

data class ConnectIdUser(
    val userId: String,
    val name: String,
    val phone: String,
    val photoPath: String?,
    val hasConnectAccess: Boolean,
    val securityMethod: String = "pin"  // "pin" or "biometric"
)

data class RegistrationSession(
    val sessionToken: String,
    val smsMethod: String,    // "firebase" or "personal_id"
    val requiredLock: String   // "pin", "biometric", or "either"
)

data class HqAppEntry(
    val name: String,
    val domain: String,
    val profileUrl: String
)

data class ConnectIdTokens(
    val accessToken: String,
    val expiresIn: Long
)
