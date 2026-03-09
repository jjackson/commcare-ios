package org.commcare.modern.models

/**
 * Created by wpride1 on 9/23/15.
 */
class RecordTooLargeException(size: Double) : RuntimeException(
    "You tried to restore some data sized " +
            size + " MB which exceeds" +
            "the maximum allowed size of 1MB. Please reduce the size of this fixture."
)
