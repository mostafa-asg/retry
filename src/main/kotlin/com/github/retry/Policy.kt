package com.github.retry

import java.time.Duration

typealias RetryHandler = (Throwable, ExecutionContext) -> Unit
typealias FailureHandler = (Throwable) -> Unit

/**
 * Represent an exception that if condition is met, retry should be occur
 */
class RetryableException<T: Throwable>(val clazz: Class<T>, val condition: (T) -> Boolean = {true}) {

    fun match(throwable: Throwable): Boolean {
        if (clazz.isAssignableFrom(throwable.javaClass)) {
            val casted: T = throwable as T
            return condition(casted)
        }

        return false
    }

}

abstract class Policy {

    abstract val exceptions: List<RetryableException<*>>
    abstract val onRetry: RetryHandler
    abstract val onFailure: FailureHandler
    abstract val sleepMillis: List<Long>

    internal fun match(throwable: Throwable): Boolean {
        return exceptions.any { it.match(throwable) }
    }

    companion object {
        inline fun <reified T: Throwable> handle(): PolicyBuilder {
            return handle(T::class.java) {true}
        }

        inline fun <reified T: Throwable> handle(noinline condition: (T) -> Boolean): PolicyBuilder {
            return handle(T::class.java, condition)
        }

        fun <T: Throwable> handle(clazz: Class<T>, condition: (T) -> Boolean): PolicyBuilder {
            val builder = PolicyBuilder()
            builder.exceptions.add(RetryableException(clazz, condition))
            return builder
        }
    }

}

class PolicyBuilder internal constructor(): Policy() {
    override val exceptions = mutableListOf<RetryableException<*>>()
    override var onRetry: RetryHandler = { _, _ -> }
    override var onFailure: FailureHandler = { _ -> }
    override val sleepMillis = mutableListOf<Long>()

    inline fun <reified T: Throwable> or(): PolicyBuilder {
        return or(T::class.java) {true}
    }

    inline fun <reified T: Throwable> or(noinline condition: (Throwable) -> Boolean): PolicyBuilder {
        return or(T::class.java, condition)
    }

    fun <T: Throwable> or(clazz: Class<T>, condition: (Throwable) -> Boolean): PolicyBuilder {
        exceptions.add(RetryableException(clazz, condition))
        return this
    }

    fun sleep(millis: Long) {
        sleepMillis.clear()
        sleepMillis.add(millis)
    }

    fun sleep(millis: List<Long>) {
        sleepMillis.clear()
        sleepMillis.addAll(millis)
    }

    fun onRetry(retryFunc: RetryHandler): PolicyBuilder {
        this.onRetry = retryFunc
        return this
    }

    fun onFailure(failerFunc: FailureHandler): PolicyBuilder {
        this.onFailure = failerFunc
        return this
    }

    fun retry(maxRetry: Int): FixedRetry {
        val policy =  FixedRetryPolicy(
                exceptions,
                onRetry,
                onFailure,
                sleepMillis,
                maxRetry
        )

        return FixedRetry(policy)
    }

    fun forever(): ForeverRetry {
        val policy =  ForeverRetryPolicy(
                exceptions,
                onRetry,
                onFailure,
                sleepMillis
        )

        return ForeverRetry(policy)
    }

    fun time(duration: Duration): TimeBasedRetry {
        val policy =  TimeBasedPolicy(
                exceptions,
                onRetry,
                onFailure,
                sleepMillis,
                duration
        )

        return TimeBasedRetry(policy)
    }

}

interface ExecutionContext {
    fun cancel()
    fun attempt(): Int
}

open class FixedRetryPolicy(
        override val exceptions: List<RetryableException<*>>,
        override val onRetry: RetryHandler,
        override val onFailure: FailureHandler,
        override val sleepMillis: List<Long>,
        val maxRetry: Int): Policy()

class ForeverRetryPolicy(
        override val exceptions: List<RetryableException<*>>,
        override val onRetry: RetryHandler,
        override val onFailure: FailureHandler,
        override val sleepMillis: List<Long>): FixedRetryPolicy(exceptions, onRetry, onFailure, sleepMillis, Int.MAX_VALUE)

open class TimeBasedPolicy(
        override val exceptions: List<RetryableException<*>>,
        override val onRetry: RetryHandler,
        override val onFailure: FailureHandler,
        override val sleepMillis: List<Long>,
        val maxTime: Duration): Policy() {

    fun toForeverRetryPolicy(): ForeverRetryPolicy {
        return ForeverRetryPolicy(
                exceptions,
                onRetry,
                onFailure,
                sleepMillis
        )
    }

}

open class FixedRetry(val policy: FixedRetryPolicy) {

    private var giveUp = false

    private val executionContext = object: ExecutionContext {

        override fun cancel() {
            giveUp = true
        }

        private var attempt:Int = 0
        override fun attempt(): Int = attempt
        fun setAttempt(attempt: Int) {
            this.attempt = attempt
        }
    }

    private fun ExecutionContext.getNextSleep(): Long {
        if ( attempt()-1 < policy.sleepMillis.size) {
            return policy.sleepMillis[attempt()-1]
        }

        return policy.sleepMillis.last()
    }

    operator fun <R> invoke(recover: (() -> R)? = null, action: () -> R): R {
        var lastException: Throwable = Exception()

        for (attempt in 1..policy.maxRetry) {
            try {
                return action()
            }
            catch (exc: Throwable) {
                lastException = exc

                executionContext.setAttempt(attempt)

                if (policy.match(exc)) {
                    policy.onRetry(exc, executionContext)
                    if (giveUp) {
                        throw exc
                    }

                    Thread.sleep(executionContext.getNextSleep())
                } else {
                    break // exit for
                }
            }
        }

        if (recover != null) {
            return recover()
        }

        policy.onFailure(lastException)
        throw lastException
    }

}

open class ForeverRetry(policy: ForeverRetryPolicy): FixedRetry(policy)

class TimeBasedRetry(policy: TimeBasedPolicy) {

    private val foreverRetry = ForeverRetry(policy.toForeverRetryPolicy())

    private val deadLine = System.currentTimeMillis() + policy.maxTime.toMillis()

    operator fun <R> invoke(recover: (() -> R)? = null, action: () -> R): R {
        return foreverRetry.invoke(recover, action)
    }

    private fun onRetry(exc: Throwable, context: ExecutionContext) {
        if (deadLine < System.currentTimeMillis()) {
            context.cancel()
        }
    }

}