package com.github.retry.circuit.breakers

import java.time.Duration

/**
 * Measure errors in time buckets, and if bucket reached the threshold it `open` the circuit
 */
class TimebucketThreshold(private val duration: Duration, private val threshold: Long): Breaker {

    private var counter = 0L

    private var bucketStart = 0L
    private var bucketStop = 0L

    override fun errorHappened(exc: Throwable): Boolean {

        fun createNewBucket(now: Long) {
            // Create a bucket
            bucketStart = now
            bucketStop = bucketStart + duration.toMillis()
        }

        val now = System.currentTimeMillis()

        if (counter == 0L) {
            createNewBucket(now)
        }

        if (now in bucketStart..(bucketStop - 1)) {
            counter++
        } else {
            createNewBucket(now)
            counter = 1 // reset counter
        }

        return counter == threshold
    }

    override fun reset() {
        counter = 0
    }

    override fun successHappened() {
        // DO NOTHING
    }

}