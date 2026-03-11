package org.commcare.session

import kotlin.jvm.JvmStatic
/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
object SessionDescriptorUtil {
    @JvmStatic
    fun loadSessionFromDescriptor(
        sessionDescriptor: String,
        session: CommCareSession
    ) {
        val tokenStream = sessionDescriptor.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        var current = 0
        while (current < tokenStream.size) {
            val action = tokenStream[current]
            if (action == SessionFrame.STATE_COMMAND_ID) {
                session.setCommand(tokenStream[++current])
            } else if (action == SessionFrame.STATE_DATUM_VAL ||
                action == SessionFrame.STATE_DATUM_COMPUTED ||
                action == SessionFrame.STATE_UNKNOWN
            ) {
                session.setDatum(action, tokenStream[++current], tokenStream[++current])
            }
            current++
        }
    }

    /**
     * Serializes the session into a string which is unique for a
     * given path through the application, and which can be deserialized
     * back into a live session.
     *
     * NOTE: Currently we rely on this state being semantically unique,
     * but it may change in the future. Rely on the specific format as
     * little as possible.
     */
    @JvmStatic
    fun createSessionDescriptor(session: CommCareSession): String {
        val descriptor = StringBuilder()
        for (step in session.getFrame().getSteps()) {
            val type = step.getType()
            if (SessionFrame.STATE_QUERY_REQUEST == type ||
                SessionFrame.STATE_SYNC_REQUEST == type
            ) {
                // Skip adding remote server query/sync steps to the descriptor.
                // They are hard to replay (requires serializing query results)
                // and shouldn't be needed for incomplete forms
                continue
            }
            descriptor.append(type).append(" ")
            if (SessionFrame.STATE_COMMAND_ID == type) {
                descriptor.append(step.getId()).append(" ")
            } else if (SessionFrame.STATE_DATUM_VAL == type
                || SessionFrame.STATE_DATUM_COMPUTED == type
            ) {
                descriptor.append(step.getId()).append(" ").append(step.getValue()).append(" ")
            }
        }
        return descriptor.toString().trim()
    }
}
