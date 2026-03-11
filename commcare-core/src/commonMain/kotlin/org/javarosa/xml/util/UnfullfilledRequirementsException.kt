package org.javarosa.xml.util

/**
 * @author ctsims
 */
open class UnfullfilledRequirementsException : Exception {

    val requirementType: RequirementType

    enum class RequirementType {
        /** Default case, nothing special about this */
        NONE,

        /** local system can't provide an expected location to store data */
        WRITEABLE_REFERENCE,

        /** The profile is incompatible with the major version of the current CommCare installation */
        MAJOR_APP_VERSION,

        /** The profile is incompatible with the minor version of the current CommCare installation */
        MINOR_APP_VERSION,

        /** Indicates that this exception was thrown due to an attempt to install an app that was already installed */
        DUPLICATE_APP,

        /** app is targetting another flavour of Commcare than the one running currently */
        INCORRECT_TARGET_PACKAGE,

        /** Trying to reinstall a CommCare App using a ccz that belongs to a different CommCare App */
        REINSTALL_FROM_INVALID_CCZ
    }

    /** Version Numbers if version is incompatible */
    private val maR: Int
    private val miR: Int
    private val minR: Int
    private val maA: Int
    private val miA: Int
    private val minA: Int

    constructor(message: String) : this(message, RequirementType.NONE)

    constructor(message: String, requirementType: RequirementType) : this(message, -1, -1, -1, -1, -1, -1, requirementType)

    /**
     * Constructor for unfulfilled version requirements.
     */
    constructor(
        message: String,
        requiredMajor: Int, requiredMinor: Int, requiredMinimal: Int,
        availableMajor: Int, availableMinor: Int, availableMinimal: Int,
        requirementType: RequirementType
    ) : super(message) {
        this.maR = requiredMajor
        this.miR = requiredMinor
        this.minR = requiredMinimal
        this.maA = availableMajor
        this.miA = availableMinor
        this.minA = availableMinimal
        this.requirementType = requirementType
    }

    /**
     * @return A human readable version string describing the required version
     */
    fun getRequiredVersionString(): String = "$maR.$miR.$minR"

    /**
     * @return A human readable version string describing the available version
     */
    fun getAvailableVesionString(): String = "$maA.$miA.$minA"

    /**
     * @return true if this exception was thrown due to an attempt at installing a duplicate app
     */
    fun isDuplicateException(): Boolean = requirementType == RequirementType.DUPLICATE_APP

    /**
     * @return true if this exception was thrown due to an attempt at installing an app targetting a different Commcare package id
     */
    fun isIncorrectTargetException(): Boolean = requirementType == RequirementType.INCORRECT_TARGET_PACKAGE

    /**
     * @return true if this exception was thrown due to an attempt at recovering a CommCare App using ccz belonging to a different CommCare App
     */
    fun isReinstallFromInvalidCCZException(): Boolean = requirementType == RequirementType.REINSTALL_FROM_INVALID_CCZ

    /**
     * @return true if this exception was thrown due to an attempt at installing/updating an app targetting a
     * commcare version larger than the currently installed version
     */
    fun isVersionMismatchException(): Boolean =
        requirementType == RequirementType.MINOR_APP_VERSION ||
                requirementType == RequirementType.MAJOR_APP_VERSION
}
