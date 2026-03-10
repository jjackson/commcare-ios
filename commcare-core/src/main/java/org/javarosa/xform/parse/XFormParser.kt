package org.javarosa.xform.parse

import org.commcare.cases.util.StringUtils
import org.javarosa.core.model.Constants
import org.javarosa.core.model.DataBinding
import org.javarosa.core.model.FormDef
import org.javarosa.core.model.GroupDef
import org.javarosa.core.model.IFormElement
import org.javarosa.core.model.ItemsetBinding
import org.javarosa.core.model.QuestionDef
import org.javarosa.core.model.QuestionString
import org.javarosa.core.model.SelectChoice
import org.javarosa.core.model.SubmissionProfile
import org.javarosa.core.model.actions.Action
import org.javarosa.core.model.actions.ActionController
import org.javarosa.core.model.actions.SendAction
import org.javarosa.core.model.actions.SetValueAction
import org.javarosa.core.model.condition.Condition
import org.javarosa.core.model.condition.Constraint
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.Recalculate
import org.javarosa.core.model.data.AnswerDataFactory
import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.InvalidReferenceException
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.services.Logger
import org.javarosa.core.services.locale.Localizer
import org.javarosa.core.services.locale.TableLocaleSource
import org.javarosa.core.util.DataUtil
import org.javarosa.core.util.Interner
import org.javarosa.core.util.ShortestCycleAlgorithm
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.model.xform.XPathReference
import org.javarosa.xform.util.InterningKXmlParser
import org.javarosa.xform.util.XFormSerializer
import org.javarosa.xform.util.XFormUtils
import org.javarosa.xpath.XPathConditional
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.XPathUnsupportedException
import org.javarosa.xpath.parser.XPathSyntaxException
import org.kxml2.io.KXmlParser
import org.kxml2.kdom.Document
import org.kxml2.kdom.Element
import org.kxml2.kdom.Node
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.util.Hashtable
import java.util.Stack
import java.util.Vector

/**
 * Provides conversion from xform to epihandy object model and vice vasa.
 *
 * @author Daniel Kayiwa
 * @author Drew Roos
 */
class XFormParser {

    companion object {
        // Constants to clean up code and prevent user error
        private const val ID_ATTR = "id"
        private const val FORM_ATTR = "form"
        private const val APPEARANCE_ATTR = "appearance"
        private const val NODESET_ATTR = "nodeset"
        const val LABEL_ELEMENT: String = "label"
        const val HELP_ELEMENT: String = "help"
        const val HINT_ELEMENT: String = "hint"
        const val CONSTRAINT_ELEMENT: String = "alert"
        private const val VALUE = "value"
        private const val ITEXT_CLOSE = "')"
        private const val ITEXT_OPEN = "jr:itext('"
        private const val BIND_ATTR = "bind"
        private const val REF_ATTR = "ref"
        private const val EVENT_ATTR = "event"
        private const val SELECTONE = "select1"
        private const val SELECT = "select"
        private const val SORT = "sort"

        const val NAMESPACE_JAVAROSA: String = "http://openrosa.org/javarosa"
        const val NAMESPACE_HTML: String = "http://www.w3.org/1999/xhtml"
        const val NAMESPACE_XFORMS: String = "http://www.w3.org/2002/xforms"

        private const val CONTAINER_GROUP = 1
        private const val CONTAINER_REPEAT = 2

        private lateinit var topLevelHandlers: Hashtable<String, IElementHandler>
        private lateinit var groupLevelHandlers: Hashtable<String, IElementHandler>
        private lateinit var typeMappings: Hashtable<String, Int>
        private lateinit var actionHandlers: Hashtable<String, IElementHandler>

        // Track specification extension keywords so we know what to do during
        // parsing when they are encountered.
        private var specExtensionKeywords: Hashtable<String, Vector<String>> = Hashtable()
        // Namespace for which inner elements should be parsed.
        private var parseSpecExtensionsInnerElements: Vector<String> = Vector()
        // Namespace for which we suppress "unrecognized element" warnings
        private var suppressSpecExtensionWarnings: Vector<String> = Vector()

        init {
            try {
                staticInit()
            } catch (e: Exception) {
                Logger.die("xfparser-static-init", e)
            }
        }

        private fun staticInit() {
            initProcessingRules()
            initTypeMappings()
        }

        private fun initProcessingRules() {
            setupGroupLevelHandlers()
            setupTopLevelHandlers()
            setupActionHandlers()
        }

        private fun setupGroupLevelHandlers() {
            val input = IElementHandler { p, e, parent -> p.parseControl(parent as IFormElement, e, Constants.CONTROL_INPUT) }
            val secret = IElementHandler { p, e, parent -> p.parseControl(parent as IFormElement, e, Constants.CONTROL_SECRET) }
            val select = IElementHandler { p, e, parent -> p.parseControl(parent as IFormElement, e, Constants.CONTROL_SELECT_MULTI) }
            val select1 = IElementHandler { p, e, parent -> p.parseControl(parent as IFormElement, e, Constants.CONTROL_SELECT_ONE) }
            val group = IElementHandler { p, e, parent -> p.parseGroup(parent as IFormElement, e, CONTAINER_GROUP) }
            val repeat = IElementHandler { p, e, parent -> p.parseGroup(parent as IFormElement, e, CONTAINER_REPEAT) }
            val groupLabel = IElementHandler { p, e, parent -> p.parseGroupLabel(parent as GroupDef, e) }
            val trigger = IElementHandler { p, e, parent -> p.parseControl(parent as IFormElement, e, Constants.CONTROL_TRIGGER) }
            val upload = IElementHandler { p, e, parent -> p.parseUpload(parent as IFormElement, e, Constants.CONTROL_UPLOAD) }

            groupLevelHandlers = Hashtable()
            groupLevelHandlers["input"] = input
            groupLevelHandlers["secret"] = secret
            groupLevelHandlers[SELECT] = select
            groupLevelHandlers[SELECTONE] = select1
            groupLevelHandlers["group"] = group
            groupLevelHandlers["repeat"] = repeat
            groupLevelHandlers["trigger"] = trigger
            groupLevelHandlers[Constants.XFTAG_UPLOAD] = upload
            groupLevelHandlers[LABEL_ELEMENT] = groupLabel
        }

        private fun setupTopLevelHandlers() {
            val title = IElementHandler { p, e, _ -> p.parseTitle(e) }
            val meta = IElementHandler { p, e, _ -> p.parseMeta(e) }
            val model = IElementHandler { p, e, _ -> p.parseModel(e) }

            topLevelHandlers = Hashtable()
            val en = groupLevelHandlers.keys()
            while (en.hasMoreElements()) {
                val key = en.nextElement()
                topLevelHandlers[key] = groupLevelHandlers[key]!!
            }
            topLevelHandlers["model"] = model
            topLevelHandlers["title"] = title
            topLevelHandlers["meta"] = meta
        }

        private fun setupActionHandlers() {
            actionHandlers = Hashtable()
            registerActionHandler(SetValueAction.ELEMENT_NAME, SetValueAction.getHandler())
            registerActionHandler(SendAction.ELEMENT_NAME, SendAction.getHandler())
        }

        /**
         * Setup mapping from a tag's type attribute to its datatype id.
         */
        private fun initTypeMappings() {
            typeMappings = Hashtable()
            typeMappings["string"] = DataUtil.integer(Constants.DATATYPE_TEXT)
            typeMappings["integer"] = DataUtil.integer(Constants.DATATYPE_INTEGER)
            typeMappings["long"] = DataUtil.integer(Constants.DATATYPE_LONG)
            typeMappings["int"] = DataUtil.integer(Constants.DATATYPE_INTEGER)
            typeMappings["decimal"] = DataUtil.integer(Constants.DATATYPE_DECIMAL)
            typeMappings["double"] = DataUtil.integer(Constants.DATATYPE_DECIMAL)
            typeMappings["float"] = DataUtil.integer(Constants.DATATYPE_DECIMAL)
            typeMappings["dateTime"] = DataUtil.integer(Constants.DATATYPE_DATE_TIME)
            typeMappings["date"] = DataUtil.integer(Constants.DATATYPE_DATE)
            typeMappings["time"] = DataUtil.integer(Constants.DATATYPE_TIME)
            typeMappings["gYear"] = DataUtil.integer(Constants.DATATYPE_UNSUPPORTED)
            typeMappings["gMonth"] = DataUtil.integer(Constants.DATATYPE_UNSUPPORTED)
            typeMappings["gDay"] = DataUtil.integer(Constants.DATATYPE_UNSUPPORTED)
            typeMappings["gYearMonth"] = DataUtil.integer(Constants.DATATYPE_UNSUPPORTED)
            typeMappings["gMonthDay"] = DataUtil.integer(Constants.DATATYPE_UNSUPPORTED)
            typeMappings["boolean"] = DataUtil.integer(Constants.DATATYPE_BOOLEAN)
            typeMappings["base64Binary"] = DataUtil.integer(Constants.DATATYPE_UNSUPPORTED)
            typeMappings["hexBinary"] = DataUtil.integer(Constants.DATATYPE_UNSUPPORTED)
            typeMappings["anyURI"] = DataUtil.integer(Constants.DATATYPE_UNSUPPORTED)
            typeMappings["listItem"] = DataUtil.integer(Constants.DATATYPE_CHOICE)
            typeMappings["listItems"] = DataUtil.integer(Constants.DATATYPE_CHOICE_LIST)
            typeMappings[SELECTONE] = DataUtil.integer(Constants.DATATYPE_CHOICE)
            typeMappings[SELECT] = DataUtil.integer(Constants.DATATYPE_CHOICE_LIST)
            typeMappings["geopoint"] = DataUtil.integer(Constants.DATATYPE_GEOPOINT)
            typeMappings["barcode"] = DataUtil.integer(Constants.DATATYPE_BARCODE)
            typeMappings["binary"] = DataUtil.integer(Constants.DATATYPE_BINARY)
        }

        /**
         * Converts a (possibly relative) reference into an absolute reference
         * based on its parent
         *
         * @param ref       potentially null reference
         * @param parentRef must be an absolute path
         */
        @JvmStatic
        fun getAbsRef(ref: XPathReference?, parentRef: TreeReference): XPathReference {
            if (!parentRef.isAbsolute) {
                throw RuntimeException("XFormParser.getAbsRef: parentRef must be absolute")
            }

            val tref: TreeReference = if (ref != null) {
                ref.reference
            } else {
                TreeReference.selfRef() // only happens for <group>s with no binding
            }

            val refPreContextualization = tref
            val contextualized = tref.parent(parentRef)
                ?: throw XFormParseException(
                    "Binding path [" + refPreContextualization.toString(true) +
                            "] not allowed with parent binding of [" + parentRef + "]"
                )

            return XPathReference(contextualized)
        }

        // collapse groups whose only child is a repeat into a single repeat that uses the label of the wrapping group
        private fun collapseRepeatGroups(fe: IFormElement) {
            if (fe.getChildren() == null) return

            for (i in 0 until fe.getChildren()!!.size) {
                val child = fe.getChild(i)
                var group: GroupDef? = null
                if (child is GroupDef) group = child

                if (group != null) {
                    if (!group.isRepeat() && group.getChildren()!!.size == 1) {
                        val grandchild = group.getChildren()!!.elementAt(0)
                        var repeat: GroupDef? = null
                        if (grandchild is GroupDef) repeat = grandchild

                        if (repeat != null && repeat.isRepeat()) {
                            // collapse the wrapping group
                            // merge group into repeat
                            repeat.setLabelInnerText(group.getLabelInnerText())
                            repeat.setTextID(group.getTextID())
                            // don't merge binding; repeat will always already have one

                            // replace group with repeat
                            fe.getChildren()!!.setElementAt(repeat, i)
                            @Suppress("UNUSED_VALUE")
                            group = repeat
                        }
                    }

                    collapseRepeatGroups(group)
                }
            }
        }

        @JvmStatic
        fun buildInstanceStructure(node: Element, parent: TreeElement?): TreeElement {
            return buildInstanceStructure(node, parent, null, node.namespace)
        }

        /**
         * parse instance hierarchy and turn into a skeleton model; ignoring data content,
         * but respecting repeated nodes and 'template' flags
         */
        @JvmStatic
        fun buildInstanceStructure(
            node: Element,
            parent: TreeElement?,
            instanceName: String?,
            docnamespace: String?
        ): TreeElement {
            // catch when text content is mixed with children
            val numChildren = node.childCount
            var hasText = false
            var hasElements = false
            for (i in 0 until numChildren) {
                when (node.getType(i)) {
                    Node.ELEMENT -> hasElements = true
                    Node.TEXT -> if (node.getText(i).trim().isNotEmpty()) hasText = true
                }
            }
            if (hasElements && hasText) {
                System.out.println("Warning: instance node '" + node.name + "' contains both elements and text as children; text ignored")
            }

            // check for repeat templating
            val name = node.name
            val multiplicity: Int
            if (node.getAttributeValue(NAMESPACE_JAVAROSA, "template") != null) {
                multiplicity = TreeReference.INDEX_TEMPLATE
                if (parent != null && parent.getChild(name, TreeReference.INDEX_TEMPLATE) != null) {
                    throw XFormParseException(
                        "More than one node declared as the template for the same repeated set [$name]",
                        node
                    )
                }
            } else {
                multiplicity = if (parent == null) 0 else parent.getChildMultiplicity(name)
            }

            val modelType = node.getAttributeValue(NAMESPACE_JAVAROSA, "modeltype")
            // create node; handle children
            val element: TreeElement
            if (modelType == null) {
                element = TreeElement(name, multiplicity)
                element.setInstanceName(instanceName)
            } else {
                if (typeMappings[modelType] == null) {
                    throw XFormParseException("ModelType $modelType is not recognized.", node)
                }
                element = TreeElement(name, multiplicity)
            }
            if (node.namespace != null) {
                if (node.namespace != docnamespace) {
                    element.setNamespace(node.namespace)
                }
            }

            if (hasElements) {
                for (i in 0 until numChildren) {
                    if (node.getType(i) == Node.ELEMENT) {
                        element.addChild(
                            buildInstanceStructure(node.getElement(i), element, instanceName, docnamespace)
                        )
                    }
                }
            }

            // handle attributes
            if (node.attributeCount > 0) {
                for (i in 0 until node.attributeCount) {
                    val attrNamespace = node.getAttributeNamespace(i)
                    val attrName = node.getAttributeName(i)
                    if (attrNamespace == NAMESPACE_JAVAROSA && attrName == "template") {
                        continue
                    }
                    if (attrNamespace == NAMESPACE_JAVAROSA && attrName == "recordset") {
                        continue
                    }
                    element.setAttribute(attrNamespace, attrName, node.getAttributeValue(i))
                }
            }

            return element
        }

        // build a pseudo-data model tree that describes the repeat structure of the instance
        private fun buildRepeatTree(repeatRefs: Vector<TreeReference>, topLevelName: String): FormInstance? {
            val root = TreeElement(null, 0)

            for (i in 0 until repeatRefs.size) {
                val repeatRef = repeatRefs.elementAt(i)
                // check and see if this references a repeat from a non-main instance
                if (repeatRef.instanceName != null) {
                    continue
                }
                if (repeatRef.size() <= 1) {
                    continue
                }

                var cur = root
                for (j in 0 until repeatRef.size()) {
                    val rName = repeatRef.getName(j)!!
                    var child = cur.getChild(rName, 0)
                    if (child == null) {
                        child = TreeElement(rName, 0)
                        cur.addChild(child)
                    }
                    cur = child!!
                }
                cur.setRepeatable(true)
            }

            return if (root.getNumChildren() == 0) {
                null
            } else {
                FormInstance(root.getChild(topLevelName, TreeReference.DEFAULT_MUTLIPLICITY)!!)
            }
        }

        // checks which repeat bindings have explicit template nodes
        private fun checkRepeatsForTemplate(
            instance: FormInstance,
            repeatTree: FormInstance?,
            missingTemplates: Vector<TreeReference>
        ) {
            if (repeatTree != null) {
                checkRepeatsForTemplate(repeatTree.getRoot(), TreeReference.rootRef(), instance, missingTemplates)
            }
        }

        // helper function for checkRepeatsForTemplate
        private fun checkRepeatsForTemplate(
            repeatTreeNode: TreeElement,
            ref: TreeReference,
            instance: FormInstance,
            missing: Vector<TreeReference>
        ) {
            val name = repeatTreeNode.getName()
            val mult = if (repeatTreeNode.isRepeatable) TreeReference.INDEX_TEMPLATE else 0
            val extRef = ref.extendRef(name, mult)

            if (repeatTreeNode.isRepeatable) {
                val template = instance.resolveReference(extRef)
                if (template == null) {
                    missing.addElement(extRef)
                }
            }

            for (i in 0 until repeatTreeNode.getNumChildren()) {
                checkRepeatsForTemplate(repeatTreeNode.getChildAt(i)!!, extRef, instance, missing)
            }
        }

        // trim repeatable children of newly created template nodes
        private fun trimRepeatChildren(node: TreeElement) {
            var i = 0
            while (i < node.getNumChildren()) {
                val child = node.getChildAt(i)!!
                if (child.isRepeatable) {
                    node.removeChildAt(i)
                    i--
                } else {
                    trimRepeatChildren(child)
                }
                i++
            }
        }

        private fun attachBindGeneral(bind: DataBinding) {
            val ref = DataInstance.unpackReference(bind.reference!!)

            if (bind.relevancyCondition != null) {
                bind.relevancyCondition!!.addTarget(ref)
            }
            if (bind.requiredCondition != null) {
                bind.requiredCondition!!.addTarget(ref)
            }
            if (bind.readonlyCondition != null) {
                bind.readonlyCondition!!.addTarget(ref)
            }
            if (bind.calculate != null) {
                bind.calculate!!.addTarget(ref)
            }
        }

        private fun attachBind(node: TreeElement, bind: DataBinding) {
            node.setDataType(bind.dataType)

            if (bind.relevancyCondition == null) {
                node.setRelevant(bind.relevantAbsolute)
            }
            if (bind.requiredCondition == null) {
                node.setRequired(bind.requiredAbsolute)
            }
            if (bind.readonlyCondition == null) {
                node.setEnabled(!bind.readonlyAbsolute)
            }
            if (bind.constraint != null) {
                node.setConstraint(Constraint(bind.constraint, bind.constraintMessage))
            }

            node.setPreloadHandler(bind.preload)
            node.setPreloadParams(bind.preloadParams)
        }

        /**
         * Traverse the node, copying data from it into the TreeElement argument.
         */
        private fun loadInstanceData(node: Element, cur: TreeElement) {
            val numChildren = node.childCount
            var hasElements = false
            for (i in 0 until numChildren) {
                if (node.getType(i) == Node.ELEMENT) {
                    hasElements = true
                    break
                }
            }

            if (hasElements) {
                val multiplicities = Hashtable<String, Int>()
                for (i in 0 until numChildren) {
                    if (node.getType(i) == Node.ELEMENT) {
                        val child = node.getElement(i)
                        val cName = child.name
                        val index: Int
                        val isTemplate = child.getAttributeValue(NAMESPACE_JAVAROSA, "template") != null

                        if (isTemplate) {
                            index = TreeReference.INDEX_TEMPLATE
                        } else {
                            val mult = multiplicities[cName]
                            index = if (mult == null) 0 else mult + 1
                            multiplicities[cName] = DataUtil.integer(index)
                        }

                        loadInstanceData(child, cur.getChild(cName, index)!!)
                    }
                }
            } else {
                val text = getXMLText(node, true)
                if (text != null && text.trim().isNotEmpty()) {
                    cur.setValue(AnswerDataFactory.templateByDataType(cur.getDataType()).cast(UncastData(text.trim())))
                }
            }
        }

        private fun loadNamespaces(e: Element, tree: FormInstance): Hashtable<String, String> {
            val prefixes = Hashtable<String, String>()
            for (i in 0 until e.namespaceCount) {
                val uri = e.getNamespaceUri(i)
                val prefix = e.getNamespacePrefix(i)
                if (uri != null && prefix != null) {
                    tree.addNamespace(prefix, uri)
                }
            }
            return prefixes
        }

        @JvmStatic
        fun loadXmlInstance(formDef: FormDef, xmlReader: Reader): FormDef {
            return loadXmlInstance(formDef, getXMLDocument(xmlReader))
        }

        /**
         * Load a compatible xml instance into FormDef f
         *
         * call before f.initialize()!
         */
        @JvmStatic
        fun loadXmlInstance(f: FormDef, xmlInst: Document): FormDef {
            return loadXmlInstance(f, restoreDataModel(xmlInst, null))
        }

        @JvmStatic
        fun loadXmlInstance(formDef: FormDef, xmlInst: FormInstance): FormDef {
            val savedRoot = xmlInst.getRoot()
            val templateRoot = formDef.getMainInstance()!!.getRoot().deepCopy(true)

            // weak check for matching forms
            if (savedRoot.getName() != templateRoot.getName() || savedRoot.getMult() != 0) {
                throw RuntimeException("Saved form instance does not match template form definition")
            }

            // populate the data model
            val tr = TreeReference.rootRef()
            tr.add(templateRoot.getName(), TreeReference.INDEX_UNBOUND)
            templateRoot.populate(savedRoot)

            // populated model to current form
            formDef.getMainInstance()!!.setRoot(templateRoot)

            return formDef
        }

        @JvmStatic
        fun addDataType(type: String, dataType: Int) {
            typeMappings[type] = DataUtil.integer(dataType)
        }

        @JvmStatic
        fun registerControlType(type: String, typeId: Int) {
            val newHandler = IElementHandler { p, e, parent -> p.parseControl(parent as IFormElement, e, typeId) }
            topLevelHandlers[type] = newHandler
            groupLevelHandlers[type] = newHandler
        }

        @JvmStatic
        fun registerHandler(type: String, handler: IElementHandler) {
            topLevelHandlers[type] = handler
            groupLevelHandlers[type] = handler
        }

        /**
         * Let the parser know how to handle a given action -- All actions are first parsed by the
         * generic parseAction() method, which is passed another handler to invoke after the generic
         * handler is done
         */
        @JvmStatic
        fun registerActionHandler(name: String, specificHandler: IElementHandler) {
            actionHandlers[name] = IElementHandler { p, e, parent ->
                p.parseAction(e, parent, specificHandler)
            }
        }

        @JvmStatic
        fun getXMLDocument(reader: Reader): Document {
            return getXMLDocument(reader, null)
        }

        @JvmStatic
        fun getXMLDocument(reader: Reader, stringCache: Interner<String>?): Document {
            val doc = Document()

            try {
                val parser: KXmlParser = if (stringCache != null) {
                    InterningKXmlParser(stringCache)
                } else {
                    KXmlParser()
                }

                parser.setInput(reader)
                parser.setFeature(PlatformXmlParser.FEATURE_PROCESS_NAMESPACES, true)
                doc.parse(parser)
            } catch (e: PlatformXmlParserException) {
                val errorMsg = "XML Syntax Error at Line: ${e.lineNumber}, Column: ${e.columnNumber}!"
                System.err.println(errorMsg)
                e.printStackTrace()
                throw XFormParseException(errorMsg)
            } catch (e: PlatformIOException) {
                // CTS - 12/09/2012 - Stop swallowing IO Exceptions
                throw e
            } catch (e: Exception) {
                val errorMsg = "Unhandled Exception while Parsing XForm"
                System.err.println(errorMsg)
                e.printStackTrace()
                throw XFormParseException(errorMsg)
            } finally {
                try {
                    reader.close()
                } catch (e: PlatformIOException) {
                    System.out.println("Error closing reader")
                    e.printStackTrace()
                }
            }

            // For escaped unicode strings we end up with a lot of cruft,
            // so we really want to go through and convert the kxml parsed
            // text (which have lots of characters each as their own string)
            // into one single string
            val q = Stack<Element>()

            q.push(doc.rootElement)
            while (!q.isEmpty()) {
                val e = q.pop()
                val toRemove = BooleanArray(e.childCount * 2)
                var accumulate = ""
                var i = 0
                while (i < e.childCount) {
                    val type = e.getType(i)
                    if (type == Element.TEXT) {
                        val text = e.getText(i)
                        accumulate += text
                        toRemove[i] = true
                    } else {
                        if (type == Element.ELEMENT) {
                            q.addElement(e.getElement(i))
                        }
                        val accumulatedString = accumulate.trim()
                        if (accumulatedString.isNotEmpty()) {
                            if (stringCache == null) {
                                e.addChild(i, Element.TEXT, accumulate)
                            } else {
                                e.addChild(i, Element.TEXT, stringCache.intern(accumulate))
                            }
                            accumulate = ""
                            ++i
                        } else {
                            accumulate = ""
                        }
                    }
                    i++
                }
                if (accumulate.trim().isNotEmpty()) {
                    if (stringCache == null) {
                        e.addChild(Element.TEXT, accumulate)
                    } else {
                        e.addChild(Element.TEXT, stringCache.intern(accumulate))
                    }
                }
                for (idx in e.childCount - 1 downTo 0) {
                    if (toRemove[idx]) {
                        e.removeChild(idx)
                    }
                }
            }

            return doc
        }

        @JvmStatic
        fun getXMLText(n: Node, trim: Boolean): String? {
            return if (n.childCount == 0) null else getXMLText(n, 0, trim)
        }

        /**
         * reads all subsequent text nodes and returns the combined string
         * needed because escape sequences are parsed into consecutive text nodes
         * e.g. "abc&amp;123" --> (abc)(&)(123)
         */
        @JvmStatic
        fun getXMLText(node: Node, startIndex: Int, trim: Boolean): String? {
            var strBuff: StringBuffer? = null

            var text: String? = node.getText(startIndex) ?: return null

            var i = startIndex + 1
            while (i < node.childCount && node.getType(i) == Node.TEXT) {
                if (strBuff == null) strBuff = StringBuffer(text)
                strBuff.append(node.getText(i))
                i++
            }
            if (strBuff != null) text = strBuff.toString()

            if (trim) text = text!!.trim()

            return text
        }

        @JvmStatic
        fun restoreDataModel(input: InputStream, restorableType: Class<*>?): FormInstance {
            val doc = getXMLDocument(InputStreamReader(input))
                ?: throw RuntimeException("syntax error in XML instance; could not parse")
            return restoreDataModel(doc, restorableType)
        }

        @JvmStatic
        fun restoreDataModel(doc: Document, restorableType: Class<*>?): FormInstance {
            val r = if (restorableType != null) PrototypeFactory.getInstance(restorableType) as? org.javarosa.core.model.util.restorable.Restorable else null

            val e = doc.rootElement

            val te = buildInstanceStructure(e, null)
            val dm = FormInstance(te)
            loadNamespaces(e, dm)
            if (r != null) {
                org.javarosa.core.model.util.restorable.RestoreUtils.templateData(r, dm, null)
            }
            loadInstanceData(e, te)

            return dm
        }

        @JvmStatic
        fun getVagueLocation(e: Element): String {
            var path = e.name
            var walker: Element? = e
            while (walker != null) {
                val n = walker.parent
                if (n is Element) {
                    walker = n
                    var step = walker.name
                    for (i in 0 until walker.attributeCount) {
                        step += "[@" + walker.getAttributeName(i) + "="
                        step += walker.getAttributeValue(i) + "]"
                    }
                    path = "$step/$path"
                } else {
                    walker = null
                    path = "/$path"
                }
            }

            val elementString = getVagueElementPrintout(e, 2)

            var fullmsg = "\n    Problem found at nodeset: $path"
            fullmsg += "\n    With element $elementString\n"
            return fullmsg
        }

        @JvmStatic
        fun getVagueElementPrintout(e: Element, maxDepth: Int): String {
            var elementString = "<" + e.name
            for (i in 0 until e.attributeCount) {
                elementString += " " + e.getAttributeName(i) + "=\""
                elementString += e.getAttributeValue(i) + "\""
            }
            if (e.childCount > 0) {
                elementString += ">"
                if (e.getType(0) == Element.ELEMENT) {
                    elementString += if (maxDepth > 0) {
                        getVagueElementPrintout(e.getChild(0) as Element, maxDepth - 1)
                    } else {
                        "..."
                    }
                }
            } else {
                elementString += "/>"
            }
            return elementString
        }

        @JvmStatic
        fun buildParseException(
            nodeset: String,
            message: String,
            expression: String,
            attribute: String
        ): XFormParseException {
            return XFormParseException(
                "Problem with bind for $nodeset contains invalid $attribute expression [$expression] $message"
            )
        }

        private fun buildCalculate(xpath: String, contextRef: XPathReference): Recalculate {
            val calc = XPathConditional(xpath)
            return Recalculate(calc, DataInstance.unpackReference(contextRef))
        }
    }

    private val extensionParsers: Vector<QuestionExtensionParser> = Vector()

    private var _reader: Reader? = null
    private var _xmldoc: Document? = null
    private var _f: FormDef? = null

    private var _instReader: Reader? = null
    private var _instDoc: Document? = null

    private var modelFound = false
    private lateinit var bindingsByID: Hashtable<String, DataBinding>
    private lateinit var bindings: Vector<DataBinding>
    private lateinit var actionTargets: Vector<TreeReference>
    private lateinit var repeats: Vector<TreeReference>
    private lateinit var itemsets: Vector<ItemsetBinding>
    private lateinit var selectOnes: Vector<TreeReference>
    private lateinit var selectMultis: Vector<TreeReference>
    private var mainInstanceNode: Element? = null
    private lateinit var instanceNodes: Vector<Element>
    private lateinit var instanceNodeIdStrs: Vector<String>
    private var defaultNamespace: String? = null
    private lateinit var itextKnownForms: Vector<String>

    private var repeatTree: FormInstance? = null

    // incremented to provide unique question ID for each question
    private var serialQuestionID = 1

    @JvmField
    internal var reporter: XFormParserReporter = XFormParserReporter()

    @JvmField
    internal var stringCache: Interner<String>? = null

    constructor(reader: Reader) {
        _reader = reader
    }

    constructor(doc: Document) {
        _xmldoc = doc
    }

    constructor(form: Reader, instance: Reader) {
        _reader = form
        _instReader = instance
    }

    constructor(form: Document, instance: Document) {
        _xmldoc = form
        _instDoc = instance
    }

    fun attachReporter(reporter: XFormParserReporter) {
        this.reporter = reporter
    }

    /**
     * If the handlers that parse specification extensions aren't present,
     * register a place-holder to enable control over parsing and warnings.
     *
     * @param namespace          String ensures we only apply parser extension logic to
     *                           the correct namespace.
     * @param keywords           are the commands are to be expected in the specification
     *                           extension.
     * @param suppressWarnings   do we want to show warnings if parser attempts to
     *                           work on a given keyword in the namespace?
     * @param parseInnerElements do we want the parser to work on children of
     *                           the element from the spec extension?
     */
    fun addSpecExtension(
        namespace: String,
        keywords: Vector<String>,
        suppressWarnings: Boolean,
        parseInnerElements: Boolean
    ) {
        if (suppressWarnings) {
            suppressSpecExtensionWarnings.addElement(namespace)
        }
        if (parseInnerElements) {
            parseSpecExtensionsInnerElements.addElement(namespace)
        }
        specExtensionKeywords[namespace] = keywords
    }

    /**
     * Setup local state that controls specification extension parsing logic.
     */
    fun setupAllSpecExtensions(
        namespacesToKeywords: Hashtable<String, Vector<String>>,
        namespacesToSuppressWarnings: Vector<String>,
        namespacesToParseInner: Vector<String>
    ) {
        parseSpecExtensionsInnerElements = namespacesToParseInner
        suppressSpecExtensionWarnings = namespacesToSuppressWarnings
        specExtensionKeywords = namespacesToKeywords
    }

    /**
     * Has the tag, including namespace, been registered as an extension whose
     * parsing will be handled at a different time via registerHandler.
     */
    fun inSpecExtension(namespace: String, name: String): Boolean {
        return specExtensionKeywords.containsKey(namespace) &&
                specExtensionKeywords[namespace]!!.contains(name)
    }

    /**
     * Handle parsing and warning logic for a tag that doesn't have attached
     * logic already, but has been registered as a spec extension.
     */
    fun parseUnregisteredSpecExtension(
        namespace: String,
        name: String,
        e: Element,
        parent: Any,
        handlers: Hashtable<String, IElementHandler>
    ) {
        if (!suppressSpecExtensionWarnings.contains(namespace)) {
            reporter.warning(
                XFormParserReporter.TYPE_UNKNOWN_MARKUP,
                "Unrecognized element [$name] from namespace $namespace.",
                getVagueLocation(e)
            )
        }

        if (parseSpecExtensionsInnerElements.contains(namespace)) {
            for (i in 0 until e.childCount) {
                if (e.getType(i) == Element.ELEMENT) {
                    parseElement(e.getElement(i), parent, handlers)
                }
            }
        }
    }

    @Throws(PlatformIOException::class)
    fun parse(): FormDef {
        if (_f == null) {
            if (_xmldoc == null) {
                _xmldoc = getXMLDocument(_reader!!, stringCache)
            }

            parseDoc()

            // load in a custom xml instance, if applicable
            if (_instReader != null) {
                loadXmlInstance(_f!!, _instReader!!)
            } else if (_instDoc != null) {
                loadXmlInstance(_f!!, _instDoc!!)
            }

            // Lots of code assumes there's _some_ title so if we never got anything during the parse
            // just initialize this to something
            if (_f!!.getName() == null && _f!!.getTitle() == null) {
                _f!!.setName("Form")
            }
        }
        return _f!!
    }

    private fun parseDoc() {
        _f = FormDef()

        initState()
        defaultNamespace = _xmldoc!!.rootElement.getNamespaceUri(null)
        parseElement(_xmldoc!!.rootElement, _f!!, topLevelHandlers)
        collapseRepeatGroups(_f!!)

        // parse the non-main instance nodes first
        if (instanceNodes.size > 1) {
            for (i in 1 until instanceNodes.size) {
                val e = instanceNodes.elementAt(i)
                val srcLocation = e.getAttributeValue(null, "src")
                val instanceid = instanceNodeIdStrs.elementAt(i)

                val di: DataInstance<*>
                if (srcLocation != null) {
                    if (e.childCount > 0) {
                        for (k in 0 until e.childCount) {
                            when (e.getType(k)) {
                                Element.TEXT -> {
                                    if ("" == e.getText(i).trim()) {
                                        continue
                                    }
                                    // fall through (no break in original Java)
                                }
                                Element.IGNORABLE_WHITESPACE -> continue
                                Element.ELEMENT -> throw XFormParseException(
                                    "Instance declaration for instance $instanceid contains both a src and a body, only one is permitted",
                                    e
                                )
                            }
                        }
                    }
                    di = ExternalDataInstance(srcLocation, instanceid)
                } else {
                    val fi = parseInstance(e, false)
                    loadInstanceData(e, fi.getRoot())
                    di = fi
                }
                _f!!.addNonMainInstance(di)
            }
        }
        // now parse the main instance
        if (mainInstanceNode != null) {
            val fi = parseInstance(mainInstanceNode!!, true)
            addMainInstanceToFormDef(mainInstanceNode!!, fi)

            // set the main instance
            _f!!.setInstance(fi)
        }
    }

    private fun initState() {
        modelFound = false
        bindingsByID = Hashtable()
        bindings = Vector()
        actionTargets = Vector()
        repeats = Vector()
        itemsets = Vector()
        selectOnes = Vector()
        selectMultis = Vector()
        mainInstanceNode = null
        instanceNodes = Vector()
        instanceNodeIdStrs = Vector()
        repeatTree = null
        defaultNamespace = null

        itextKnownForms = Vector()
        itextKnownForms.addElement("long")
        itextKnownForms.addElement("short")
        itextKnownForms.addElement("image")
        itextKnownForms.addElement("audio")
    }

    private fun parseElement(e: Element, parent: Any, handlers: Hashtable<String, IElementHandler>) {
        val name = e.name
        val namespace = e.namespace

        val suppressWarningArr = arrayOf(
            "html", "head", "body", "xform",
            "chooseCaption", "addCaption", "addEmptyCaption",
            "delCaption", "doneCaption", "doneEmptyCaption",
            "mainHeader", "entryHeader", "delHeader",
            "hashtags", "hashtagTransforms"
        )
        val suppressWarning = Vector<String>()
        for (item in suppressWarningArr) {
            suppressWarning.addElement(item)
        }

        // if there is a registered parser, invoke it
        val eh = handlers[name]
        if (eh != null) {
            eh.handle(this, e, parent)
        } else {
            if (inSpecExtension(namespace, name)) {
                parseUnregisteredSpecExtension(namespace, name, e, parent, handlers)
            } else {
                if (!suppressWarning.contains(name)) {
                    reporter.warning(
                        XFormParserReporter.TYPE_UNKNOWN_MARKUP,
                        "Unrecognized element [$name]. Ignoring and processing children...",
                        getVagueLocation(e)
                    )
                }
                for (i in 0 until e.childCount) {
                    if (e.getType(i) == Element.ELEMENT) {
                        parseElement(e.getElement(i), parent, handlers)
                    }
                }
            }
        }
    }

    private fun parseTitle(e: Element) {
        val usedAtts = Vector<String>()
        val title = getXMLText(e, true)
        _f!!.setTitle(title)
        if (_f!!.getName() == null) {
            _f!!.setName(title)
        }

        if (XFormUtils.showUnusedAttributeWarning(e, usedAtts)) {
            reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(e, usedAtts), getVagueLocation(e))
        }
    }

    private fun parseMeta(e: Element) {
        val usedAtts = Vector<String>()
        val attributes = e.attributeCount
        for (i in 0 until attributes) {
            val name = e.getAttributeName(i)
            val value = e.getAttributeValue(i)
            if ("name" == name) {
                _f!!.setName(value)
            }
        }

        usedAtts.addElement("name")
        if (XFormUtils.showUnusedAttributeWarning(e, usedAtts)) {
            reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(e, usedAtts), getVagueLocation(e))
        }
    }

    private fun parseModel(e: Element) {
        val usedAtts = Vector<String>()
        val delayedParseElements = Vector<Element>()

        if (modelFound) {
            reporter.warning(
                XFormParserReporter.TYPE_INVALID_STRUCTURE,
                "Multiple models not supported. Ignoring subsequent models.",
                getVagueLocation(e)
            )
            return
        }
        modelFound = true

        if (XFormUtils.showUnusedAttributeWarning(e, usedAtts)) {
            reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(e, usedAtts), getVagueLocation(e))
        }

        var i = 0
        while (i < e.childCount) {
            val type = e.getType(i)
            val child: Element? = if (type == Node.ELEMENT) e.getElement(i) else null
            val childName: String? = child?.name

            if ("itext" == childName) {
                parseIText(child!!)
            } else if ("instance" == childName) {
                saveInstanceNode(child!!)
            } else if (BIND_ATTR == childName) {
                parseBind(child!!)
            } else if ("submission" == childName) {
                delayedParseElements.addElement(child)
            } else if (childName != null && actionHandlers.containsKey(childName)) {
                delayedParseElements.addElement(child)
            } else {
                if (type == Node.ELEMENT) {
                    if (child!!.namespace == NAMESPACE_XFORMS) {
                        throw XFormParseException("Unrecognized top-level tag [$childName] found within <model>", child)
                    }
                } else if (type == Node.TEXT && getXMLText(e, i, true)!!.isNotEmpty()) {
                    throw XFormParseException(
                        "Unrecognized text content found within <model>: \"${getXMLText(e, i, true)}\"",
                        child ?: e
                    )
                }
            }

            if (child == null || BIND_ATTR == childName || "itext" == childName) {
                e.removeChild(i)
                --i
            }
            i++
        }

        // Now parse out the submission/action blocks
        for (child in delayedParseElements) {
            val name = child.name
            if (name == "submission") {
                parseSubmission(child)
            } else {
                actionHandlers[name]!!.handle(this, child, _f!!)
            }
        }
    }

    /**
     * Generic parse method that all actions get passed through.
     */
    private fun parseAction(e: Element, parent: Any, specificHandler: IElementHandler) {
        val event = e.getAttributeValue(null, EVENT_ATTR)
        if (!Action.isValidEvent(event)) {
            throw XFormParseException("An action was registered for an unsupported event: $event")
        }

        if (parent !is IFormElement) {
            throw XFormParseException(
                "An action element occurred in an invalid location. " +
                        "Must be either a child of a control element, or a child of the <model>"
            )
        }

        specificHandler.handle(this, e, parent)
    }

    fun parseSetValueAction(source: ActionController, e: Element) {
        val ref = e.getAttributeValue(null, REF_ATTR)
        val bind = e.getAttributeValue(null, BIND_ATTR)

        var dataRef: XPathReference?
        var refFromBind = false

        if (bind != null) {
            val binding = bindingsByID[bind]
                ?: throw XFormParseException("XForm Parse: invalid binding ID in submit'$bind'", e)
            dataRef = binding.reference
            refFromBind = true
        } else if (ref != null) {
            dataRef = XPathReference(ref)
        } else {
            throw XFormParseException("setvalue action with no target!", e)
        }

        if (dataRef != null) {
            if (!refFromBind) {
                dataRef = getAbsRef(dataRef, TreeReference.rootRef())
            }
        }

        val valueRef = e.getAttributeValue(null, "value")
        val action: Action
        val treeref = DataInstance.unpackReference(dataRef!!)

        registerActionTarget(treeref)
        if (valueRef == null) {
            if (e.childCount == 0 || !e.isText(0)) {
                throw XFormParseException(
                    "No 'value' attribute and no inner value set in <setvalue> associated with: $treeref",
                    e
                )
            }
            action = SetValueAction(treeref, e.getText(0))
        } else {
            try {
                action = SetValueAction(treeref, XPathParseTool.parseXPath(valueRef))
            } catch (e1: XPathSyntaxException) {
                e1.printStackTrace()
                throw XFormParseException("Invalid XPath in value set action declaration: '$valueRef'", e)
            }
        }

        val event = e.getAttributeValue(null, EVENT_ATTR)
        source.registerEventListener(event, action)
    }

    fun parseSendAction(source: ActionController, e: Element) {
        val event = getRequiredAttribute(e, "event")
        val id = getRequiredAttribute(e, "submission")

        val action = SendAction(id)
        source.registerEventListener(event, action)
    }

    private fun getRequiredAttribute(e: Element, attrName: String): String {
        val value = e.getAttributeValue(null, attrName)
        if (value == null || value == "") {
            throw XFormParseException("Missing required attribute $attrName in element", e)
        }
        return value
    }

    private fun parseSubmission(submission: Element) {
        val id = submission.getAttributeValue(null, ID_ATTR)

        val resource = getRequiredAttribute(submission, "resource")
        val targetref = getRequiredAttribute(submission, "targetref")

        val ref = submission.getAttributeValue(null, "ref")

        val method = submission.getAttributeValue(null, "method")

        if ("get" != method) {
            throw XFormParseException("Unsupported submission @method: $method")
        }

        val replace = submission.getAttributeValue(null, "replace")

        if ("text" != replace) {
            throw XFormParseException("Unsupported submission @replace: $replace")
        }

        val mode = submission.getAttributeValue(null, "mode")

        if ("synchronous" != mode) {
            throw XFormParseException("Unsupported submission @mode: $mode")
        }

        val targetReference = XPathReference.getPathExpr(targetref).getReference()
        if (targetReference.instanceName != null) {
            throw XFormParseException("<submission> events can only target the main instance", submission)
        }
        registerActionTarget(targetReference)

        var refReference: TreeReference? = null
        if (ref != null) {
            refReference = XPathReference.getPathExpr(ref).getReference()
            registerActionTarget(refReference!!)
        }

        val profile = SubmissionProfile(resource, targetReference, refReference)
        _f!!.addSubmissionProfile(id, profile)
    }

    private fun saveInstanceNode(instance: Element) {
        var instanceNode: Element? = null
        val instanceId = instance.getAttributeValue("", "id")

        for (i in 0 until instance.childCount) {
            if (instance.getType(i) == Node.ELEMENT) {
                if (instanceNode != null) {
                    throw XFormParseException("XForm Parse: <instance> has more than one child element", instance)
                } else {
                    instanceNode = instance.getElement(i)
                }
            }
        }

        if (instanceNode == null) {
            instanceNode = instance
        }

        if (mainInstanceNode == null) {
            mainInstanceNode = instanceNode
        } else if (instanceId == null) {
            throw XFormParseException("XForm Parse: Non-main <instance> element requires an id attribute", instance)
        }

        instanceNodes.addElement(instanceNode)
        instanceNodeIdStrs.addElement(instanceId)
    }

    protected fun parseUpload(parent: IFormElement, e: Element, controlUpload: Int): QuestionDef {
        val usedAtts = Vector<String>()
        usedAtts.addElement("mediatype")

        val question = parseControl(parent, e, controlUpload, usedAtts)

        val mediaType = e.getAttributeValue(null, "mediatype")
        if ("image/*" == mediaType) {
            question.setControlType(Constants.CONTROL_IMAGE_CHOOSE)
        } else if ("audio/*" == mediaType) {
            question.setControlType(Constants.CONTROL_AUDIO_CAPTURE)
        } else if ("video/*" == mediaType) {
            question.setControlType(Constants.CONTROL_VIDEO_CAPTURE)
        } else if ("application/*,text/*" == mediaType) {
            question.setControlType(Constants.CONTROL_DOCUMENT_UPLOAD)
        }

        return question
    }

    protected fun parseControl(parent: IFormElement, e: Element, controlType: Int): QuestionDef {
        return parseControl(parent, e, controlType, Vector())
    }

    protected fun parseControl(
        parent: IFormElement,
        e: Element,
        controlType: Int,
        usedAtts: Vector<String>
    ): QuestionDef {
        val question = QuestionDef()

        for (parser in extensionParsers) {
            if (parser.canParse(e)) {
                val extension = parser.parse(e)
                if (extension != null) {
                    question.addExtension(extension)
                    val attributesFromExtension = parser.getUsedAttributes()
                    for (attr: String in attributesFromExtension) {
                        usedAtts.addElement(attr)
                    }
                }
            }
        }

        question.setID(serialQuestionID++)

        usedAtts.addElement(REF_ATTR)
        usedAtts.addElement(BIND_ATTR)
        usedAtts.addElement(APPEARANCE_ATTR)

        var dataRef: XPathReference? = null
        var refFromBind = false

        val ref = e.getAttributeValue(null, REF_ATTR)
        val bind = e.getAttributeValue(null, BIND_ATTR)

        if (bind != null) {
            val binding = bindingsByID[bind]
                ?: throw XFormParseException("XForm Parse: invalid binding ID '$bind'", e)
            dataRef = binding.reference
            refFromBind = true
        } else if (ref != null) {
            try {
                dataRef = XPathReference(ref)
                val controlRefTarget = dataRef.reference
                if (controlRefTarget.instanceName != null) {
                    reporter.error(
                        "<${e.name}> points to an non-main instance (${controlRefTarget.instanceName}), which isn't supported."
                    )
                }
                if (controlRefTarget.hasPredicates()) {
                    throw XFormParseException(
                        "XForm Parse: The ref path of a <trigger> isn't allowed to have predicates.",
                        e
                    )
                }
            } catch (el: RuntimeException) {
                System.out.println(getVagueLocation(e))
                throw el
            }
        } else {
            if (controlType == Constants.CONTROL_TRIGGER) {
                // special handling for triggers
            } else {
                throw XFormParseException("XForm Parse: input control with neither 'ref' nor 'bind'", e)
            }
        }

        if (dataRef != null) {
            if (!refFromBind) {
                dataRef = getAbsRef(dataRef, parent)
            }
            question.setBind(dataRef)

            if (controlType == Constants.CONTROL_SELECT_ONE) {
                selectOnes.addElement(dataRef.reference)
            } else if (controlType == Constants.CONTROL_SELECT_MULTI) {
                selectMultis.addElement(dataRef.reference)
            }
        }

        val isSelect = controlType == Constants.CONTROL_SELECT_MULTI || controlType == Constants.CONTROL_SELECT_ONE
        question.setControlType(controlType)
        question.setAppearanceAttr(e.getAttributeValue(null, APPEARANCE_ATTR))

        parseControlChildren(e, question, parent, isSelect)

        if (isSelect) {
            if (question.getNumChoices() > 0 && question.getDynamicChoices() != null) {
                throw XFormParseException(
                    "Multiple choice question at ${getFormElementRef(question)} contains both literal choices and <itemset>"
                )
            } else if (question.getNumChoices() == 0 && question.getDynamicChoices() == null) {
                throw XFormParseException(
                    "Multiple choice question at ${getFormElementRef(question)} has no choices"
                )
            }
        }

        parent.addChild(question)

        if (XFormUtils.showUnusedAttributeWarning(e, usedAtts)) {
            reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(e, usedAtts), getVagueLocation(e))
        }

        return question
    }

    private fun parseControlChildren(
        e: Element,
        question: QuestionDef,
        parent: IFormElement,
        isSelect: Boolean
    ) {
        for (i in 0 until e.childCount) {
            val type = e.getType(i)
            val child: Element? = if (type == Node.ELEMENT) e.getElement(i) else null
            if (child == null) continue
            val childName = child.name

            if (LABEL_ELEMENT == childName || HINT_ELEMENT == childName
                || HELP_ELEMENT == childName || CONSTRAINT_ELEMENT == childName
            ) {
                parseHelperText(question, child)
            } else if (isSelect && "item" == childName) {
                parseItem(question, child)
            } else if (isSelect && "itemset" == childName) {
                parseItemset(question, child)
            } else if (actionHandlers.containsKey(childName)) {
                actionHandlers[childName]!!.handle(this, child, question)
            }
        }
    }

    private fun parseHelperText(q: QuestionDef, e: Element) {
        val usedAtts = Vector<String>()
        usedAtts.addElement(REF_ATTR)
        val xmlText = getXMLText(e, true)
        val innerText = getLabel(e)
        val ref = e.getAttributeValue("", REF_ATTR)
        val name = e.name

        val mQuestionString = QuestionString(name)
        q.putQuestionString(name, mQuestionString)

        if (ref != null) {
            if (ref.startsWith(ITEXT_OPEN) && ref.endsWith(ITEXT_CLOSE)) {
                val textRef = ref.substring(ITEXT_OPEN.length, ref.indexOf(ITEXT_CLOSE))
                verifyTextMappings(textRef, "<$name>", true)
                mQuestionString.textId = textRef
            } else {
                throw RuntimeException("malformed ref [$ref] for <$name>")
            }
        }

        mQuestionString.textInner = innerText
        mQuestionString.textFallback = xmlText

        if (XFormUtils.showUnusedAttributeWarning(e, usedAtts)) {
            reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(e, usedAtts), getVagueLocation(e))
        }
    }

    private fun parseGroupLabel(g: GroupDef, e: Element) {
        if (g.isRepeat()) return

        val usedAtts = Vector<String>()
        usedAtts.addElement(REF_ATTR)

        val labelItextId = getItextReference(e)
        g.setTextID(labelItextId)
        if (labelItextId == null) {
            val label = getLabel(e)
            g.setLabelInnerText(label)
        }

        if (XFormUtils.showUnusedAttributeWarning(e, usedAtts)) {
            reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(e, usedAtts), getVagueLocation(e))
        }
    }

    private fun getItextReference(e: Element): String? {
        val ref = e.getAttributeValue("", REF_ATTR)
        if (ref != null) {
            if (ref.startsWith(ITEXT_OPEN) && ref.endsWith(ITEXT_CLOSE)) {
                val textRef = ref.substring(ITEXT_OPEN.length, ref.indexOf(ITEXT_CLOSE))
                verifyTextMappings(textRef, "Group <label>", true)
                return textRef
            } else {
                throw XFormParseException("malformed ref [$ref] for <label>")
            }
        }
        return null
    }

    private fun getLabelOrTextId(element: Element): String? {
        val labelItextId = getItextReference(element)
        if (!StringUtils.isEmpty(labelItextId)) {
            return labelItextId
        }
        return getLabel(element)
    }

    private fun getLabel(e: Element): String? {
        if (e.childCount == 0) return null

        recurseForOutput(e)

        val sb = StringBuffer()
        for (i in 0 until e.childCount) {
            if (e.getType(i) != Node.TEXT && e.getChild(i) !is String) {
                val b = e.getChild(i)
                val child = b as Element

                if (NAMESPACE_HTML == child.namespace) {
                    sb.append(XFormSerializer.elementToString(child))
                } else {
                    System.out.println(
                        "Unrecognized tag inside of text: <${child.name}>. " +
                                "Did you intend to use HTML markup? If so, ensure that the element is defined in " +
                                "the HTML namespace."
                    )
                }
            } else {
                sb.append(e.getText(i))
            }
        }

        return sb.toString().trim()
    }

    private fun recurseForOutput(e: Element) {
        if (e.childCount == 0) return

        var i = 0
        while (i < e.childCount) {
            val kidType = e.getType(i)
            if (kidType == Node.TEXT) {
                i++
                continue
            }
            if (e.getChild(i) is String) {
                i++
                continue
            }
            val kid = e.getChild(i) as Element

            if (kidType == Node.ELEMENT && XFormUtils.isOutput(kid)) {
                val s = "\${${parseOutput(kid)}}"
                e.removeChild(i)
                e.addChild(i, Node.TEXT, s)
            } else if (kid.childCount != 0) {
                recurseForOutput(kid)
            } else {
                i++
                continue
            }
            i++
        }
    }

    private fun parseOutput(e: Element): String {
        var xpath = e.getAttributeValue(null, REF_ATTR)
        var attr = REF_ATTR
        if (xpath == null) {
            attr = VALUE
            xpath = e.getAttributeValue(null, VALUE)
        }
        if (xpath == null) {
            throw XFormParseException("XForm Parse: <output> without 'ref' or 'value'", e)
        }

        val expr: XPathConditional
        try {
            expr = XPathConditional(xpath)
        } catch (xse: XPathSyntaxException) {
            throw XFormParseException("Output tag has malformed $attr attribute: $xpath", e)
        }

        var index: Int
        if (_f!!.getOutputFragments().contains(expr)) {
            index = _f!!.getOutputFragments().indexOf(expr)
        } else {
            index = _f!!.getOutputFragments().size
            _f!!.getOutputFragments().addElement(expr)
        }

        val usedAtts = Vector<String>()
        usedAtts.addElement(REF_ATTR)
        usedAtts.addElement(VALUE)
        if (XFormUtils.showUnusedAttributeWarning(e, usedAtts)) {
            reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(e, usedAtts), getVagueLocation(e))
        }

        return index.toString()
    }

    private fun parseItem(q: QuestionDef, e: Element) {
        val MAX_VALUE_LEN = 256

        val usedAtts = Vector<String>()
        val labelUA = Vector<String>()
        val valueUA = Vector<String>()
        labelUA.addElement(REF_ATTR)
        valueUA.addElement(FORM_ATTR)

        var labelInnerText: String? = null
        var textRef: String? = null
        var value: String? = null

        for (i in 0 until e.childCount) {
            val type = e.getType(i)
            val child: Element? = if (type == Node.ELEMENT) e.getElement(i) else null
            val childName: String? = child?.name

            if (LABEL_ELEMENT == childName) {
                if (XFormUtils.showUnusedAttributeWarning(child, labelUA)) {
                    reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(child, labelUA), getVagueLocation(child!!))
                }
                labelInnerText = getLabel(child!!)
                val ref = child.getAttributeValue("", REF_ATTR)

                if (ref != null) {
                    if (ref.startsWith(ITEXT_OPEN) && ref.endsWith(ITEXT_CLOSE)) {
                        textRef = ref.substring(ITEXT_OPEN.length, ref.indexOf(ITEXT_CLOSE))
                        verifyTextMappings(textRef, "Item <label>", true)
                    } else {
                        throw XFormParseException("malformed ref [$ref] for <item>", child)
                    }
                }
            } else if (VALUE == childName) {
                value = getXMLText(child!!, true)

                if (XFormUtils.showUnusedAttributeWarning(child, valueUA)) {
                    reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(child, valueUA), getVagueLocation(child))
                }

                if (value != null) {
                    if (value.length > MAX_VALUE_LEN) {
                        reporter.warning(
                            XFormParserReporter.TYPE_ERROR_PRONE,
                            "choice value [$value] is too long; max. suggested length $MAX_VALUE_LEN chars",
                            getVagueLocation(child)
                        )
                    }

                    for (k in 0 until value.length) {
                        val c = value[k]
                        if (" \n\t\u000C\r'\"`".indexOf(c) >= 0) {
                            val isMultiSelect = q.getControlType() == Constants.CONTROL_SELECT_MULTI
                            reporter.warning(
                                XFormParserReporter.TYPE_ERROR_PRONE,
                                (if (isMultiSelect) SELECT else SELECTONE) + " question <value>s [$value] " +
                                        (if (isMultiSelect) "cannot" else "should not") +
                                        " contain spaces, and are recommended not to contain apostraphes/quotation marks",
                                getVagueLocation(child)
                            )
                            break
                        }
                    }
                }
            }
        }

        if (textRef == null && labelInnerText == null) {
            throw XFormParseException("<item> without proper <label>", e)
        }
        if (value == null) {
            throw XFormParseException("<item> without proper <value>", e)
        }

        if (textRef != null) {
            q.addSelectChoice(SelectChoice(textRef, value))
        } else {
            q.addSelectChoice(SelectChoice(null, labelInnerText, value, false))
        }

        if (XFormUtils.showUnusedAttributeWarning(e, usedAtts)) {
            reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(e, usedAtts), getVagueLocation(e))
        }
    }

    private fun parseItemset(q: QuestionDef, e: Element) {
        val itemset = ItemsetBinding()

        val usedAtts = Vector<String>()
        val labelUA = Vector<String>()
        val valueUA = Vector<String>()
        val copyUA = Vector<String>()
        usedAtts.addElement(NODESET_ATTR)
        labelUA.addElement(REF_ATTR)
        valueUA.addElement(REF_ATTR)
        valueUA.addElement(FORM_ATTR)
        copyUA.addElement(REF_ATTR)

        itemset.contextRef = getFormElementRef(q)
        val nodesetStr = e.getAttributeValue("", NODESET_ATTR)
        ItemSetParsingUtils.setNodeset(itemset, nodesetStr, e.name)

        for (i in 0 until e.childCount) {
            val type = e.getType(i)
            val child: Element? = if (type == Node.ELEMENT) e.getElement(i) else null
            val childName: String? = child?.name

            if (LABEL_ELEMENT == childName) {
                parseItemsetLabelElement(child!!, itemset, labelUA)
            } else if ("copy" == childName) {
                parseItemsetCopyElement(child!!, itemset, copyUA)
            } else if (VALUE == childName) {
                parseItemsetValueElement(child!!, itemset, valueUA)
            } else if (SORT == childName) {
                parseItemsetSortElement(child!!, itemset)
            }
        }

        if (itemset.labelRef == null) {
            throw XFormParseException("<itemset> requires <label>")
        } else if (itemset.copyRef == null && itemset.valueRef == null) {
            throw XFormParseException("<itemset> requires <copy> or <value>")
        }

        if (itemset.copyRef != null) {
            if (itemset.valueRef == null) {
                reporter.warning(
                    XFormParserReporter.TYPE_TECHNICAL,
                    "<itemset>s with <copy> are STRONGLY recommended to have <value> as well; pre-selecting, default answers, and display of answers will not work properly otherwise",
                    getVagueLocation(e)
                )
            } else if (!itemset.copyRef!!.isParentOf(itemset.valueRef!!, false)) {
                throw XFormParseException("<value> is outside <copy>")
            }
        }

        q.setDynamicChoices(itemset)
        itemsets.addElement(itemset)

        if (XFormUtils.showUnusedAttributeWarning(e, usedAtts)) {
            reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(e, usedAtts), getVagueLocation(e))
        }
    }

    private fun parseItemsetLabelElement(child: Element, itemset: ItemsetBinding, labelUA: Vector<String>) {
        val labelXpath = child.getAttributeValue("", REF_ATTR)

        if (XFormUtils.showUnusedAttributeWarning(child, labelUA)) {
            reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(child, labelUA), getVagueLocation(child))
        }

        ItemSetParsingUtils.setLabel(itemset, labelXpath)
    }

    private fun parseItemsetCopyElement(child: Element, itemset: ItemsetBinding, copyUA: Vector<String>) {
        val copyRef = child.getAttributeValue("", REF_ATTR)
        if (XFormUtils.showUnusedAttributeWarning(child, copyUA)) {
            reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(child, copyUA), getVagueLocation(child))
        }
        if (copyRef == null) {
            throw XFormParseException("<copy> in <itemset> requires 'ref'")
        }
        itemset.copyRef = DataInstance.unpackReference(getAbsRef(XPathReference(copyRef), itemset.nodesetRef!!))
        itemset.copyMode = true
    }

    private fun parseItemsetValueElement(child: Element, itemset: ItemsetBinding, valueUA: Vector<String>) {
        val valueXpath = child.getAttributeValue("", REF_ATTR)

        if (XFormUtils.showUnusedAttributeWarning(child, valueUA)) {
            reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(child, valueUA), getVagueLocation(child))
        }
        ItemSetParsingUtils.setValue(itemset, valueXpath)
    }

    private fun parseItemsetSortElement(child: Element, itemset: ItemsetBinding) {
        val sortXpathString = child.getAttributeValue("", REF_ATTR)
        ItemSetParsingUtils.setSort(itemset, sortXpathString)
    }

    private fun parseGroup(parent: IFormElement, e: Element, groupType: Int) {
        val group = GroupDef()
        group.setID(serialQuestionID++)
        var dataRef: XPathReference? = null
        var refFromBind = false

        val usedAtts = Vector<String>()
        usedAtts.addElement(REF_ATTR)
        usedAtts.addElement(NODESET_ATTR)
        usedAtts.addElement(BIND_ATTR)
        usedAtts.addElement(APPEARANCE_ATTR)
        usedAtts.addElement("count")
        usedAtts.addElement("noAddRemove")

        if (groupType == CONTAINER_REPEAT) {
            group.setIsRepeat(true)
        }

        val ref = e.getAttributeValue(null, REF_ATTR)
        val nodeset = e.getAttributeValue(null, NODESET_ATTR)
        val bind = e.getAttributeValue(null, BIND_ATTR)
        group.setAppearanceAttr(e.getAttributeValue(null, APPEARANCE_ATTR))

        if (bind != null) {
            val binding = bindingsByID[bind]
                ?: throw XFormParseException("XForm Parse: invalid binding ID [$bind]", e)
            dataRef = binding.reference
            refFromBind = true
        } else {
            if (group.isRepeat()) {
                if (nodeset != null) {
                    dataRef = XPathReference(nodeset)
                } else {
                    throw XFormParseException("XForm Parse: <repeat> with no binding ('bind' or 'nodeset')", e)
                }
            } else {
                if (ref != null) {
                    dataRef = XPathReference(ref)
                }
            }
        }

        if (!refFromBind) {
            dataRef = getAbsRef(dataRef, parent)
        }
        group.setBind(dataRef)

        if (group.isRepeat()) {
            repeats.addElement(dataRef!!.reference)

            val countRef = e.getAttributeValue(NAMESPACE_JAVAROSA, "count")
            if (countRef != null) {
                group.count = getAbsRef(XPathReference(countRef), parent)
                group.noAddRemove = true
            } else {
                group.noAddRemove = e.getAttributeValue(NAMESPACE_JAVAROSA, "noAddRemove") != null
            }
        }

        for (i in 0 until e.childCount) {
            val type = e.getType(i)
            val child: Element? = if (type == Node.ELEMENT) e.getElement(i) else null
            val childName: String? = child?.name
            val childNamespace: String? = child?.namespace

            if (group.isRepeat() && NAMESPACE_JAVAROSA == childNamespace) {
                when (childName) {
                    "chooseCaption" -> group.chooseCaption = getLabelOrTextId(child!!)
                    "addCaption" -> group.addCaption = getLabelOrTextId(child!!)
                    "delCaption" -> group.delCaption = getLabelOrTextId(child!!)
                    "doneCaption" -> group.doneCaption = getLabelOrTextId(child!!)
                    "addEmptyCaption" -> group.addEmptyCaption = getLabelOrTextId(child!!)
                    "doneEmptyCaption" -> group.doneEmptyCaption = getLabelOrTextId(child!!)
                    "entryHeader" -> group.entryHeader = getLabelOrTextId(child!!)
                    "delHeader" -> group.delHeader = getLabelOrTextId(child!!)
                    "mainHeader" -> group.mainHeader = getLabelOrTextId(child!!)
                }
            }
        }

        for (i in 0 until e.childCount) {
            if (e.getType(i) == Element.ELEMENT) {
                parseElement(e.getElement(i), group, groupLevelHandlers)
            }
        }

        if (XFormUtils.showUnusedAttributeWarning(e, usedAtts)) {
            reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(e, usedAtts), getVagueLocation(e))
        }

        parent.addChild(group)
    }

    private fun getFormElementRef(fe: IFormElement): TreeReference {
        return if (fe is FormDef) {
            val ref = TreeReference.rootRef()
            ref.add(mainInstanceNode!!.name, 0)
            ref
        } else {
            fe.getBind()!!.reference
        }
    }

    private fun getAbsRef(ref: XPathReference?, parent: IFormElement): XPathReference {
        return getAbsRef(ref, getFormElementRef(parent))
    }

    fun registerExtensionParser(parser: QuestionExtensionParser) {
        extensionParsers.addElement(parser)
    }

    /**
     * Notify parser about a node that will later be relevant to an action.
     */
    fun registerActionTarget(target: TreeReference) {
        actionTargets.addElement(target)
    }

    fun setStringCache(stringCache: Interner<String>?) {
        this.stringCache = stringCache
    }

    private fun parseIText(itext: Element) {
        val l = Localizer(true, true)
        _f!!.setLocalizer(l)

        val usedAtts = Vector<String>()

        for (i in 0 until itext.childCount) {
            val trans = itext.getElement(i) ?: continue
            if (trans.name != "translation") continue
            parseTranslation(l, trans)
        }

        if (l.availableLocales.isEmpty()) {
            throw XFormParseException("no <translation>s defined", itext)
        }

        if (l.defaultLocale == null) {
            l.setDefaultLocale(l.availableLocales[0])
        }

        if (XFormUtils.showUnusedAttributeWarning(itext, usedAtts)) {
            reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(itext, usedAtts), getVagueLocation(itext))
        }
    }

    private fun parseTranslation(l: Localizer, trans: Element) {
        val usedAtts = Vector<String>()
        usedAtts.addElement("lang")
        usedAtts.addElement("default")

        val lang = trans.getAttributeValue("", "lang")
        if (lang == null || lang.isEmpty()) {
            throw XFormParseException("no language specified for <translation>", trans)
        }
        val isDefault = trans.getAttributeValue("", "default")

        if (!l.addAvailableLocale(lang)) {
            throw XFormParseException("duplicate <translation> for language '$lang'", trans)
        }

        if (isDefault != null) {
            if (l.defaultLocale != null) {
                throw XFormParseException("more than one <translation> set as default", trans)
            }
            l.setDefaultLocale(lang)
        }

        val source = TableLocaleSource()

        var j = 0
        while (j < trans.childCount) {
            val text = trans.getElement(j)
            if (text == null || text.name != "text") {
                j++
                continue
            }

            parseTextHandle(source, text)
            trans.removeChild(j)
            --j
            j++
        }

        if (XFormUtils.showUnusedAttributeWarning(trans, usedAtts)) {
            reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(trans, usedAtts), getVagueLocation(trans))
        }

        l.registerLocaleResource(lang, source)
    }

    private fun parseTextHandle(l: TableLocaleSource, text: Element) {
        val id = text.getAttributeValue("", ID_ATTR)

        val usedAtts = Vector<String>()
        val childUsedAtts = Vector<String>()
        usedAtts.addElement(ID_ATTR)
        usedAtts.addElement(FORM_ATTR)
        childUsedAtts.addElement(FORM_ATTR)
        childUsedAtts.addElement(ID_ATTR)

        if (id == null || id.isEmpty()) {
            throw XFormParseException("no id defined for <text>", text)
        }

        for (k in 0 until text.childCount) {
            val value = text.getElement(k) ?: continue
            if (value.name != VALUE) {
                throw XFormParseException("Unrecognized element [${value.name}] in Itext->translation->text")
            }

            var form: String? = value.getAttributeValue("", FORM_ATTR)
            if (form != null && form.isEmpty()) {
                form = null
            }
            var data = getLabel(value)
            if (data == null) {
                data = ""
            }

            val textID = if (form == null) id else "$id;$form"
            if (l.hasMapping(textID)) {
                throw XFormParseException(
                    "duplicate definition for text ID \"$id\" and form \"$form\". Can only have one definition for each text form.",
                    text
                )
            }
            l.setLocaleMapping(textID, data)

            if (XFormUtils.showUnusedAttributeWarning(value, childUsedAtts)) {
                reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(value, childUsedAtts), getVagueLocation(value))
            }
        }

        if (XFormUtils.showUnusedAttributeWarning(text, usedAtts)) {
            reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(text, usedAtts), getVagueLocation(text))
        }
    }

    private fun hasITextMapping(textID: String, locale: String?): Boolean {
        val l = _f!!.getLocalizer()!!
        return l.hasMapping(locale ?: l.defaultLocale, textID)
    }

    private fun verifyTextMappings(textID: String, type: String, allowSubforms: Boolean) {
        val l = _f!!.getLocalizer()!!
        val locales = l.availableLocales

        for (locale in locales) {
            if (!(hasITextMapping(textID, locale) ||
                        (allowSubforms && hasSpecialFormMapping(textID, locale!!)))
            ) {
                if (locale == l.defaultLocale) {
                    throw XFormParseException("$type '$textID': text is not localizable for default locale [${l.defaultLocale}]!")
                } else {
                    reporter.warning(
                        XFormParserReporter.TYPE_TECHNICAL,
                        "$type '$textID': text is not localizable for locale $locale.",
                        null
                    )
                }
            }
        }
    }

    private fun hasSpecialFormMapping(textID: String, locale: String): Boolean {
        for (guess in itextKnownForms) {
            if (hasITextMapping("$textID;$guess", locale)) {
                return true
            }
        }
        for (key in _f!!.getLocalizer()!!.getLocaleData(locale)!!.keys) {
            if (key.startsWith("$textID;")) {
                val textForm = key.substring(key.indexOf(";") + 1, key.length)
                if (!itextKnownForms.contains(textForm)) {
                    System.out.println("adding unexpected special itext form: $textForm to list of expected forms")
                    itextKnownForms.addElement(textForm)
                }
                return true
            }
        }
        return false
    }

    private fun processStandardBindAttributes(usedAtts: Vector<String>, e: Element): DataBinding {
        usedAtts.addElement(ID_ATTR)
        usedAtts.addElement(NODESET_ATTR)
        usedAtts.addElement("type")
        usedAtts.addElement("relevant")
        usedAtts.addElement("required")
        usedAtts.addElement("readonly")
        usedAtts.addElement("constraint")
        usedAtts.addElement("constraintMsg")
        usedAtts.addElement("calculate")
        usedAtts.addElement("preload")
        usedAtts.addElement("preloadParams")

        val binding = DataBinding()

        binding.id = e.getAttributeValue("", ID_ATTR)

        val nodeset = e.getAttributeValue(null, NODESET_ATTR)
            ?: throw XFormParseException("XForm Parse: <bind> without nodeset", e)
        val ref: XPathReference
        try {
            ref = XPathReference(nodeset)
        } catch (xpe: XPathException) {
            throw XFormParseException(xpe.message)
        }
        val absRef = getAbsRef(ref, _f!!)
        binding.reference = absRef

        binding.dataType = getDataType(e.getAttributeValue(null, "type"))

        val xpathRel = e.getAttributeValue(null, "relevant")
        if (xpathRel != null) {
            if ("true()" == xpathRel) {
                binding.relevantAbsolute = true
            } else if ("false()" == xpathRel) {
                binding.relevantAbsolute = false
            } else {
                try {
                    var c = buildCondition(xpathRel, "relevant", absRef)
                    c = _f!!.addTriggerable(c) as Condition
                    binding.relevancyCondition = c
                } catch (xue: XPathUnsupportedException) {
                    throw buildParseException(nodeset, xue.message!!, xpathRel, "display condition")
                }
            }
        }

        val xpathReq = e.getAttributeValue(null, "required")
        if (xpathReq != null) {
            if ("true()" == xpathReq) {
                binding.requiredAbsolute = true
            } else if ("false()" == xpathReq) {
                binding.requiredAbsolute = false
            } else {
                try {
                    var c = buildCondition(xpathReq, "required", absRef)
                    c = _f!!.addTriggerable(c) as Condition
                    binding.requiredCondition = c
                } catch (xue: XPathUnsupportedException) {
                    throw buildParseException(nodeset, xue.message!!, xpathReq, "required condition")
                }
            }
        }

        val xpathRO = e.getAttributeValue(null, "readonly")
        if (xpathRO != null) {
            if ("true()" == xpathRO) {
                binding.readonlyAbsolute = true
            } else if ("false()" == xpathRO) {
                binding.readonlyAbsolute = false
            } else {
                try {
                    var c = buildCondition(xpathRO, "readonly", absRef)
                    c = _f!!.addTriggerable(c) as Condition
                    binding.readonlyCondition = c
                } catch (xue: XPathUnsupportedException) {
                    throw buildParseException(nodeset, xue.message!!, xpathRO, "read-only condition")
                }
            }
        }

        val xpathConstr = e.getAttributeValue(null, "constraint")
        if (xpathConstr != null) {
            try {
                binding.constraint = XPathConditional(xpathConstr)
            } catch (xse: XPathSyntaxException) {
                throw buildParseException(nodeset, xse.message!!, xpathConstr, "validation")
            }
            binding.constraintMessage = e.getAttributeValue(NAMESPACE_JAVAROSA, "constraintMsg")
        }

        val xpathCalc = e.getAttributeValue(null, "calculate")
        if (xpathCalc != null) {
            var r: Recalculate
            try {
                r = buildCalculate(xpathCalc, absRef)
            } catch (xpse: XPathSyntaxException) {
                throw buildParseException(nodeset, xpse.message!!, xpathCalc, "calculate")
            }
            try {
                r = _f!!.addTriggerable(r) as Recalculate
            } catch (xpe: XPathException) {
                throw buildParseException(nodeset, xpe.message!!, xpathCalc, "calculate")
            }
            binding.calculate = r
        }

        binding.preload = e.getAttributeValue(NAMESPACE_JAVAROSA, "preload")
        binding.preloadParams = e.getAttributeValue(NAMESPACE_JAVAROSA, "preloadParams")

        return binding
    }

    private fun parseBind(e: Element) {
        val usedAtts = Vector<String>()

        val binding = processStandardBindAttributes(usedAtts, e)

        if (XFormUtils.showUnusedAttributeWarning(e, usedAtts)) {
            reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(e, usedAtts), getVagueLocation(e))
        }

        addBinding(binding)
    }

    private fun buildCondition(xpath: String, type: String, contextRef: XPathReference): Condition {
        val cond: XPathConditional
        var trueAction = -1
        var falseAction = -1

        val prettyType: String

        when (type) {
            "relevant" -> {
                prettyType = "display condition"
                trueAction = Condition.ACTION_SHOW
                falseAction = Condition.ACTION_HIDE
            }
            "required" -> {
                prettyType = "require condition"
                trueAction = Condition.ACTION_REQUIRE
                falseAction = Condition.ACTION_DONT_REQUIRE
            }
            "readonly" -> {
                prettyType = "readonly condition"
                trueAction = Condition.ACTION_DISABLE
                falseAction = Condition.ACTION_ENABLE
            }
            else -> prettyType = "unknown condition"
        }

        try {
            cond = XPathConditional(xpath)
        } catch (xse: XPathSyntaxException) {
            val errorMessage = "Encountered a problem with $prettyType for node [${contextRef.reference}] at line: $xpath, ${xse.message}"
            reporter.error(errorMessage)
            throw XFormParseException(errorMessage)
        }

        return Condition(cond, trueAction, falseAction, DataInstance.unpackReference(contextRef))
    }

    private fun addBinding(binding: DataBinding) {
        bindings.addElement(binding)

        if (binding.id != null) {
            if (bindingsByID.put(binding.id, binding) != null) {
                throw XFormParseException("XForm Parse: <bind>s with duplicate ID: '${binding.id}'")
            }
        }
    }

    private fun addMainInstanceToFormDef(e: Element, instanceModel: FormInstance) {
        loadInstanceData(e, instanceModel.getRoot())

        checkDependencyCycles()
        _f!!.setInstance(instanceModel)
        try {
            _f!!.finalizeTriggerables()
        } catch (ise: IllegalStateException) {
            throw XFormParseException(
                ise.message ?: "Form has an illegal cycle in its calculate and relevancy expressions!"
            )
        }
    }

    private fun parseInstance(e: Element, isMainInstance: Boolean): FormInstance {
        val name = instanceNodeIdStrs.elementAt(instanceNodes.indexOf(e))

        val root = buildInstanceStructure(e, null, if (!isMainInstance) name else null, e.namespace)
        val instanceModel = FormInstance(root, if (!isMainInstance) name else null)
        if (isMainInstance) {
            instanceModel.setName(_f!!.getTitle())
        } else {
            instanceModel.setName(name)
        }

        val usedAtts = Vector<String>()
        usedAtts.addElement("version")
        usedAtts.addElement("uiVersion")
        usedAtts.addElement("name")

        val schema = e.namespace
        if (schema != null && schema.isNotEmpty() && schema != defaultNamespace) {
            instanceModel.schema = schema
        }
        instanceModel.formVersion = e.getAttributeValue(null, "version")
        instanceModel.uiVersion = e.getAttributeValue(null, "uiVersion")

        loadNamespaces(e, instanceModel)
        if (isMainInstance) {
            processRepeats(instanceModel)
            verifyBindings(instanceModel)
            verifyActions(instanceModel)
        }
        applyInstanceProperties(instanceModel)

        if (XFormUtils.showUnusedAttributeWarning(e, usedAtts)) {
            reporter.warning(XFormParserReporter.TYPE_UNKNOWN_MARKUP, XFormUtils.unusedAttWarning(e, usedAtts), getVagueLocation(e))
        }

        return instanceModel
    }

    private fun getRepeatableRefs(): Vector<TreeReference> {
        val refs = Vector<TreeReference>()

        for (i in 0 until repeats.size) {
            refs.addElement(repeats.elementAt(i))
        }

        for (i in 0 until itemsets.size) {
            val itemset = itemsets.elementAt(i)
            val srcRef = itemset.nodesetRef!!
            if (!refs.contains(srcRef)) {
                var nonstatic = true
                for (j in 0 until srcRef.size()) {
                    if (TreeReference.NAME_WILDCARD == srcRef.getName(j)) {
                        nonstatic = false
                    }
                }

                if (srcRef.instanceName != null) {
                    nonstatic = false
                }
                if (nonstatic) {
                    refs.addElement(srcRef)
                }
            }

            if (itemset.copyMode) {
                val destRef = itemset.getDestRef()!!
                if (!refs.contains(destRef)) {
                    refs.addElement(destRef)
                }
            }
        }

        return refs
    }

    private fun processRepeats(instance: FormInstance) {
        flagRepeatables(instance)
        processTemplates(instance)
        checkHomogeneity(instance)
    }

    private fun flagRepeatables(instance: FormInstance) {
        val refs = getRepeatableRefs()
        for (i in 0 until refs.size) {
            val ref = refs.elementAt(i)
            val nodes = EvaluationContext(instance).expandReference(ref, true)!!
            for (j in 0 until nodes.size) {
                val nref = nodes.elementAt(j)
                val node = instance.resolveReference(nref)
                if (node != null) {
                    node.setRepeatable(true)
                }
            }
        }
    }

    private fun processTemplates(instance: FormInstance) {
        repeatTree = buildRepeatTree(getRepeatableRefs(), instance.getRoot().getName()!!)

        val missingTemplates = Vector<TreeReference>()
        checkRepeatsForTemplate(instance, repeatTree, missingTemplates)

        removeInvalidTemplates(instance, repeatTree)
        createMissingTemplates(instance, missingTemplates)
    }

    private fun removeInvalidTemplates(instance: FormInstance, repeatTree: FormInstance?) {
        removeInvalidTemplates(
            instance.getRoot(),
            if (repeatTree == null) null else repeatTree!!.getRoot(),
            true
        )
    }

    private fun removeInvalidTemplates(
        instanceNode: TreeElement,
        repeatTreeNode: TreeElement?,
        templateAllowed: Boolean
    ): Boolean {
        val mult = instanceNode.getMult()
        val repeatable = repeatTreeNode != null && repeatTreeNode.isRepeatable
        var currentTemplateAllowed = templateAllowed

        if (mult == TreeReference.INDEX_TEMPLATE) {
            if (!currentTemplateAllowed) {
                reporter.warning(
                    XFormParserReporter.TYPE_INVALID_STRUCTURE,
                    "Template nodes for sub-repeats must be located within the template node of the parent repeat; ignoring template... [${instanceNode.getName()}]",
                    null
                )
                return true
            } else if (!repeatable) {
                reporter.warning(
                    XFormParserReporter.TYPE_INVALID_STRUCTURE,
                    "Warning: template node found for ref that is not repeatable; ignoring... [${instanceNode.getName()}]",
                    null
                )
                return true
            }
        }

        if (repeatable && mult != TreeReference.INDEX_TEMPLATE) {
            currentTemplateAllowed = false
        }

        var i = 0
        while (i < instanceNode.getNumChildren()) {
            val child = instanceNode.getChildAt(i)!!
            val rchild = if (repeatTreeNode == null) null else repeatTreeNode.getChild(child.getName()!!, 0)

            if (removeInvalidTemplates(child, rchild, currentTemplateAllowed)) {
                instanceNode.removeChildAt(i)
                i--
            }
            i++
        }
        return false
    }

    private fun createMissingTemplates(instance: FormInstance, missingTemplates: Vector<TreeReference>) {
        for (i in 0 until missingTemplates.size) {
            val templRef = missingTemplates.elementAt(i)
            val firstMatch: TreeReference

            val ref = templRef.clone()
            for (j in 0 until ref.size()) {
                ref.setMultiplicity(j, TreeReference.INDEX_UNBOUND)
            }
            val nodes = EvaluationContext(instance).expandReference(ref)!!
            if (nodes.size == 0) {
                continue
            } else {
                firstMatch = nodes.elementAt(0)
            }

            try {
                instance.copyNode(firstMatch, templRef)
            } catch (e: InvalidReferenceException) {
                reporter.warning(
                    XFormParserReporter.TYPE_INVALID_STRUCTURE,
                    "Could not create a default repeat template; this is almost certainly a homogeneity error! Your form will not work! (Failed on ${templRef})",
                    null
                )
            }
            trimRepeatChildren(instance.resolveReference(templRef)!!)
        }
    }

    private fun checkHomogeneity(instance: FormInstance) {
        val refs = getRepeatableRefs()
        for (i in 0 until refs.size) {
            val ref = refs.elementAt(i)
            var template: TreeElement? = null
            val nodes = EvaluationContext(instance).expandReference(ref)!!
            for (j in 0 until nodes.size) {
                val nref = nodes.elementAt(j)
                val node = instance.resolveReference(nref) ?: continue

                if (template == null) template = instance.getTemplate(nref)

                if (!FormInstance.isHomogeneous(template!!, node)) {
                    reporter.warning(
                        XFormParserReporter.TYPE_INVALID_STRUCTURE,
                        "Not all repeated nodes for a given repeat binding [${nref}] are homogeneous! This will cause serious problems!",
                        null
                    )
                }
            }
        }
    }

    private fun verifyBindings(instance: FormInstance) {
        val instanceContext = EvaluationContext(instance)
        var i = 0
        while (i < bindings.size) {
            val bind = bindings.elementAt(i)
            val ref = DataInstance.unpackReference(bind.reference!!)

            if (ref.size() == 0) {
                System.out.println("Cannot bind to '/'; ignoring bind...")
                bindings.removeElementAt(i)
                i--
            } else {
                val nodes = instanceContext.expandReference(ref, true)!!

                if (nodes.size == 0) {
                    if (ref.instanceName != null) {
                        reporter.warning(
                            XFormParserReporter.TYPE_ERROR_PRONE,
                            "<bind> points to an non-main instance (${ref.instanceName}), which is read-only.",
                            null
                        )
                    } else {
                        reporter.warning(
                            XFormParserReporter.TYPE_ERROR_PRONE,
                            "<bind> defined for a node that doesn't exist [${ref}]. The node was renamed and the bind should be updated accordingly.",
                            null
                        )
                    }
                }
            }
            i++
        }

        val refs = getRepeatableRefs()
        for (idx in 0 until refs.size) {
            val ref = refs.elementAt(idx)
            if (ref.size() <= 1) {
                throw XFormParseException("Cannot bind repeat to '/' or '/${mainInstanceNode!!.name}'")
            }
        }

        val bindErrors = Vector<String>()
        verifyControlBindings(_f!!, instance, bindErrors)
        if (bindErrors.size > 0) {
            var errorMsg = ""
            for (idx in 0 until bindErrors.size) {
                errorMsg += bindErrors.elementAt(idx) + "\n"
            }
            throw XFormParseException(errorMsg)
        }

        verifyRepeatMemberBindings(_f!!, instance, null)
        verifyItemsetBindings(instance)
        verifyItemsetSrcDstCompatibility(instance)
    }

    private fun verifyActions(instance: FormInstance) {
        for (i in 0 until actionTargets.size) {
            val target = actionTargets.elementAt(i)
            val nodes = EvaluationContext(instance).expandReference(target, true)!!
            if (nodes.size == 0) {
                throw XFormParseException("Invalid Action - Targets non-existent node: ${target.toString(true)}")
            }
        }
    }

    private fun verifyControlBindings(fe: IFormElement, instance: FormInstance, errors: Vector<String>) {
        if (fe.getChildren() == null) return

        for (i in 0 until fe.getChildren()!!.size) {
            val child = fe.getChildren()!!.elementAt(i)
            var ref: XPathReference? = null
            var type: String? = null

            if (child is GroupDef) {
                ref = child.getBind()
                type = if (child.isRepeat()) "Repeat" else "Group"
            } else if (child is QuestionDef) {
                ref = child.getBind()
                type = "Question"
            }
            val tref = DataInstance.unpackReference(ref!!)

            if (child is QuestionDef && tref.size() == 0) {
                reporter.warning(XFormParserReporter.TYPE_INVALID_STRUCTURE, "Cannot bind control to '/'", null)
            } else {
                val nodes = EvaluationContext(instance).expandReference(tref, true)!!
                if (nodes.size == 0) {
                    val error = "$type bound to non-existent node: [${tref}]"
                    reporter.error(error)
                    errors.addElement(error)
                }
            }

            verifyControlBindings(child, instance, errors)
        }
    }

    private fun verifyRepeatMemberBindings(fe: IFormElement, instance: FormInstance, parentRepeat: GroupDef?) {
        if (fe.getChildren() == null) return

        for (i in 0 until fe.getChildren()!!.size) {
            val child = fe.getChildren()!!.elementAt(i)
            val isRepeat = child is GroupDef && child.isRepeat()

            val repeatBind = if (parentRepeat == null) TreeReference.rootRef() else DataInstance.unpackReference(parentRepeat.getBind()!!)
            val childBind = DataInstance.unpackReference(child.getBind()!!)

            if (!repeatBind.isParentOf(childBind, false)) {
                throw XFormParseException(
                    "<repeat> member's binding [${childBind}] is not a descendant of <repeat> binding [${repeatBind}]!"
                )
            } else if (repeatBind == childBind && isRepeat) {
                throw XFormParseException(
                    "child <repeat>s [${childBind}] cannot bind to the same node as their parent <repeat>; only questions/groups can"
                )
            }

            val repeatAncestry = Vector<TreeElement>()
            var repeatNode: TreeElement? = if (repeatTree == null) null else repeatTree!!.getRoot()
            if (repeatNode != null) {
                repeatAncestry.addElement(repeatNode)
                for (j in 1 until childBind.size()) {
                    repeatNode = repeatNode!!.getChild(childBind.getName(j)!!, 0)
                    if (repeatNode != null) {
                        repeatAncestry.addElement(repeatNode)
                    } else {
                        break
                    }
                }
            }
            for (k in repeatBind.size() until childBind.size()) {
                val rChild = if (k < repeatAncestry.size) repeatAncestry.elementAt(k) else null
                val repeatable = rChild != null && rChild.isRepeatable
                if (repeatable && !(k == childBind.size() - 1 && isRepeat)) {
                    throw XFormParseException(
                        "<repeat> member's binding [${childBind}] is within the scope of a <repeat> that is not its closest containing <repeat>!"
                    )
                }
            }

            verifyRepeatMemberBindings(child, instance, if (isRepeat) child as GroupDef else parentRepeat)
        }
    }

    private fun verifyItemsetBindings(instance: FormInstance) {
        for (i in 0 until itemsets.size) {
            val itemset = itemsets.elementAt(i)

            if (!itemset.nodesetRef!!.isParentOf(itemset.labelRef!!, false)) {
                throw XFormParseException("itemset nodeset ref is not a parent of label ref")
            } else if (itemset.copyRef != null && !itemset.nodesetRef!!.isParentOf(itemset.copyRef!!, false)) {
                throw XFormParseException("itemset nodeset ref is not a parent of copy ref")
            } else if (itemset.valueRef != null && !itemset.nodesetRef!!.isParentOf(itemset.valueRef!!, false)) {
                throw XFormParseException("itemset nodeset ref is not a parent of value ref")
            }

            val fi: DataInstance<*>
            if (itemset.labelRef!!.instanceName != null) {
                fi = _f!!.getNonMainInstance(itemset.labelRef!!.instanceName)
                    ?: throw XFormParseException("Instance: ${itemset.labelRef!!.instanceName} Does not exists")
            } else {
                fi = instance
            }

            if (fi.isRuntimeEvaluated()) {
                return
            }

            if (fi.getTemplatePath(itemset.labelRef!!) == null) {
                throw XFormParseException("<label> node for itemset doesn't exist! [${itemset.labelRef}]")
            } else if (itemset.valueRef != null && fi.getTemplatePath(itemset.valueRef!!) == null) {
                throw XFormParseException("<value> node for itemset doesn't exist! [${itemset.valueRef}]")
            }
        }
    }

    private fun verifyItemsetSrcDstCompatibility(instance: FormInstance) {
        for (i in 0 until itemsets.size) {
            val itemset = itemsets.elementAt(i)

            val destRepeatable = instance.getTemplate(itemset.getDestRef()!!) != null
            if (itemset.copyMode) {
                if (!destRepeatable) {
                    throw XFormParseException("itemset copies to node(s) which are not repeatable")
                }

                val srcNode = instance.getTemplatePath(itemset.copyRef!!)!!
                val dstNode = instance.getTemplate(itemset.getDestRef()!!)!!

                if (!FormInstance.isHomogeneous(srcNode, dstNode)) {
                    reporter.warning(
                        XFormParserReporter.TYPE_INVALID_STRUCTURE,
                        "Your itemset source [${srcNode.getRef()}] and dest [${dstNode.getRef()}] of appear to be incompatible!",
                        null
                    )
                }
            } else {
                if (destRepeatable) {
                    throw XFormParseException("itemset sets value on repeatable nodes")
                }
            }
        }
    }

    private fun applyInstanceProperties(instance: FormInstance) {
        for (i in 0 until bindings.size) {
            val bind = bindings.elementAt(i)
            val ref = DataInstance.unpackReference(bind.reference!!)
            val nodes = EvaluationContext(instance).expandReference(ref, true)!!

            if (nodes.size > 0) {
                attachBindGeneral(bind)
            }
            for (j in 0 until nodes.size) {
                val nref = nodes.elementAt(j)
                attachBind(instance.resolveReference(nref)!!, bind)
            }
        }

        applyControlProperties(instance)
    }

    private fun applyControlProperties(instance: FormInstance) {
        for (h in 0 until 2) {
            val selectRefs = if (h == 0) selectOnes else selectMultis
            val type = if (h == 0) Constants.DATATYPE_CHOICE else Constants.DATATYPE_CHOICE_LIST

            for (i in 0 until selectRefs.size) {
                val ref = selectRefs.elementAt(i)
                val nodes = EvaluationContext(instance).expandReference(ref, true)!!
                for (j in 0 until nodes.size) {
                    val node = instance.resolveReference(nodes.elementAt(j))!!
                    if (node.getDataType() == Constants.DATATYPE_CHOICE || node.getDataType() == Constants.DATATYPE_CHOICE_LIST) {
                        // do nothing
                    } else if (node.getDataType() == Constants.DATATYPE_NULL || node.getDataType() == Constants.DATATYPE_TEXT) {
                        node.setDataType(type)
                    } else {
                        reporter.warning(
                            XFormParserReporter.TYPE_INVALID_STRUCTURE,
                            "Multiple choice question ${ref} appears to have data type that is incompatible with selection",
                            null
                        )
                    }
                }
            }
        }
    }

    private fun checkDependencyCycles() {
        val vertices = Vector<TreeReference>()
        val edges = Vector<Array<TreeReference>>()

        val it = _f!!.refWithTriggerDependencies()
        while (it.hasNext()) {
            val trigger = it.next()
            if (!vertices.contains(trigger)) vertices.addElement(trigger)

            @Suppress("UNCHECKED_CAST")
            val triggered = _f!!.conditionsTriggeredByRef(trigger) as Vector<org.javarosa.core.model.condition.Triggerable>
            val targets = Vector<TreeReference>()
            for (i in 0 until triggered.size) {
                val t = triggered.elementAt(i)
                for (j in 0 until t.targets.size) {
                    val target = t.targets.elementAt(j)
                    if (!targets.contains(target)) targets.addElement(target)
                }
            }

            for (i in 0 until targets.size) {
                val target = targets.elementAt(i)
                if (!vertices.contains(target)) vertices.addElement(target)

                val edge = arrayOf(trigger, target)
                edges.addElement(edge)
            }
        }

        var acyclic = true
        while (vertices.size > 0) {
            val leaves = Vector<TreeReference>()
            for (i in 0 until vertices.size) {
                leaves.addElement(vertices.elementAt(i))
            }
            for (i in 0 until edges.size) {
                val edge = edges.elementAt(i)
                leaves.removeElement(edge[0])
            }

            if (leaves.size == 0) {
                acyclic = false
                break
            }

            for (i in 0 until leaves.size) {
                val leaf = leaves.elementAt(i)
                vertices.removeElement(leaf)
            }
            for (i in edges.size - 1 downTo 0) {
                val edge = edges.elementAt(i)
                if (leaves.contains(edge[1])) edges.removeElementAt(i)
            }
        }

        if (!acyclic) {
            val errorMessage = ShortestCycleAlgorithm(edges).getCycleErrorMessage()
            reporter.error(errorMessage)
            throw XFormParseException(errorMessage)
        }
    }

    private fun getDataType(type: String?): Int {
        var dataType = Constants.DATATYPE_NULL

        if (type != null) {
            var processedType = type
            if (processedType.contains(":")) {
                processedType = processedType.substring(processedType.indexOf(":") + 1)
            }

            if (typeMappings.containsKey(processedType)) {
                dataType = typeMappings[processedType]!!
            } else {
                dataType = Constants.DATATYPE_UNSUPPORTED
                reporter.warning(XFormParserReporter.TYPE_ERROR_PRONE, "unrecognized data type [$processedType]", null)
            }
        }

        return dataType
    }
}
