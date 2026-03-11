package org.commcare.suite.model

import org.javarosa.core.model.condition.EvaluationContext

/**
 * Interface to be implemented by objects that want to be
 * displayed in MenuLists and MenuGrids
 *
 * Created by wpride1 on 4/27/15.
 */
interface MenuDisplayable {

    fun getAudioURI(): String?

    fun getImageURI(): String?

    fun getDisplayText(ec: EvaluationContext?): String?

    fun getTextForBadge(ec: EvaluationContext?): PlatformSingle<String>

    fun getCommandID(): String?

    fun getRawBadgeTextObject(): Text?

    fun getRawText(): Text?
}
