package org.commcare.util

import org.commcare.cases.entity.Entity
import org.commcare.suite.model.DetailField

/**
 * Contains all of the raw information needed by a PrintableDetailField
 */
class DetailFieldPrintInfo(
    @JvmField var field: DetailField,
    @JvmField var entity: Entity<*>,
    @JvmField var index: Int
)
