package org.javarosa.core.services.properties

import java.util.Vector

/**
 * The IPropertyRules interface is used to describe a set of rules for what properties are allowed for a given
 * property manager, and what values are are acceptable for a given property.
 *
 * Essentially, individual properties should be considered to be actual persistent storage
 * for a device's specific configuration, and a set of property rules should be considered
 * to be the non-persistent meta-data surrounding what those configurations mean.
 *
 * @author ctsims
 */
interface IPropertyRules {
    /**
     * Identifies what values are acceptable for a given property
     *
     * @param propertyName The name of the property that is being identified
     * @return A Vector containing all of the values that this property may be set to
     */
    fun allowableValues(propertyName: String): Vector<*>

    /**
     * Identifies whether the given value is an acceptable for a property.
     *
     * @param propertyName   The name of the property that is being identified
     * @param potentialValue The value that is being tested
     * @return True if the property specified may be set to potentialValue, False otherwise
     */
    fun checkValueAllowed(propertyName: String, potentialValue: String): Boolean

    /**
     * Identifies what properties are acceptable for this rules set
     *
     * @return A Vector containing all of the properties that may be set
     */
    fun allowableProperties(): Vector<*>

    /**
     * Identifies whether the given property is usable
     *
     * @param propertyName The name of the property that is being tested
     * @return True if the property specified may used. False otherwise
     */
    fun checkPropertyAllowed(propertyName: String): Boolean

    /**
     * Identifies whether the property should be revealed to users. Note
     * that this does not govern whether the value can be set, simply
     * whether it should be set manually by users.
     *
     * @param propertyName The name of the property that is being tested
     * @return True if the property specified may not be modified by the user. false otherwise
     */
    fun checkPropertyUserReadOnly(propertyName: String): Boolean

    /**
     * Returns a human readable string representing the description of a
     * property.
     *
     * @param propertyName The name of the property to be described
     * @return A string that describes the meaning of the property name
     */
    fun getHumanReadableDescription(propertyName: String): String

    /**
     * Returns a human readable string representing the value of a specific
     * property. This allows multiple choice answers to be stored in a concise
     * format, while offering a standardized way to present those options to
     * a user.
     *
     * @param propertyName The name of the property whose value is to be
     *                     interpreted.
     * @param value        The value to be interpreted as a String
     * @return A string representing the passed in value that can be parsed by
     * a user to determine what its significance is.
     */
    fun getHumanReadableValue(propertyName: String, value: String): String

    /**
     * Handles any state changes that would be required upon a specific value
     * being changed.
     *
     * @param propertyName The name of the property that has changed
     */
    fun handlePropertyChanges(propertyName: String)
}
