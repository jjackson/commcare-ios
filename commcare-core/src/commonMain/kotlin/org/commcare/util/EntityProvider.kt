package org.commcare.util

import org.commcare.cases.entity.Entity
import org.javarosa.core.model.instance.TreeReference

interface EntityProvider {
    fun getEntity(index: Int): Entity<TreeReference>?
}
