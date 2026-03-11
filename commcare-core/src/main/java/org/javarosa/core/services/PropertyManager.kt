package org.javarosa.core.services

import org.javarosa.core.services.properties.IPropertyRules
import org.javarosa.core.services.properties.Property
import org.javarosa.core.services.storage.IStorageUtilityIndexed

/**
 * PropertyManager is a class that is used to set and retrieve name/value pairs
 * from persistent storage.
 *
 * Which properties are allowed, and what they can be set to, can be specified by an implementation of
 * the IPropertyRules interface, any number of which can be registered with a property manager. All
 * property rules are inclusive, and can only increase the number of potential properties or property
 * values.
 *
 * @author Clayton Sims
 */
class PropertyManager(
    private val properties: IStorageUtilityIndexed<*>
) : IPropertyManager {

    companion object {
        /**
         * The name for the Persistent storage utility name
         */
        const val STORAGE_KEY: String = "PROPERTY"
    }

    /**
     * Retrieves the singular property specified, as long as it exists in one of the current rulesets
     *
     * @param propertyName the name of the property being retrieved
     * @return The String value of the property specified if it exists, is singular, and is in one the current
     * rulesets. null if the property is denied by the current ruleset, or is a vector.
     */
    override fun getSingularProperty(propertyName: String): String? {
        val value = getValue(propertyName)
        return if (value != null && value.size == 1) {
            value[0] as String
        } else {
            null
        }
    }

    /**
     * Retrieves the property specified, as long as it exists in one of the current rulesets
     *
     * @param propertyName the name of the property being retrieved
     * @return The String value of the property specified if it exists, and is the current ruleset, if one exists.
     * null if the property is denied by the current ruleset.
     */
    override fun getProperty(propertyName: String): ArrayList<Any?>? {
        return getValue(propertyName)
    }

    /**
     * Sets the given property to the given string value, if both are allowed by any existing ruleset
     *
     * @param propertyName  The property to be set
     * @param propertyValue The value that the property will be set to
     */
    override fun setProperty(propertyName: String, propertyValue: String) {
        val wrapper = ArrayList<String>()
        wrapper.add(propertyValue)
        setProperty(propertyName, wrapper)
    }

    /**
     * Sets the given property to the given vector value, if both are allowed by any existing ruleset
     *
     * @param propertyName  The property to be set
     * @param propertyValue The value that the property will be set to
     */
    override fun setProperty(propertyName: String, propertyValue: ArrayList<String>) {
        val oldValue = getProperty(propertyName)
        if (oldValue != null && vectorEquals(oldValue, propertyValue)) {
            //No point in redundantly setting values!
            return
        }
        writeValue(propertyName, propertyValue)
    }

    private fun vectorEquals(v1: ArrayList<*>, v2: ArrayList<*>): Boolean {
        if (v1.size != v2.size) {
            return false
        } else {
            for (i in 0 until v1.size) {
                if (v1[i] != v2[i]) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Retrieves the set of rules being used by this property manager if any exist.
     *
     * @return The rulesets being used by this property manager
     */
    override fun getRules(): ArrayList<Any?> {
        throw RuntimeException("PropertyManager rules not implemented")
    }

    /**
     * Adds a set of rules to be used by this PropertyManager.
     * Note that rules sets are inclusive, they add new possible
     * values, never remove possible values.
     *
     * @param rules The set of rules to be added to the permitted list
     */
    override fun addRules(rules: IPropertyRules) {
        throw RuntimeException("PropertyManager rules not implemented")
    }

    fun getValue(name: String): ArrayList<Any?>? {
        return try {
            val p = properties.getRecordForValue("NAME", name) as Property
            p.value as ArrayList<Any?>
        } catch (nsee: NoSuchElementException) {
            null
        }
    }

    private fun writeValue(propertyName: String, value: ArrayList<*>) {
        val theProp = Property()
        theProp.name = propertyName
        theProp.value = value as ArrayList<String>

        val ids = properties.getIDsForValue("NAME", propertyName)
        if (ids.size == 1) {
            theProp.setID(ids[0] as Int)
        }

        properties.write(theProp)
    }
}
