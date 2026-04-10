package org.commcare.app.storage

import org.commcare.app.model.ConnectIdUser

/**
 * Repository for persisting Connect ID user data and HQ links.
 * Maps between SQLDelight-generated types and ConnectIdUser domain model.
 */
class ConnectIdRepository(internal val db: CommCareDatabase) {

    fun getUser(): ConnectIdUser? =
        db.commCareQueries.getConnectIdUser().executeAsOneOrNull()?.let { row ->
            ConnectIdUser(
                userId = row.user_id,
                name = row.name,
                phone = row.phone,
                photoPath = row.photo_path,
                hasConnectAccess = row.has_connect_access != 0L,
                securityMethod = row.security_method
            )
        }

    fun saveUser(user: ConnectIdUser) {
        db.commCareQueries.insertConnectIdUser(
            user_id = user.userId,
            name = user.name,
            phone = user.phone,
            photo_path = user.photoPath,
            has_connect_access = if (user.hasConnectAccess) 1L else 0L,
            security_method = user.securityMethod
        )
    }

    fun deleteUser() {
        db.commCareQueries.deleteConnectIdUser()
    }

    fun getHqLinks(userId: String): List<Pair<String, String>> =
        db.commCareQueries.getAllHqLinks(userId).executeAsList().map { row ->
            row.hq_username to row.domain
        }

    fun saveHqLink(username: String, domain: String, userId: String) {
        db.commCareQueries.insertHqLink(
            hq_username = username,
            domain = domain,
            connect_user_id = userId
        )
    }

    fun deleteHqLink(username: String, domain: String) {
        db.commCareQueries.deleteHqLink(
            hq_username = username,
            domain = domain
        )
    }

    fun isRegistered(): Boolean = getUser() != null
}
