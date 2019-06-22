package com.github.retry

import org.junit.Assert.*
import org.junit.Test
import java.time.Duration

class TimebasedRetry {

    @Test
    fun test() {
        val inaccuracy = 100
        val duration = 2000L
        var workflow = ""
        var counter = 0

        val retry = Policy.handle<Throwable>()
                          .onRetry { exc, context ->
                            workflow += "OnRetry ${context.attempt()}\n"
                            counter++
                          }
                          .onFailure {
                              workflow += "OnFailure"
                          }
                          .sleep(
                                50
                           )
                          .time(Duration.ofMillis(duration))

        val start = System.currentTimeMillis()

        runCatching {
            retry {
                throw Exception()
            }
        }

        val now = System.currentTimeMillis()
        assertTrue(now - start >= (duration - inaccuracy))

        var expectedWorkflow = ""
        for (i in 1..counter) {
            expectedWorkflow += "OnRetry $i\n"
        }
        expectedWorkflow += "OnFailure"
        assertEquals(workflow, expectedWorkflow)
    }

}