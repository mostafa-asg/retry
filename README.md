# retry
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
    val withRetry = Policy.handle<IOException>() // all IOExceptions
                          .or<SQLException> { exc -> exc.errorCode == 1001 } // But retry only for SqlException with code 1001
                          .retry(3)
```
In the above code If any exception other than `IOException` and `SQLException` thrown, retry will not be occured. If after 3 times it cannot succeeded, it will throw the last exception that has been thrown.
