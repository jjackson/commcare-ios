package org.commcare.util

/**
 * Defines different possible LogTypes
 */
object LogTypes {
    /** Fatal problem with one of CommCare's cryptography libraries */
    const val TYPE_ERROR_CRYPTO = "error-crypto"

    /** Some invariant application assumption has been violated */
    const val TYPE_ERROR_ASSERTION = "error-state"

    /** Used for internal checking of whether or not certain sections of code ever get called */
    const val SOFT_ASSERT = "soft-assert"

    /** Something bad/unexpected occurred in a user's workflow */
    const val TYPE_ERROR_WORKFLOW = "error-workflow"

    /** There is a problem with the underlying storage layer which is preventing the app from working correctly */
    const val TYPE_ERROR_STORAGE = "error-storage"

    /**
     * One of the config files (suite, profile, xform, locale, etc) contains something
     * which is invalid and prevented the app from working properly
     */
    const val TYPE_ERROR_CONFIG_STRUCTURE = "error-config"

    /**
     * Something bad happened which the app should not have allowed to happen. This
     * category of error should be aggressively caught and addressed by the software team
     */
    const val TYPE_ERROR_DESIGN = "error-design"

    /** Something bad happened because of network connectivity */
    const val TYPE_WARNING_NETWORK = "warning-network"

    /** We were incapable of processing or understanding something that the server sent down */
    const val TYPE_ERROR_SERVER_COMMS = "error-server-comms"

    /** Logs relating to user events (login/logout/restore, etc) */
    const val TYPE_USER = "user"

    /** Logs relating to the external files and resources which make up an app */
    const val TYPE_RESOURCES = "resources"

    /** Maintenance events (autopurging, cleanups, etc) */
    const val TYPE_MAINTENANCE = "maintenance"

    /** Form Entry workflow messages */
    const val TYPE_FORM_ENTRY = "form-entry"

    /** Form submission messages */
    const val TYPE_FORM_SUBMISSION = "form-submission"

    /** Used to track when we knowingly delete a form record */
    const val TYPE_FORM_DELETION = "form-deletion"

    /** Problem reported via report activity at home screen */
    const val USER_REPORTED_PROBLEM = "user-report"

    /** Used for tracking the behavior of the form dump activity */
    const val TYPE_FORM_DUMP = "form-dump"

    const val TYPE_FORCECLOSE = "forceclose"

    const val TYPE_GRAPHING = "graphing"

    const val TYPE_PRINTING = "printing"

    const val TYPE_WIFI_DIRECT = "wifi-direct"

    /** Important events like app installation, updates, DB updates etc. */
    const val TYPE_DATA_CHANGE = "data-change"

    const val TYPE_CC_UPDATE = "commcare-update"

    const val TYPE_NETWORK = "commcare-network"

    /** Logs related to Firebase Cloud Messaging */
    const val TYPE_FCM = "fcm"

    const val TYPE_MEDIA_EVENT = "media-event"

    /** A Java Exception log */
    const val TYPE_EXCEPTION = "exception"
}
