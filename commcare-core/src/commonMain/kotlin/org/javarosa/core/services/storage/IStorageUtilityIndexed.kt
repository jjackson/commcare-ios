package org.javarosa.core.services.storage

import org.javarosa.core.model.condition.RequestAbandonedException
import org.javarosa.core.util.InvalidIndexException
import org.javarosa.core.util.externalizable.Externalizable
import kotlin.reflect.KClass

/**
 * IStorageUtilityIndexed
 *
 * Implementations of this interface provide persistent records-based storage in which records are stored
 * and retrieved using record IDs.
 *
 * IStorageUtilityIndexed can be used in two flavors: you manage the IDs, or the utility manages the IDs:
 *
 * If you manage the IDs, the objects you are storing must implement Persistable, which provides the ID from the object
 * itself. You then use the functions read(), write(), and remove() when dealing with storage.
 *
 * If the utility manages the IDs, your objects need only implement Externalizable. You use the functions read(), add(),
 * update(), and remove(). add() will return a new ID for the record, which you then explicitly provide to all subsequent
 * calls to update().
 *
 * Since storage operations may have significant runtimes, those methods will frequently check for
 * thread interruptions. If the thread is interrupted, those methods will be expected to throw
 * an RequestAbandonedException, which signals that the current thread is no longer relevant.
 *
 * These two schemes should not be mixed within the same StorageUtility.
 */
interface IStorageUtilityIndexed<E : Externalizable> {

    /**
     * Read and return the record corresponding to 'id'.
     *
     * @param id id of the object
     * @return object for 'id'. null if no object is stored under that ID
     */
    fun read(id: Int): E

    /**
     * Read and return the raw bytes for the record corresponding to 'id'.
     *
     * @param id id of the object
     * @return raw bytes for the record. null if no record is stored under that ID
     */
    fun readBytes(id: Int): ByteArray

    /**
     * Write an object to the store. Will either add a new record, or update the existing record (if one exists) for the
     * object's ID. This function should never be used in conjunction with add() and update() within the same StorageUtility
     *
     * @param p object to store
     */
    fun write(p: Persistable)

    /**
     * Add a new record to the store. This function always adds a new record; it never updates an existing record. The
     * record ID under which this record is added is allocated by the StorageUtility. If this StorageUtility stores
     * Persistables, you should almost certainly use write() instead.
     *
     * @param e object to add
     * @return record ID for newly added object
     */
    fun add(e: E): Int

    /**
     * Update a record in the store. The record must have previously been added to the store using add(). If this
     * StorageUtility stores Persistables, you should almost certainly use write() instead.
     *
     * @param id ID of record to update
     * @param e  updated object
     * @throws IllegalArgumentException if no record exists for ID
     */
    fun update(id: Int, e: E)

    /**
     * Remove record with the given ID from the store.
     *
     * @param id ID of record to remove
     * @throws IllegalArgumentException if no record with that ID exists
     */
    fun remove(id: Int)

    /**
     * Remove object from the store
     *
     * @param p object to remove
     * @throws IllegalArgumentException if object is not in the store
     */
    fun remove(p: Persistable)

    fun removeAll()

    fun removeAll(ef: EntityFilter<*>): ArrayList<Int>

    /**
     * Return the number of records in the store
     *
     * @return number of records
     */
    fun getNumRecords(): Int

    /**
     * Return whether the store is empty
     *
     * @return true if there are no records in the store
     */
    fun isEmpty(): Boolean

    /**
     * Return whether a record exists in the store
     *
     * @param id record ID
     * @return true if a record exists for that ID in the store
     */
    fun exists(id: Int): Boolean

    /**
     * Return an iterator to iterate through all records in this store
     *
     * @return record iterator
     */
    fun iterate(): IStorageIterator<E>

    /**
     * Return an iterator to iterate through all records in this store
     *
     * if includeData is false, the iterator is only guaranteed to be able to return ID's for
     * records, not full values.
     *
     * @return record iterator
     */
    fun iterate(includeData: Boolean): IStorageIterator<E>

    /**
     * Close all resources associated with this StorageUtility. Any attempt to use this StorageUtility after this call will result
     * in error. Though not strictly necessary, it is a good idea to call this when you are done with the StorageUtility, as closing
     * may trigger clean-up in the underlying device storage (reclaiming unused space, etc.).
     */
    fun close()

    /**
     * Fetch the object that acts as the synchronization lock for this StorageUtility
     *
     * @return lock object
     */
    fun getAccessLock(): Any?

    /**
     * Retrieves a ArrayList of IDs of Externalizable objects in storage for which the field
     * specified contains the value specified.
     *
     * @param metaFieldName The name of a field which should be evaluated
     * @param value         The value which should be contained by the field specified
     * @return A ArrayList of Integers such that retrieving the Externalizable object with any
     * of those integer IDs will result in an object for which the field specified is equal
     * to the value provided.
     * @throws RuntimeException (Fix this exception type) if the field is unrecognized by the
     *                          meta data
     */
    fun getIDsForValue(metaFieldName: String, value: Any): ArrayList<Int>

    /**
     * Retrieves a ArrayList of IDs of Externalizable objects in storage for which the field
     * specified contains the values specified.
     *
     * @param metaFieldNames A list of metadata field names to match
     * @param values         The values which must match the field names provided
     * @return A ArrayList of Integers such that retrieving the Externalizable object with any
     * of those integer IDs will result in an object for which the fields specified are equal
     * to the value provided.
     * @throws RuntimeException (Fix this exception type) if the field is unrecognized by the
     *                          meta data
     */
    fun getIDsForValues(metaFieldNames: Array<String>, values: Array<Any>): List<Int>

    /**
     * Retrieves a ArrayList of IDs of Externalizable objects in storage for which the field
     * specified contains the values specified.
     *
     * @param metaFieldNames A list of metadata field names to match
     * @param values         The values which must match the field names provided
     * @param returnSet      A LinkedHashSet of integers which match the return value
     * @return A ArrayList of Integers such that retrieving the Externalizable object with any
     * of those integer IDs will result in an object for which the fields specified are equal
     * to the value provided.
     * @throws RuntimeException (Fix this exception type) if the field is unrecognized by the
     *                          meta data
     */
    fun getIDsForValues(metaFieldNames: Array<String>, values: Array<Any>, returnSet: LinkedHashSet<Int>): List<Int>

    /**
     * Retrieves a ArrayList of IDs of Externalizable objects in storage for which the field
     * specified contains the values specified and the fields specified in inverseFieldNames
     * do not contain the value specified in inverseValues. If no values are passed, all cases are returned
     *
     * @param metaFieldNames A list of metadata field names to match
     * @param values     The values which must match the field names provided
     * @param inverseFieldNames A list of field names to inverse match (!=)
     * @param inverseValues     The values which the field must not match
     * @param returnSet A LinkedHashSet of integers which match the return value
     * @return A ArrayList of Integers such that retrieving the Externalizable object with any
     * of those integer IDs will result in an object for which the fields specified are equal
     * to the value provided.
     * @throws RuntimeException (Fix this exception type) if any field in is unrecognized by the
     *                          meta data
     */
    fun getIDsForValues(
        metaFieldNames: Array<String>,
        values: Array<Any>,
        inverseFieldNames: Array<String>,
        inverseValues: Array<Any>,
        returnSet: LinkedHashSet<Int>
    ): List<Int>

    /**
     * Retrieves a Externalizable object from the storage which is reference by the unique index fieldName.
     *
     * @param metaFieldName The name of the index field which will be evaluated
     * @param value         The value which should be set in the index specified by fieldName for the returned
     *                      object.
     * @return An Externalizable object e, such that e.getMetaData(fieldName).equals(value);
     * @throws NoSuchElementException If no objects reside in storage for which the return condition
     *                                can be successful.
     * @throws InvalidIndexException  If the field used is an invalid index, because more than one field in the Storage
     *                                contains the value of the index requested.
     */
    @Throws(NoSuchElementException::class, InvalidIndexException::class)
    fun getRecordForValue(metaFieldName: String, value: Any): E

    /**
     * Retrieves a ArrayList of Externalizable objects from the storage for which, for each field in fieldNames,
     * the record has the correct corresponding value in values
     *
     * @param metaFieldNames A list of metadata field names to match
     * @param values         The values which must match the field names provided
     * @return A ArrayList of Externalizable objects e, such that the fields specified are equal to the corresponding values provided.
     */
    fun getRecordsForValues(metaFieldNames: Array<String>, values: Array<Any>): ArrayList<E>

    /**
     * Load multiple record objects from storage at one time from a list of record ids.
     *
     * If the provided recordMap already contains entries for any ids, it is _not_
     * required for them to be retrieved from storage again.
     *
     * @throws RequestAbandonedException If the current request is abandoned, this method will
     *                                   throw a RequestAbandonedException. Callers should not
     *                                   generally catch that exception unless they rethrow it
     *                                   or another exception, but they should anticipate that
     *                                   they may need to clean up if the bulk read doesn't complete
     */
    @Throws(RequestAbandonedException::class)
    fun bulkRead(cuedCases: LinkedHashSet<Int>, recordMap: HashMap<Int, E>)

    /**
     * Retrieves the metadata field values requested from the record at the provided record ID.
     */
    fun getMetaDataForRecord(recordId: Int, metaFieldNames: Array<String>): Array<String>

    /**
     * Load metadata fields associated with a provided list of record IDs. For each recordId in the
     * provided set, the metadataMap should be loaded with the metadata from storage associated with
     * that record.
     *
     * If the provided metadataMap already contains entries for any ids, it is _not_
     * required for the fields to be retrieved from storage again.
     */
    fun bulkReadMetadata(recordIds: LinkedHashSet<Int>, metaFieldNames: Array<String>, metadataMap: HashMap<Int, Array<String>>)

    /**
     * Bulk retrieves a set of the records in storage based on a list of values matching one of the
     * field for this storage
     * @param metaFieldName field we are matching against
     * @param matchingValues matching values for metaFieldName that we want to filter records against
     * @return A ArrayList of Externalizable objects e, such that the field specified is equal to the corresponding value provided.
     */
    fun getBulkRecordsForIndex(metaFieldName: String, matchingValues: Collection<String>): ArrayList<E>

    /**
     * Bulk retrieves a set of the record ids in storage based on a list of values matching one of the
     * field for this storage
     * @param metaFieldName field we are matching against
     * @param matchingValues matching values for metaFieldName that we want to filter records against
     * @return A ArrayList of Externalizable objects e, such that the field specified is equal to the corresponding value provided.
     */
    fun getBulkIdsForIndex(metaFieldName: String, matchingValues: Collection<String>): ArrayList<Int>

    /**
     * Provide public accessor to the inner class that is stored
     */
    fun getPrototype(): KClass<*>

    /**
     * if the storage exists or not
     *
     * @return a boolean indicating if the storage exists
     */
    fun isStorageExists(): Boolean

    /**
     * initialise the storage, for example create the table in the DB for this storage
     */
    fun initStorage()

    /**
     * Deletes the storage
     */
    fun deleteStorage()
}
