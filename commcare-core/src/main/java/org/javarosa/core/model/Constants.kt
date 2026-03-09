package org.javarosa.core.model

/**
 * Constants shared throughout classes in the containing package.
 */
object Constants {
    /**
     * ID not set to a value
     */
    const val NULL_ID: Int = -1

    const val DATATYPE_UNSUPPORTED: Int = -1

    /** for nodes that have no data, or data type otherwise unknown */
    const val DATATYPE_NULL: Int = 0

    /**
     * Text question type.
     */
    const val DATATYPE_TEXT: Int = 1

    /**
     * Numeric question type. These are numbers without decimal points
     */
    const val DATATYPE_INTEGER: Int = 2

    /**
     * Decimal question type. These are numbers with decimals
     */
    const val DATATYPE_DECIMAL: Int = 3

    /**
     * Date question type. This has only date component without time.
     */
    const val DATATYPE_DATE: Int = 4

    /**
     * Time question type. This has only time element without date
     */
    const val DATATYPE_TIME: Int = 5

    /**
     * Date and Time question type. This has both the date and time components
     */
    const val DATATYPE_DATE_TIME: Int = 6

    /**
     * This is a question with a list of options where not more than one option can be selected at a time.
     */
    const val DATATYPE_CHOICE: Int = 7

    /**
     * This is a question with a list of options where more than one option can be selected at a time.
     */
    const val DATATYPE_CHOICE_LIST: Int = 8

    /**
     * Question with true and false answers.
     */
    const val DATATYPE_BOOLEAN: Int = 9

    /**
     * Question with location answer.
     */
    const val DATATYPE_GEOPOINT: Int = 10

    /**
     * Question with barcode string answer.
     */
    const val DATATYPE_BARCODE: Int = 11

    /**
     * Question with external binary answer.
     */
    const val DATATYPE_BINARY: Int = 12

    /**
     * Question with external binary answer.
     */
    const val DATATYPE_LONG: Int = 13

    const val CONTROL_UNTYPED: Int = -1
    const val CONTROL_INPUT: Int = 1
    const val CONTROL_SELECT_ONE: Int = 2
    const val CONTROL_SELECT_MULTI: Int = 3
    const val CONTROL_TEXTAREA: Int = 4
    const val CONTROL_SECRET: Int = 5
    const val CONTROL_RANGE: Int = 6
    const val CONTROL_UPLOAD: Int = 7
    const val CONTROL_SUBMIT: Int = 8
    const val CONTROL_TRIGGER: Int = 9
    const val CONTROL_IMAGE_CHOOSE: Int = 10
    const val CONTROL_LABEL: Int = 11
    const val CONTROL_AUDIO_CAPTURE: Int = 12
    const val CONTROL_VIDEO_CAPTURE: Int = 13
    const val CONTROL_DOCUMENT_UPLOAD: Int = 14

    /**
     * constants for xform tags
     */
    const val XFTAG_UPLOAD: String = "upload"

    /**
     * constants for stack frame step extras
     */
    const val EXTRA_POST_SUCCESS: String = "post-success"
}
