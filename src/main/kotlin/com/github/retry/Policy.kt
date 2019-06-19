package com.github.retry

import java.time.Duration

typealias RetryHandler = (Throwable, ExecutionContext) -> Unit
typealias FailureHandler = (Throwable) -> Unit

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

    fun match(throwable: Throwable): Boolean {
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

class PolicyBuilder: Policy() {
    override val exceptions = mutableListOf<RetryableException<*>>()
    override var onRetry: RetryHandler = { _, _ -> }
    override var onFailure: FailureHandler = { _ -> }

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

    fun retry(maxRetry: Int, sleepMillis: List<Long>): FixedRetry {
        val policy =  FixedRetryPolicy(
                exceptions,
                onRetry,
                onFailure,
                maxRetry,
                sleepMillis
        )

        return FixedRetry(policy)
    }

    fun retry(maxRetry: Int, sleepMillis: Long = 1000): FixedRetry {
        return retry(maxRetry, listOf(sleepMillis))
    }

    fun forever(sleepMillis: Long = 1000): ForeverRetry {
        val policy =  ForeverRetryPolicy(
                exceptions,
                onRetry,
                onFailure,
                sleepMillis
        )

        return ForeverRetry(policy)
    }

    fun time(duration: Duration, sleepMillis: Long = 1000): TimeBasedRetry {
        val policy =  TimeBasedPolicy(
                exceptions,
                onRetry,
                onFailure,
                duration,
                sleepMillis
        )

        return TimeBasedRetry(policy)
    }

    fun onRetry(retryFunc: RetryHandler): PolicyBuilder {
        this.onRetry = retryFunc
        return this
    }

    fun onFailure(failerFunc: FailureHandler): PolicyBuilder {
        this.onFailure = failerFunc
        return this
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
        val maxRetry: Int,
        val sleepMillis: List<Long>): Policy()

class ForeverRetryPolicy(
        override val exceptions: List<RetryableException<*>>,
        override val onRetry: RetryHandler,
        override val onFailure: FailureHandler,
        sleepMillis: Long = 1000): FixedRetryPolicy(exceptions, onRetry, onFailure, Int.MAX_VALUE, listOf(sleepMillis))

open class TimeBasedPolicy(
        override val exceptions: List<RetryableException<*>>,
        override val onRetry: RetryHandler,
        override val onFailure: FailureHandler,
        val maxTime: Duration,
        val sleepMillis: Long): Policy()

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
class TimeBasedRetry(val policy: TimeBasedPolicy) {

    private val foreverRetry =  Policy.handle<Exception>()
                                      .onRetry(this::onRetry)
                                      .forever(policy.sleepMillis)

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