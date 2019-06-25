**Retry** is a *kotlin* resilience and fault-handling library.

### Simple usage
```Kotlin
// Retry maximum 3 times if any exception has occured
val withRetry = Policy.handle<Throwable>()
                      .retry(3)
    
// The result is strongly typed, `world` is type of String
val world = withRetry {
    print("Hello ")
    "World!" // return of this lambda
}
println(world)
    
// pow is type of Double
val pow = withRetry {
    Math.pow(2.0, 3.0)
}
println("2 ^ 3 = $pow")
    
// OUTPUT:
// Hello World!
// 2 ^ 3 = 8.0
```
### Retry for only specific exceptions with specific conditions
```Kotlin
Policy.handle<IOException>() // all IOExceptions
      .or<SQLException> { exc -> exc.errorCode == 1001 } // But retry only for SqlException with code 1001
      .retry(3)
```
In the above code If any exception other than `IOException` and `SQLException` thrown, retry will not be occured. If after 3 times it cannot succeeded, it will throw the last exception that has been thrown.

### Control of sleep
```Kotlin
// without sleep, the next retry will be fired immediately
Policy.handle<Throwable>()
      .retry(3)

// Fixed sleep between each retry
Policy.handle<Throwable>()
      .sleep(1000)  
      .retry(3)
      
// first retry, waits 1 second
// second retry waits 5 seconds
// third retry waits 20 seconds
Policy.handle<Throwable>()
      .sleep(1000, 5000, 20000)  
      .retry(3)      
      
// If retry number is greater than sleep values
// then last sleep value will be used for further retries
// first retry, waits 1 second
// second, third and forth retry waits 5 seconds
Policy.handle<Throwable>()
      .sleep(1000, 5000)  
      .retry(4)           

// We can set custom sleep function
// For example a sleep function that doubled sleep time on in each retry
Policy.handle<Throwable>()
      .sleepFunc { retryNumber, prevSleepTime ->
        if (retryNumber == 1) 1000 else prevSleepTime * 2
      }  
      .retry(3)           
```
### Callbacks
#### onRetry
Called on each retry.
```Kotlin
Policy.handle<Throwable>()
      .onRetry { exc, context ->
          println(exc.message)
          println("Retry number: ${context.attempt()}")
      }
      .retry(3)

// ability to cancel retry by calling ExecutionContext.cancel()
Policy.handle<Throwable>()
      .onRetry { exc, context ->
          if (someConditionMet)
            context.cancel()
      }
      .retry(3)
```
#### onFailure
Call only when all retry has been done and the result is unsuccessful.
```Kotlin
Policy.handle<Throwable>()
      .onFailure { exc ->
         println("This is the last exception: ${exc.message}")
       }
      .retry(3)
```
#### recover
Provide the default value, if operation is not successful.  
**Note**: if recover is provided, *onFailure* callback will not fire.
```Kotlin
val retry = Policy.handle<Throwable>()
                  .onFailure { exc ->
                     // THIS WONT EXECUTED
                   }
                  .retry(3)

val message = retry(recover = { "default message" }) {
    api.getMessageFromRemoteService()
}
println(message)
```

### Forever retry
```Kotlin
// Fixed sleep between each retry
Policy.handle<Throwable>()
      .sleep(1000)  
      .forever()
```

### Timebased retry
Retry *at least* for the specific amount of time.  
**Note**: it will not stop the function, if the timeout has occured. So if the given function blocks forever, retry won't happend. It is responsibility of the function, to throw the proper exception.
```Kotlin
Policy.handle<Throwable>()
      .time(Duration.ofSeconds(30))
```
### Circuit breaker
#### Fixed threshold
```Kotlin
// If the number of failure reached 100, then circuit state will be changed to `open` for 5 seconds
// meaning only the `recovery` function will be called during this time,
// After 5 seconds, again the state will change to `closed`
// There is no `half-open` state
val circuit = Policy.handle<Exception>()
                    .thresholdCircuitBreaker(100, Duration.ofSeconds(5))

var result = circuit(recover = {"Default message"}) {
            getMessageFromRemoteService()
}
```
#### Consecutive threshold
```Kotlin
// Only `open` the circuit if consecutive error happened, in this case
// 25 consecutive errors must be happend until circuit's state changed to `open`
// It will remain in `open` state for 10 seconds
val circuit = Policy.handle<Exception>()
                    .consecutiveThresholdCircuitBreaker(25, Duration.ofSeconds(10))

var result = circuit(recover = {"Default message"}) {
            getMessageFromRemoteService()
}
```
#### Timebucket threshold
```Kotlin
// Measure errors in time buckets, and if bucket reached the threshold it `open` the circuit

// If in 5 seconds, the errors reached 40, then circuit will be in `open` state for about 1 seconds
val circuit = Policy.handle<Exception>()
                    .timeBucketCircuitBreaker(Duration.ofSeconds(5), 40, Duration.ofSeconds(1))

var result = circuit(recover = {"Default message"}) {
            getMessageFromRemoteService()
}
```

#### Add to your projects
[![](https://jitpack.io/v/mostafa-asg/retry.svg)](https://jitpack.io/#mostafa-asg/retry)
