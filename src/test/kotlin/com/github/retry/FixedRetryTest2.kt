package com.github.retry

import org.junit.Test
import org.junit.Assert.*

class FixedRetryTest2 {

    var counter = 0

    fun doSomeWork() {
        ++counter

        if (counter % 2 == 0) {
            throw IllegalArgumentException()
        } else {
            throw IllegalStateException()
        }
    }

    @Test
    fun catch_more_than_one_exception_test() {
        val retry = Policy
                        .handle<IllegalArgumentException>()
                        .or<IllegalStateException>()
                        .onRetry { exc, context ->
                            if (context.attempt() % 2 == 0) {
                                assertTrue(exc is IllegalArgumentException)
                            } else {
                                assertTrue(exc is IllegalStateException)
                            }
                        }
                        .retry(6)

        val result = runCatching {
            retry {
                doSomeWork()
            }
        }

        assertNotNull( result.exceptionOrNull() )
        assertEquals(counter, 6)
    }

}