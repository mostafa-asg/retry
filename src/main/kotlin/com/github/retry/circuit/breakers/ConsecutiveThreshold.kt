package com.github.retry.circuit.breakers

/**
 * Only `open` the circuit if consecutive error happen
 */
class ConsecutiveThreshold(private val threshold: Long): Breaker {

    private var counter = 0L

    override fun errorHappened(exc: Throwable): Boolean {
        counter++

        if (counter == threshold) {
            resetCounter()
            return true
        }

        return false
    }

    override fun successHappened() {
        resetCounter()
    }

    override fun reset() {
        resetCounter()
    }

    private fun resetCounter() {
        counter = 0
    }

}