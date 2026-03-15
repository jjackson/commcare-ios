package org.commcare.app.ui

/**
 * Calendar formatting utilities for alternative calendar systems.
 * Provides formatting context for Ethiopian and Nepali calendar appearances.
 * The actual calendar conversion is handled by commcare-core's CalendarUtils
 * within XPath evaluation — this widget provides display-level support.
 */
object CalendarWidget {

    private val ethiopianMonths = listOf(
        "Meskerem", "Tikimt", "Hidar", "Tahsas", "Tir", "Yekatit",
        "Megabit", "Miyazya", "Ginbot", "Sene", "Hamle", "Nehase", "Pagume"
    )

    private val nepaliMonths = listOf(
        "Baishakh", "Jestha", "Ashadh", "Shrawan", "Bhadra", "Ashwin",
        "Kartik", "Mangsir", "Poush", "Magh", "Falgun", "Chaitra"
    )

    /**
     * Get month names for a calendar system.
     */
    fun getMonthNames(appearance: String?): List<String> {
        return when {
            appearance?.contains("ethiopian") == true -> ethiopianMonths
            appearance?.contains("nepali") == true -> nepaliMonths
            else -> emptyList()
        }
    }

    /**
     * Format a pre-converted calendar date (already in the target calendar system).
     * @param year Year in target calendar
     * @param month Month in target calendar (1-based)
     * @param day Day in target calendar
     * @param appearance Calendar appearance string
     */
    fun formatConvertedDate(year: Int, month: Int, day: Int, appearance: String?): String {
        val months = getMonthNames(appearance)
        return if (months.isNotEmpty() && month in 1..months.size) {
            "${months[month - 1]} $day, $year"
        } else {
            "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
        }
    }

    /**
     * Check if an appearance string indicates an alternative calendar.
     */
    fun isAlternativeCalendar(appearance: String?): Boolean {
        if (appearance == null) return false
        return appearance.contains("ethiopian") || appearance.contains("nepali")
    }

    /**
     * Get the calendar system name from an appearance.
     */
    fun getCalendarName(appearance: String?): String? {
        return when {
            appearance?.contains("ethiopian") == true -> "Ethiopian"
            appearance?.contains("nepali") == true -> "Nepali"
            else -> null
        }
    }
}
