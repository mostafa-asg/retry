package com.github.retry.circuit.breakers

interface Breaker {

    /**
     * Return `true` if circuit must be turned to `open` state
     */
    fun errorHappened(exc: Throwable): Boolean

    /**
     * Notify the breaker that operation has been done without error
     */
    fun successHappened()

    /**
     * Reset the state of the breaker
     */
    fun reset()
}