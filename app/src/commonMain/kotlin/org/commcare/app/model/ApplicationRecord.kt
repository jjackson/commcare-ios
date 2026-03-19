package org.commcare.app.model

data class ApplicationRecord(
    val id: String,
    val profileUrl: String,
    val displayName: String,
    val domain: String,
    val majorVersion: Int,
    val minorVersion: Int = 0,
    val status: AppStatus = AppStatus.INSTALLED,
    val resourcesValidated: Boolean = false,
    val installDate: Long,
    val bannerUrl: String? = null,
    val iconUrl: String? = null
) {
    fun isUsable(): Boolean = status == AppStatus.INSTALLED
    fun isArchived(): Boolean = status == AppStatus.ARCHIVED
}

enum class AppStatus { INSTALLED, ARCHIVED }
