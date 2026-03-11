package org.javarosa.core.storage

import org.javarosa.core.services.storage.IMetaData
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.PrototypeFactory

/**
 * Basic model for storage
 *
 * Created by ctsims on 9/22/2017.
 */
class Shoe : Persistable, IMetaData {

    var brand: String? = null
    var size: String? = null
    var style: String? = null

    var recordId: Int = -1
    private var reviewText: String = ""

    constructor()

    constructor(brand: String?, style: String?, size: String?) {
        if (brand == null || style == null || size == null) {
            throw IllegalArgumentException("No values can be null")
        }
        this.brand = brand
        this.size = size
        this.style = style
    }

    fun setReview(reviewText: String?) {
        this.reviewText = reviewText ?: ""
    }

    override fun getMetaDataFields(): Array<String> {
        return arrayOf(META_BRAND, META_SIZE, META_STYLE)
    }

    override fun getMetaData(fieldName: String): Any {
        return when (fieldName) {
            META_BRAND -> brand!!
            META_SIZE -> size!!
            META_STYLE -> style!!
            else -> throw IllegalArgumentException("No meta field: $fieldName")
        }
    }

    override fun setID(ID: Int) {
        this.recordId = ID
    }

    override fun getID(): Int {
        return this.recordId
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        brand = ExtUtil.readString(`in`)
        style = ExtUtil.readString(`in`)
        size = ExtUtil.readString(`in`)
        reviewText = ExtUtil.readString(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeString(out, brand)
        ExtUtil.writeString(out, style)
        ExtUtil.writeString(out, size)
        ExtUtil.writeString(out, reviewText)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Shoe) return false
        return this.size == other.size &&
                this.style == other.style &&
                this.brand == other.brand &&
                this.reviewText == other.reviewText
    }

    override fun hashCode(): Int {
        return (this.size?.hashCode() ?: 0) xor
                (this.style?.hashCode() ?: 0) xor
                (this.brand?.hashCode() ?: 0) xor
                this.reviewText.hashCode()
    }

    fun getReviewText(): String = reviewText

    companion object {
        const val META_BRAND: String = "brand"
        const val META_SIZE: String = "size"
        const val META_STYLE: String = "style"
    }
}
