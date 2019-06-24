package com.github.retry.circuit

import com.github.retry.*
import com.github.retry.circuit.breakers.Breaker
import java.time.Duration

enum class CircuitStatus {
    CLOSED,
    OPEN
}

class CircuitPolicy(
        override val exceptions: List<RetryableException<*>>,
        override val onRetry: RetryHandler,
        override val onFailure: FailureHandler,
        override val sleepMillis: List<Long>,
        override val sleepFunc: SleepFunc,
        val closeCircuitAfter: Duration,
        val breaker: Breaker): Policy()


class CircuitBreaker(val policy: CircuitPolicy) {

    private val breaker = policy.breaker
    private val retry = ForeverRetry(
            ForeverRetryPolicy(
                    policy.exceptions,
                    this::onRetry,
                    policy.onFailure,
                    policy.sleepMillis,
                    policy.sleepFunc
            )
    )

    var circuitStatus = CircuitStatus.CLOSED
    var circuitOpenTime = 0L

    operator fun <R> invoke(recover: (() -> R), action: () -> R): R {
        val now = System.currentTimeMillis()

        if (circuitStatus == CircuitStatus.OPEN &&
            circuitOpenTime + policy.closeCircuitAfter.toMillis() < now) {
            breaker.reset()
            circuitStatus = CircuitStatus.CLOSED
        }

        return if (circuitStatus == CircuitStatus.OPEN) {
            recover()
        } else {

            val result = runCatching {
                retry.invoke(recover, action)
            }

            return if (result.isFailure) {
                recover()
            } else {
                breaker.successHappened()
                result.getOrThrow()
            }
        }
    }

    private fun onRetry(exc: Throwable, context: ExecutionContext) {
        policy.onRetry(exc, context)
        val mustBeOpen = breaker.errorHappened(exc)
        if (mustBeOpen) {
            circuitOpenTime = System.currentTimeMillis()
            circuitStatus = CircuitStatus.OPEN
            context.cancel()
        }
    }

}