package org.javarosa.xpath.expr

import org.javarosa.core.model.instance.TreeReference

/**
 * Created by amstone326 on 3/23/18.
 */
class ExpressionCacheKey internal constructor(
    private val expr: InFormCacheableExpr,
    // These are each only relevant to caching in some cases, and will be null in a cache key for
    // which they are not relevant
    private val contextRef: TreeReference?,
    private val originalContextRef: TreeReference?
) {

    override fun equals(other: Any?): Boolean {
        if (other is ExpressionCacheKey) {
            return this.expr == other.expr &&
                    contextsEqual(this, other) &&
                    originalContextsEqual(this, other)
        }
        return false
    }

    override fun hashCode(): Int {
        var hash = expr.hashCode()
        if (contextRef != null) {
            hash = hash xor contextRef.hashCode()
        }
        if (originalContextRef != null) {
            hash = hash xor originalContextRef.hashCode()
        }
        return hash
    }

    companion object {
        private fun contextsEqual(ck1: ExpressionCacheKey, ck2: ExpressionCacheKey): Boolean {
            return if (ck1.contextRef == null) {
                ck2.contextRef == null
            } else {
                ck1.contextRef == ck2.contextRef
            }
        }

        private fun originalContextsEqual(ck1: ExpressionCacheKey, ck2: ExpressionCacheKey): Boolean {
            return if (ck1.originalContextRef == null) {
                ck2.originalContextRef == null
            } else {
                ck1.originalContextRef == ck2.originalContextRef
            }
        }
    }
}
