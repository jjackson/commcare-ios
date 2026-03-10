package org.commcare.resources.model.installers

import org.commcare.resources.model.MissingMediaException
import org.commcare.resources.model.Resource
import org.javarosa.core.reference.InvalidReferenceException
import org.javarosa.core.reference.ReferenceManager
import org.javarosa.core.util.SizeBoundUniqueVector
import org.javarosa.core.util.externalizable.PlatformIOException

object InstallerUtil {

    enum class MediaType {
        IMAGE, AUDIO, VIDEO
    }

    @JvmStatic
    fun checkMedia(
        r: Resource, filePath: String,
        problems: SizeBoundUniqueVector<MissingMediaException>, mt: MediaType
    ) {
        try {
            val ref = ReferenceManager.instance().DeriveReference(filePath)
            val localName = ref.getLocalURI()
            try {
                if (!ref.doesBinaryExist()) {
                    val successfulAdd = problems.add(
                        MissingMediaException(
                            r, "Missing external media: $localName", filePath,
                            MissingMediaException.MissingMediaExceptionType.FILE_NOT_FOUND
                        )
                    )
                    if (successfulAdd) {
                        when (mt) {
                            MediaType.IMAGE -> problems.addBadImageReference()
                            MediaType.AUDIO -> problems.addBadAudioReference()
                            MediaType.VIDEO -> problems.addBadVideoReference()
                        }
                    }
                }
            } catch (e: PlatformIOException) {
                problems.addElement(
                    MissingMediaException(
                        r, "Problem reading external media: $localName", filePath,
                        MissingMediaException.MissingMediaExceptionType.FILE_NOT_ACCESSIBLE
                    )
                )
            }
        } catch (e: InvalidReferenceException) {
            //So the problem is that this might be a valid entry that depends on context
            //in the form, so we'll ignore this situation for now.
        }
    }
}
