package com.github.retry.circuit.breakers

/**
 * Simple threshold breaker that counts errors, and if errors reached the threshold,
 * It returns true to change the circuit to `open` state
 */
class SimpleThreshold(private val threshold: Long): Breaker {

    private var counter = 0L

    override fun errorHappened(exc: Throwable): Boolean {
        counter++

        if (counter == threshold) {
            counter = 0
            return true
        }

        return false
    }

    override fun reset() {
        counter = 0
    }

    override fun successHappened() {
        // Do nothing
    }

}