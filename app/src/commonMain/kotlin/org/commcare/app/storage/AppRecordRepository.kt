package org.commcare.app.storage

import org.commcare.app.model.AppStatus
import org.commcare.app.model.ApplicationRecord

/**
 * Repository for persisting and querying installed CommCare apps.
 * Maps between SQLDelight-generated Application_record and the ApplicationRecord domain model.
 */
class AppRecordRepository(private val db: CommCareDatabase) {

    fun getAllApps(): List<ApplicationRecord> =
        db.commCareQueries.getAllApps().executeAsList().map { it.toModel() }

    fun getAppById(id: String): ApplicationRecord? =
        db.commCareQueries.getAppById(id).executeAsOneOrNull()?.toModel()

    fun getSeatedApp(): ApplicationRecord? {
        val appId = db.commCareQueries.getSeatedAppId().executeAsOneOrNull() ?: return null
        return getAppById(appId)
    }

    fun getAppCount(): Long =
        db.commCareQueries.getAppCount().executeAsOne()

    fun insertApp(app: ApplicationRecord) {
        db.commCareQueries.insertApp(
            id = app.id,
            profile_url = app.profileUrl,
            display_name = app.displayName,
            domain = app.domain,
            major_version = app.majorVersion.toLong(),
            minor_version = app.minorVersion.toLong(),
            status = app.status.name,
            resources_validated = if (app.resourcesValidated) 1L else 0L,
            install_date = app.installDate,
            banner_url = app.bannerUrl,
            icon_url = app.iconUrl
        )
    }

    fun seatApp(appId: String) {
        db.commCareQueries.setSeatedAppId(appId)
    }

    fun archiveApp(appId: String) {
        db.commCareQueries.updateAppStatus(AppStatus.ARCHIVED.name, appId)
    }

    fun deleteApp(appId: String) {
        db.commCareQueries.deleteApp(appId)
    }

    private fun Application_record.toModel(): ApplicationRecord = ApplicationRecord(
        id = id,
        profileUrl = profile_url,
        displayName = display_name,
        domain = domain,
        majorVersion = major_version.toInt(),
        minorVersion = minor_version.toInt(),
        status = AppStatus.valueOf(status),
        resourcesValidated = resources_validated != 0L,
        installDate = install_date,
        bannerUrl = banner_url,
        iconUrl = icon_url
    )
}
