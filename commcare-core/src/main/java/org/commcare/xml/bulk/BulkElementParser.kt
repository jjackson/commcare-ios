package org.commcare.xml.bulk

import org.commcare.data.xml.TransactionParser
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.xml.TreeElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParserException

import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.LinkedHashMap

/**
 * A bulk element parser reads multiple types of the same transaction together into a buffer of
 * TreeElements before performing a bulk processing step.
 *
 * NOTE: This parser will currently lose debugging information v. using a streamed processor,
 * since the XML locations of potential errors are buffered.
 *
 * An implementing class should organize its implementation into the following steps:
 *
 * preParseValidate - Validate only the root element of the streaming xml doc, shouldn't advance
 * the processing
 *
 * requestModelReadsForElement - Gather id's for models that will be needed to process a transaction
 *
 * performBulkRead - Read any relevant data from storage all at once
 *
 * processBufferedElement - After the bulk read, process the transactions one by one
 *
 * performBulkWrite - Write the processed models to storage
 *
 * Created by ctsims on 3/14/2017.
 */
abstract class BulkElementParser<T>(parser: KXmlParser) : TransactionParser<TreeElement>(parser) {

    @JvmField
    protected var bulkTrigger = 500

    private var currentBulkElementBacklog: MutableList<TreeElement> = ArrayList()
    private var currentBulkReadSet: MutableSet<String> = HashSet()
    private var currentOperatingSet: MutableMap<String, T> = HashMap()
    private var writeLog: LinkedHashMap<String, T> = LinkedHashMap()

    private var currentBulkReadCount = 0

    /**
     * Sets the count at which the parser should trigger processing the current buffer
     */
    protected fun setBulkProcessTrigger(newTriggerCount: Int) {
        this.bulkTrigger = newTriggerCount
    }

    @Throws(
        InvalidStructureException::class,
        PlatformIOException::class,
        XmlPullParserException::class,
        UnfullfilledRequirementsException::class
    )
    override fun parse(): TreeElement {
        preParseValidate()
        val subElement = TreeElementParser(parser, 0, "bulk_parser").parse()
        requestModelReadsForElement(subElement, currentBulkReadSet)
        currentBulkElementBacklog.add(subElement)
        currentBulkReadCount++

        if (currentBulkReadCount > bulkTrigger) {
            processCurrentBuffer()
        }
        return subElement
    }

    @Throws(PlatformIOException::class, XmlPullParserException::class, InvalidStructureException::class)
    fun processCurrentBuffer() {
        currentOperatingSet = HashMap()
        performBulkRead(currentBulkReadSet, currentOperatingSet)
        for (t in currentBulkElementBacklog) {
            processBufferedElement(t, currentOperatingSet, writeLog)
        }
        performBulkWrite(writeLog)
        clearState()
    }

    protected open fun clearState() {
        currentBulkElementBacklog.clear()
        currentBulkReadSet.clear()
        currentOperatingSet.clear()
        writeLog.clear()
        currentBulkReadCount = 0
    }

    @Throws(PlatformIOException::class, XmlPullParserException::class, InvalidStructureException::class)
    override fun flush() {
        processCurrentBuffer()
    }

    @Throws(PlatformIOException::class, InvalidStructureException::class)
    override fun commit(parsed: TreeElement) {
    }

    /**
     * Perform checks on the Root Element Name and attributes to establish that this parser is
     * handling the correct element
     *
     * MUST NOT SEEK BEYOND THE CURRENT ELEMENT
     *
     * @throws InvalidStructureException If the parser was passed an element it cannot parse, or
     *                                   that element has a structure which is incorrect based on the root element or attributes
     */
    @Throws(InvalidStructureException::class)
    protected abstract fun preParseValidate()

    /**
     * For the provided tree element, gather any models which this parser will need to read in
     * order to process the tree element into a writable object
     *
     * @param currentBulkReadSet Models which will be read in the next bulk read. This method should
     *                           add any needed model's ID's to this set.
     */
    @Throws(InvalidStructureException::class)
    protected abstract fun requestModelReadsForElement(
        bufferedTreeElement: TreeElement,
        currentBulkReadSet: MutableSet<String>
    )

    /**
     * Read any/all models that have been requested, and loads them into the currentOperatingSet
     * provided. Any ID's which don't match a valid object won't be loaded into the operating set.
     *
     * This step is fired when a bulk process is requested, and begins the bulk processing phase
     *
     * @param currentBulkReadSet  A list of ID's to be read in bulk
     * @param currentOperatingSet the destination mapping from each ID to a matching model
     *                            (if one exists)
     */
    @Throws(InvalidStructureException::class, PlatformIOException::class, XmlPullParserException::class)
    protected abstract fun performBulkRead(
        currentBulkReadSet: Set<String>,
        currentOperatingSet: MutableMap<String, T>
    )

    /**
     * Process the buffered element into a transaction to be written in the next bulk write.
     *
     * This method should add the element to the writeLog after creating its model.
     *
     * This method is fired after the bulk read and is part of the bulk processing phase
     *
     * IMPORTANT - If the processed element is a member of the currentOperatingSet, it should be
     * written _both_ to the currentOperatingSet _as well as_ the writeLog.
     *
     * @param bufferedTreeElement the element to be processed
     * @param currentOperatingSet The operating set of data from the bulk read and other
     *                            processed elements
     * @param writeLog            A list of models to be written during hte bulk write, this method should add
     *                            the processed model to this list.
     */
    @Throws(InvalidStructureException::class)
    protected abstract fun processBufferedElement(
        bufferedTreeElement: TreeElement,
        currentOperatingSet: MutableMap<String, T>,
        writeLog: LinkedHashMap<String, T>
    )

    /**
     * Writes the list of buffered models into storage as efficiently as possible.
     *
     * This call of this method ends the bulk processing phase.
     *
     * @param writeLog A list of models to be processed into storage, keyed by their unique ID
     */
    @Throws(PlatformIOException::class)
    protected abstract fun performBulkWrite(writeLog: LinkedHashMap<String, T>)
}
