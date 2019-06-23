package com.github.retry

import org.junit.Test
import org.junit.Assert.*

open class ExceptionA: Exception()
class ExceptionB(val code: Int): ExceptionA()

class FixedRetryTest {

    @Test
    fun testWithoutException() {
        val retryAtLeast3Times = Policy.handle<Throwable>()
                                .retry(3)

        val result = retryAtLeast3Times {
            notThrowException()
        }

        assertEquals(120, result)
    }

    @Test
    fun testThrowExceptionOfSubclasses() {
        var workflow = ""

        val retryAtLeast3Times = Policy.handle<ExceptionA>()
                                       .onRetry { exc, context ->
                                           assertTrue( exc is ExceptionB )
                                           workflow += "OnRetry ${context.attempt()}\n"
                                       }
                                       .onFailure { exc ->
                                           assertTrue( exc is ExceptionB )
                                           workflow += "OnFailure"
                                       }
                                       .retry(3)

        val result = runCatching {
            retryAtLeast3Times {
                throwExceptionB()
            }
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ExceptionB)

        val expectedWorkflow = "OnRetry 1\nOnRetry 2\nOnRetry 3\nOnFailure"
        assertEquals(expectedWorkflow, workflow)
    }

    @Test
    fun should_not_retry_on_unrelated_exceptions_test() {
        var workflow = ""

        val retryAtLeast3Times = Policy.handle<ExceptionB>()
                .onRetry { exc, context ->
                    fail("OnRetry should not be called")
                }
                .onFailure { exc ->
                    assertTrue( exc is ExceptionA )
                    workflow += "OnFailure"
                }
                .retry(3)

        val result = runCatching {
            retryAtLeast3Times {
                throwExceptionA()
            }
        }

        assertTrue(result.isFailure)
        //assertTrue(result.exceptionOrNull() is ExceptionB)

        val expectedWorkflow = "OnFailure"
        assertEquals(expectedWorkflow, workflow)
    }

    @Test
    fun exceptions_with_conditions_test() {
        var workflow = ""
        val retryAtLeast3Times = Policy.handle<ExceptionB> { exc -> exc.code == 1001 }
                                       .onRetry { _, _ -> workflow += "R" }
                                       .retry(3)

        val result1 = runCatching {
            retryAtLeast3Times {
                throw ExceptionB(code = 1001)
            }
        }
        assertTrue(result1.isFailure)
        assertEquals("RRR", workflow)

        workflow = ""

        val result2 = runCatching {
            retryAtLeast3Times {
                throw ExceptionB(code = 666)
            }
        }
        assertTrue(result2.isFailure)
        assertEquals(workflow, "") // OnRetry should not be called
    }

    @Test
    fun testSleepsWorkCorrectly1() {
        val retryNum = 3
        val sleep = 200L
        val inaccuracy = 100

        val retry = Policy.handle<Throwable>()
                          .sleep(sleep)
                          .retry(retryNum)

        val start = System.currentTimeMillis()

        runCatching {
            retry {
                throw Exception()
            }
        }

        val now = System.currentTimeMillis()
        assertTrue(now - start >= (retryNum * sleep - inaccuracy))
    }

    @Test
    fun testSleepsWorkCorrectly2() {
        val retryNum = 3
        val sleep = listOf<Long>(100, 200, 400)
        val inaccuracy = 100

        val retry = Policy.handle<Throwable>()
                .sleep(
                        sleep
                )
                .retry(retryNum)

        val start = System.currentTimeMillis()

        runCatching {
            retry {
                throw Exception()
            }
        }

        val now = System.currentTimeMillis()
        assertTrue(now - start >= (sleep.sum() - inaccuracy))
    }

    @Test
    fun testSleepsWorkCorrectly3() {
        val retryNum = 3
        val inaccuracy = 100

        val retry = Policy.handle<Throwable>()
                .sleepFunc { attmpt, prevSleep ->
                    if (attmpt == 1) {
                        500
                    } else {
                        prevSleep * 2
                    }
                }
                .retry(retryNum)

        val start = System.currentTimeMillis()

        runCatching {
            retry {
                throw Exception()
            }
        }

        val now = System.currentTimeMillis()
        assertTrue(now - start >= (500 + 1000 + 2000 - inaccuracy))
    }

    private fun notThrowException(): Int = 120

    private fun throwExceptionA(): Int {
        throw ExceptionA()
    }

    private fun throwExceptionB(): Int {
        throw ExceptionB(200)
    }

}