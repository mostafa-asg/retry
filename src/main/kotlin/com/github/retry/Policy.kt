package com.github.retry

import java.time.Duration

typealias RetryHandler = (Throwable, ExecutionContext) -> Unit
typealias FailureHandler = (Throwable) -> Unit

/**
 * The first argument is retry number starting from 1
 * The second argument is previous sleep, which always 0 if first argument is 1
 */
typealias SleepFunc = (Int, Long) -> Long

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
    abstract val sleepFunc: SleepFunc

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

    companion object {
        val DefaultSleep = 0L
    }

    override val exceptions = mutableListOf<RetryableException<*>>()
    override var onRetry: RetryHandler = { _, _ -> }
    override var onFailure: FailureHandler = { _ -> }
    override val sleepMillis = mutableListOf<Long>()
    override var sleepFunc: SleepFunc = { attempt, prevSleep ->
        if ( attempt-1 < sleepMillis.size) {
            sleepMillis[attempt-1]
        } else {
            if (sleepMillis.isEmpty()) DefaultSleep else sleepMillis.last()
        }
    }

    inline fun <reified T: Throwable> or(): PolicyBuilder {
        return or(T::class.java) {true}
    }

    inline fun <reified T: Throwable> or(noinline condition: (T) -> Boolean): PolicyBuilder {
        return or(T::class.java, condition)
    }

    fun <T: Throwable> or(clazz: Class<T>, condition: (T) -> Boolean): PolicyBuilder {
        exceptions.add(RetryableException(clazz, condition))
        return this
    }

    fun sleep(millis: Long): PolicyBuilder {
        sleepMillis.clear()
        sleepMillis.add(millis)
        return this
    }

    fun sleep(millis: List<Long>): PolicyBuilder {
        sleepMillis.clear()
        sleepMillis.addAll(millis)
        return this
    }

    fun sleep(vararg millis: Long): PolicyBuilder {
        return sleep(millis.toList())
    }

    fun onRetry(retryFunc: RetryHandler): PolicyBuilder {
        this.onRetry = retryFunc
        return this
    }

    fun sleepFunc(sleepFunc: SleepFunc): PolicyBuilder {
        this.sleepFunc = sleepFunc
        return this
    }

    fun onFailure(failerFunc: FailureHandler): PolicyBuilder {
        this.onFailure = failerFunc
        return this
    }

    fun retry(max: Int): FixedRetry {
        val policy =  FixedRetryPolicy(
                exceptions,
                onRetry,
                onFailure,
                sleepMillis,
                sleepFunc,
                max
        )

        return FixedRetry(policy)
    }

    fun forever(): ForeverRetry {
        val policy =  ForeverRetryPolicy(
                exceptions,
                onRetry,
                onFailure,
                sleepMillis,
                sleepFunc
        )

        return ForeverRetry(policy)
    }

    fun time(duration: Duration): TimeBasedRetry {
        val policy =  TimeBasedPolicy(
                exceptions,
                onRetry,
                onFailure,
                sleepMillis,
                sleepFunc,
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
        override val sleepFunc: SleepFunc,
        val maxRetry: Int): Policy()

class ForeverRetryPolicy(
        override val exceptions: List<RetryableException<*>>,
        override val onRetry: RetryHandler,
        override val onFailure: FailureHandler,
        override val sleepMillis: List<Long>,
        override val sleepFunc: SleepFunc): FixedRetryPolicy(exceptions, onRetry, onFailure, sleepMillis, sleepFunc, Int.MAX_VALUE)

open class TimeBasedPolicy(
        override val exceptions: List<RetryableException<*>>,
        override val onRetry: RetryHandler,
        override val onFailure: FailureHandler,
        override val sleepMillis: List<Long>,
        override val sleepFunc: SleepFunc,
        val maxTime: Duration): Policy()

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

    operator fun <R> invoke(recover: (() -> R)? = null, action: () -> R): R {
        var lastException: Throwable = Exception()
        var prevSleep = 0L

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

                    val sleep = policy.sleepFunc(executionContext.attempt(), prevSleep)
                    if (sleep > 0) {
                        Thread.sleep(sleep)
                    }
                    prevSleep = sleep
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

    private val foreverRetry = ForeverRetry(
            ForeverRetryPolicy(
                    policy.exceptions,
                    this::onRetry,
                    policy.onFailure,
                    policy.sleepMillis,
                    policy.sleepFunc
            )
    )

    private var deadLine = 0L
    private var deadLineReached = false

    operator fun <R> invoke(recover: (() -> R)? = null, action: () -> R): R {
        deadLine = System.currentTimeMillis() + policy.maxTime.toMillis()

        val result = runCatching {
            foreverRetry.invoke(recover, action)
        }

        if (deadLineReached) {
            policy.onFailure(result.exceptionOrNull()!!)
        }

        return result.getOrThrow()
    }

    private fun onRetry(exc: Throwable, context: ExecutionContext) {
        policy.onRetry(exc, context)

        if (deadLine < System.currentTimeMillis()) {
            deadLineReached = true
            context.cancel()
        }
    }

}