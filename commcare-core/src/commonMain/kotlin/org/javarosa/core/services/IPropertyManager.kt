package org.javarosa.core.services

import org.javarosa.core.services.properties.IPropertyRules

/**
 * An IProperty Manager is responsible for setting and retrieving name/value pairs
 *
 * @author Yaw Anokwa
 */
interface IPropertyManager {

    fun getProperty(propertyName: String): ArrayList<Any?>?

    fun setProperty(propertyName: String, propertyValue: String)

    fun setProperty(propertyName: String, propertyValue: ArrayList<String>)

    fun getSingularProperty(propertyName: String): String?

    fun addRules(rules: IPropertyRules)

    fun getRules(): ArrayList<Any?>
}
