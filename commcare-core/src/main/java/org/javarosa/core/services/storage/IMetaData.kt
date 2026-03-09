package org.javarosa.core.services.storage

interface IMetaData {

    //for the indefinite future, no meta-data field can have a value of null

    fun getMetaDataFields(): Array<String>

    fun getMetaData(fieldName: String): Any
}
