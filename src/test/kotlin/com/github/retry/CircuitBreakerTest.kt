package com.github.retry

import org.junit.Test
import org.junit.Assert.*
import java.time.Duration

class CircuitBreakerTest {

    @Test
    fun simpleThresholdTest() {
        var workflow = ""

        val circuit = Policy.handle<Exception>()
                            .onRetry { _, _ -> workflow += "R" }
                            .thresholdCircuitBreaker(3, Duration.ofSeconds(3))

        var result = circuit(recover = {"Default message"}) {
            "Message from remote service"
        }

        assertEquals("", workflow)
        assertEquals("Message from remote service", result)

        result = circuit(recover = {"Default message"}) {
            throw Exception("This is the faulty service")
        }
        assertEquals("RRR", workflow)
        assertEquals("Default message", result)

        result = circuit(recover = {"Default message"}) {
            "Must not be returned - circuit is open"
        }
        assertEquals("Default message", result)

        Thread.sleep(3050)
        // Now circuit is close
        result = circuit(recover = {"Default message"}) {
            "Must be returned"
        }
        assertEquals("Must be returned", result)
    }

    @Test
    fun consecutiveThreshold_Test1() {
        var workflow = ""
        var counter = 0

        val circuit = Policy.handle<Exception>()
                .onRetry { _, _ -> workflow += "R" }
                .consecutiveThresholdCircuitBreaker(2, Duration.ofSeconds(3))

        //----------------------------
        var result = circuit(recover = {"Default message"}) {
            counter++
            if (counter % 2 == 0) {
                "Message from remote service"
            } else {
                throw Exception()
            }
        }
        assertEquals("R", workflow)
        assertEquals("Message from remote service", result)
        //----------------------------
        result = circuit(recover = {"Default message"}) {
            counter++
            if (counter % 2 == 0) {
                "Message from remote service"
            } else {
                throw Exception()
            }
        }
        assertEquals("RR", workflow)
        assertEquals("Message from remote service", result)
        //----------------------------
        result = circuit(recover = {"Default message"}) {
            counter++
            if (counter % 2 == 0) {
                "Message from remote service"
            } else {
                throw Exception()
            }
        }
        assertEquals("RRR", workflow)
        assertEquals("Message from remote service", result)
    }

    @Test
    fun consecutiveThreshold_Test2() {
        var workflow = ""

        val circuit = Policy.handle<Exception>()
                .onRetry { _, _ -> workflow += "R" }
                .consecutiveThresholdCircuitBreaker(2, Duration.ofSeconds(3))

        var result = circuit(recover = {"Default message"}) {
            throw Exception()
        }

        assertEquals("RR", workflow)
        assertEquals("Default message", result)

        Thread.sleep(3050)
        // Now circuit is close
        result = circuit(recover = {"Default message"}) {
            "Must be returned"
        }
        assertEquals("Must be returned", result)
    }

    @Test
    fun timebucketThresholdTest() {
        val circuit = Policy.handle<Exception>()
                            .timeBucketCircuitBreaker(Duration.ofSeconds(5), 10, Duration.ofSeconds(1))

        var result = circuit(recover = {"Default message"}) {
            throw Exception()
        }
        assertEquals("Default message", result)

        Thread.sleep(1050)
        // Now circuit is close
        result = circuit(recover = {"Default message"}) {
            "Must be returned"
        }
        assertEquals("Must be returned", result)
    }

}