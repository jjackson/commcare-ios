package org.commcare.util

/**
 * The default source of month name data in alternate calendars.
 *
 * Should be maintained for any calendars which are in the core system and not extensions.
 */
class DefaultArrayDataSource : ArrayDataSource {

    private val ethiopian = arrayOf(
        "M\u00e4sk\u00e4r\u00e4m",
        "T'\u00efk'\u00efmt",
        "H\u00efdar",
        "Tahsas",
        "T'\u00efr",
        "Y\u00e4katit",
        "M\u00e4gabit",
        "Miyaziya",
        "G\u00efnbot",
        "S\u00e4ne",
        "H\u00e4mle",
        "N\u00e4h\u00e4se",
        "P'agume"
    )

    private val nepali = arrayOf(
        "Baishakh",
        "Jestha",
        "Ashadh",
        "Shrawan",
        "Bhadra",
        "Ashwin",
        "Kartik",
        "Mangsir",
        "Poush",
        "Magh",
        "Falgun",
        "Chaitra"
    )

    override fun getArray(key: String): Array<String> {
        if ("ethiopian_months" == key) {
            return ethiopian
        } else if ("nepali_months" == key) {
            return nepali
        }
        throw RuntimeException("No supported fallback month names for calendar: $key")
    }
}
