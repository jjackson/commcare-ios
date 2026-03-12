package org.commcare.suite.model

/**
 * Created by willpride on 4/13/16.
 */
class Style {
    private var displayFormats: DisplayFormat? = null
    private var fontSize: Int = 0
    private var widthHint: Int = 0
    private var horizontalAlign: String? = null
    private var verticalAlign: String? = null
    private var showBorder: Boolean = false
    private var showShading: Boolean = false

    constructor()

    constructor(detail: DetailField) {
        val fontSizeStr = detail.getFontSize()
        if (fontSizeStr != null) {
            try {
                setFontSize(fontSizeStr.toInt())
            } catch (nfe: NumberFormatException) {
                setFontSize(12)
            }
        }
        // For width, default to -1 since '0' is reserved for hidden (Search) fields
        val widthHintStr = detail.getTemplateWidthHint()
        if (widthHintStr != null) {
            try {
                setWidthHint(widthHintStr.toInt())
            } catch (nfe: NumberFormatException) {
                setWidthHint(-1)
            }
        } else {
            setWidthHint(-1)
        }
        setDisplayFormatFromString(detail.getTemplateForm())

        verticalAlign = detail.getVerticalAlign()
        horizontalAlign = detail.getHorizontalAlign()
        showBorder = detail.getShowBorder()
        showShading = detail.getShowShading()
    }

    enum class DisplayFormat {
        Image,
        Audio,
        Text,
        Address,
        AddressPopup,
        Graph,
        Phone,
        Markdown,
        ClickableIcon,
    }

    fun getDisplayFormat(): DisplayFormat? = displayFormats

    private fun setDisplayFormat(displayFormats: DisplayFormat) {
        this.displayFormats = displayFormats
    }

    fun getFontSize(): Int = fontSize

    private fun setFontSize(fontSize: Int) {
        this.fontSize = fontSize
    }

    fun getWidthHint(): Int = widthHint

    private fun setWidthHint(widthHint: Int) {
        this.widthHint = widthHint
    }

    private fun setDisplayFormatFromString(displayFormat: String?) {
        when (displayFormat) {
            "image", "enum-image" -> setDisplayFormat(DisplayFormat.Image)
            "audio" -> setDisplayFormat(DisplayFormat.Audio)
            "text" -> setDisplayFormat(DisplayFormat.Text)
            "address" -> setDisplayFormat(DisplayFormat.Address)
            "address-popup" -> setDisplayFormat(DisplayFormat.AddressPopup)
            "graph" -> setDisplayFormat(DisplayFormat.Graph)
            "phone" -> setDisplayFormat(DisplayFormat.Phone)
            "markdown" -> setDisplayFormat(DisplayFormat.Markdown)
            "clickable-icon" -> setDisplayFormat(DisplayFormat.ClickableIcon)
        }
    }

    override fun toString(): String {
        return "Style: [displayFormat=$displayFormats, fontSize=$fontSize]"
    }

    fun getHorizontalAlign(): String? = horizontalAlign

    fun getVerticalAlign(): String? = verticalAlign

    fun getShowBorder(): Boolean = showBorder

    fun getShowShading(): Boolean = showShading
}
