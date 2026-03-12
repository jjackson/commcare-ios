package org.commcare.suite.model

import org.javarosa.core.model.ItemsetBinding
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.services.locale.Localization
import org.javarosa.core.util.NoLocalizedTextException
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.xpath.expr.XPathExpression

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

// Model for <prompt> node
class QueryPrompt : Externalizable {

    private var _key: String? = null
    private var appearance: String? = null
    private var input: String? = null
    private var receive: String? = null
    private var hidden: String? = null
    private var display: DisplayUnit? = null
    private var defaultValueExpr: XPathExpression? = null
    private var itemsetBinding: ItemsetBinding? = null
    private var exclude: XPathExpression? = null
    private var required: QueryPromptCondition? = null
    private var allowBlankValue: Boolean = false
    private var validation: QueryPromptCondition? = null
    private var groupKey: String? = null

    constructor()

    constructor(
        key: String?, appearance: String?, input: String?, receive: String?,
        hidden: String?, display: DisplayUnit?, itemsetBinding: ItemsetBinding?,
        defaultValueExpr: XPathExpression?, allowBlankValue: Boolean, exclude: XPathExpression?,
        required: QueryPromptCondition?, validation: QueryPromptCondition?, groupKey: String?
    ) {
        this._key = key
        this.appearance = appearance
        this.input = input
        this.receive = receive
        this.hidden = hidden
        this.display = display
        this.itemsetBinding = itemsetBinding
        this.defaultValueExpr = defaultValueExpr
        this.allowBlankValue = allowBlankValue
        this.exclude = exclude
        this.required = required
        this.validation = validation
        this.groupKey = groupKey
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        _key = SerializationHelpers.readString(`in`)
        appearance = SerializationHelpers.readNullableString(`in`, pf)
        input = SerializationHelpers.readNullableString(`in`, pf)
        receive = SerializationHelpers.readNullableString(`in`, pf)
        hidden = SerializationHelpers.readNullableString(`in`, pf)
        display = SerializationHelpers.readExternalizable(`in`, pf) { DisplayUnit() }
        itemsetBinding = SerializationHelpers.readNullableExternalizable(`in`, pf) { ItemsetBinding() }
        defaultValueExpr = SerializationHelpers.readNullableTagged(`in`, pf) as XPathExpression?
        allowBlankValue = SerializationHelpers.readBool(`in`)
        exclude = SerializationHelpers.readNullableTagged(`in`, pf) as XPathExpression?
        validation = SerializationHelpers.readNullableExternalizable(`in`, pf) { QueryPromptCondition() }
        required = SerializationHelpers.readNullableExternalizable(`in`, pf) { QueryPromptCondition() }
        groupKey = SerializationHelpers.readNullableString(`in`, pf)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.write(out, _key!!)
        SerializationHelpers.writeNullable(out, appearance)
        SerializationHelpers.writeNullable(out, input)
        SerializationHelpers.writeNullable(out, receive)
        SerializationHelpers.writeNullable(out, hidden)
        SerializationHelpers.write(out, display!!)
        SerializationHelpers.writeNullable(out, itemsetBinding)
        SerializationHelpers.writeNullableTagged(out, defaultValueExpr)
        SerializationHelpers.writeBool(out, allowBlankValue)
        SerializationHelpers.writeNullableTagged(out, exclude)
        SerializationHelpers.writeNullable(out, validation)
        SerializationHelpers.writeNullable(out, required)
        SerializationHelpers.writeNullable(out, groupKey)
    }

    fun getKey(): String? = _key

    fun getAppearance(): String? = appearance

    fun getInput(): String? = input

    fun getReceive(): String? = receive

    fun getHidden(): String? = hidden

    fun isAllowBlankValue(): Boolean = allowBlankValue

    fun getDisplay(): DisplayUnit? = display

    fun getItemsetBinding(): ItemsetBinding? = itemsetBinding

    fun getDefaultValueExpr(): XPathExpression? = defaultValueExpr

    fun getExclude(): XPathExpression? = exclude

    fun getRequired(): QueryPromptCondition? = required

    fun getValidation(): QueryPromptCondition? = validation

    fun getGroupKey(): String? = groupKey

    /**
     * @return whether the prompt has associated choices to select from
     */
    fun isSelect(): Boolean = getItemsetBinding() != null

    // Evaluates required in the given eval context
    fun isRequired(ec: EvaluationContext): Boolean {
        if (required != null && required!!.getTest() != null) {
            return required!!.getTest()!!.eval(ec) as Boolean
        }
        return false
    }

    // Evaluates required message in the given eval context
    fun getRequiredMessage(ec: EvaluationContext): String? {
        if (required != null && required!!.getMessage() != null) {
            return required!!.getMessage()!!.evaluate(ec)
        }

        return try {
            Localization.get("case.search.answer.required")
        } catch (nlte: NoLocalizedTextException) {
            DEFAULT_REQUIRED_ERROR
        }
    }

    /**
     * Evaluates validation message against given eval context
     *
     * @param ec eval context to evaluate the validation message
     * @return evaluated validation message or a default text if no validation message is defined
     */
    fun getValidationMessage(ec: EvaluationContext): String {
        if (validation != null && validation!!.getMessage() != null) {
            return validation!!.getMessage()!!.evaluate(ec)
        }

        return try {
            Localization.get("case.search.answer.invalid")
        } catch (nlte: NoLocalizedTextException) {
            DEFAULT_VALIDATION_ERROR
        }
    }

    /**
     * Evaluates the validation condition for the prompts
     *
     * @param ec eval context to evaluate the validation condition
     * @return whether the input is invalid
     */
    fun isInvalidInput(ec: EvaluationContext): Boolean {
        return validation != null && !(validation!!.getTest()!!.eval(ec) as Boolean)
    }

    companion object {
        // Spinner with single selection
        const val INPUT_TYPE_SELECT1 = "select1"

        // Spinner with multiple selection
        const val INPUT_TYPE_SELECT = "select"

        // widget to select a date range (start and end date)
        const val INPUT_TYPE_DATERANGE = "daterange"

        // widget to select a single date
        const val INPUT_TYPE_DATE = "date"

        // Checkbox, multiple selection
        const val INPUT_TYPE_CHECKBOX = "checkbox"

        // list of address fields like street, state, city etc
        const val INPUT_TYPE_ADDRESS = "address"
        const val DEFAULT_REQUIRED_ERROR = "Sorry, this response is required!"
        const val DEFAULT_VALIDATION_ERROR = "Sorry, this response is invalid!"
    }
}
