package org.commcare.suite.model

/**
 * Created by willpride on 4/13/16.
 */
class Style {
    var displayFormat: DisplayFormat? = null
        private set
    var fontSize: Int = 0
        private set
    var widthHint: Int = 0
        private set
    var horizontalAlign: String? = null
        private set
    var verticalAlign: String? = null
        private set
    var showBorder: Boolean = false
        private set
    var showShading: Boolean = false
        private set

    constructor()

    constructor(detail: DetailField) {
        val fontSizeStr = detail.fontSize
        if (fontSizeStr != null) {
            try {
                fontSize = fontSizeStr.toInt()
            } catch (nfe: NumberFormatException) {
                fontSize = 12
            }
        }
        // For width, default to -1 since '0' is reserved for hidden (Search) fields
        val widthHintStr = detail.templateWidthHint
        if (widthHintStr != null) {
            try {
                widthHint = widthHintStr.toInt()
            } catch (nfe: NumberFormatException) {
                widthHint = -1
            }
        } else {
            widthHint = -1
        }
        setDisplayFormatFromString(detail.templateForm)

        verticalAlign = detail.verticalAlign
        horizontalAlign = detail.horizontalAlign
        showBorder = detail.showBorder
        showShading = detail.showShading
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

    private fun setDisplayFormatFromString(displayFormat: String?) {
        when (displayFormat) {
            "image", "enum-image" -> this.displayFormat = DisplayFormat.Image
            "audio" -> this.displayFormat = DisplayFormat.Audio
            "text" -> this.displayFormat = DisplayFormat.Text
            "address" -> this.displayFormat = DisplayFormat.Address
            "address-popup" -> this.displayFormat = DisplayFormat.AddressPopup
            "graph" -> this.displayFormat = DisplayFormat.Graph
            "phone" -> this.displayFormat = DisplayFormat.Phone
            "markdown" -> this.displayFormat = DisplayFormat.Markdown
            "clickable-icon" -> this.displayFormat = DisplayFormat.ClickableIcon
        }
    }

    override fun toString(): String {
        return "Style: [displayFormat=$displayFormat, fontSize=$fontSize]"
    }
}
