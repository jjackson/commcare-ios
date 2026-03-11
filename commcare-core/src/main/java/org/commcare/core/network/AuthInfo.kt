package org.commcare.core.network

import org.commcare.cases.util.StringUtils
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * Created by amstone326 on 5/8/18.
 */
abstract class AuthInfo {

    @JvmField
    var username: String? = null
    @JvmField
    var password: String? = null
    @JvmField
    var wrapDomain: Boolean = false
    @JvmField
    var bearerToken: String? = null

    class NoAuth : AuthInfo()

    class ProvidedAuth @JvmOverloads constructor(
        username: String,
        password: String,
        wrapDomain: Boolean = true
    ) : AuthInfo() {
        init {
            if (StringUtils.isEmpty(username)) {
                throw IllegalArgumentException("ProvidedAuth requires a non-empty username")
            }
            if (StringUtils.isEmpty(password)) {
                throw IllegalArgumentException("ProvidedAuth requires a non-empty password")
            }
            this.username = username
            this.password = password
            this.wrapDomain = wrapDomain
        }
    }

    // Auth with the currently-logged in user
    class CurrentAuth : AuthInfo()

    class TokenAuth(token: String) : AuthInfo() {
        init {
            if (StringUtils.isEmpty(token)) {
                throw IllegalArgumentException("TokenAuth requires a non-empty token")
            }
            bearerToken = token
        }
    }
}
