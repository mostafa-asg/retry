package com.github.retry

typealias RetryHandler = (Int, Throwable, ExecutionContext) -> Unit
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
    override var onRetry: RetryHandler = { _, _, _ -> }
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

    fun retry(maxRetry: Int): FixedRetry {
        val policy =  FixedRetryPolicy(
                exceptions,
                onRetry,
                onFailure,
                maxRetry
        )

        return FixedRetry(policy)
    }

    fun forever(): ForeverRetry {
        val policy =  ForeverRetryPolicy(
                exceptions,
                onRetry,
                onFailure
        )

        return ForeverRetry(policy)
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
}

open class FixedRetryPolicy(
        override val exceptions: List<RetryableException<*>>,
        override val onRetry: RetryHandler,
        override val onFailure: FailureHandler,
        val maxRetry: Int): Policy()

class ForeverRetryPolicy(
        override val exceptions: List<RetryableException<*>>,
        override val onRetry: RetryHandler,
        override val onFailure: FailureHandler): FixedRetryPolicy(exceptions, onRetry, onFailure, Int.MAX_VALUE)

open class FixedRetry(val policy: FixedRetryPolicy) {

    private var giveUp = false

    private val executionContext = object: ExecutionContext {
        override fun cancel() {
            giveUp = true
        }
    }

    operator fun <R> invoke(recover: (() -> R)? = null, action: () -> R): R {
        var lastException: Throwable = Exception()

        for (attempt in 1..policy.maxRetry) {
            try {
                return action()
            }
            catch (exc: Throwable) {
                lastException = exc

                if (policy.match(exc)) {
                    policy.onRetry(attempt, exc, executionContext)
                    if (giveUp) {
                        throw exc
                    }
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

class ForeverRetry(policy: ForeverRetryPolicy): FixedRetry(policy)
