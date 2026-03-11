package org.javarosa.core.model.condition


abstract class HereFunctionHandler : IFunctionHandler {

    protected var listener: HereFunctionHandlerListener? = null

    override fun getPrototypes(): ArrayList<*> {
        val p = ArrayList<Array<Any>>()
        p.add(arrayOf())
        return p
    }

    override fun rawArgs(): Boolean {
        return false
    }

    override fun getName(): String {
        return "here"
    }

    fun registerListener(listener: HereFunctionHandlerListener?) {
        this.listener = listener
    }

    fun unregisterListener() {
        this.listener = null
    }

    protected fun alertOnEval() {
        listener?.onHereFunctionEvaluated()
    }
}
