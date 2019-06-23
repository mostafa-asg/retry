**Retry** is a *kotlin* resilience and fault-handling library.

### Simple usage
```Kotlin
// Retry maximum 3 times if any exception has occured
val withRetry = Policy.handle<Throwable>()
                      .retry(3)
    
// It is strongly typed, world is type of String
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

#### Add to your projects
[![](https://jitpack.io/v/mostafa-asg/retry.svg)](https://jitpack.io/#mostafa-asg/retry)
