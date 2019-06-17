import com.github.retry.Policy

class HttpException(val status: Int): Exception()
class NotConnect(reason: String): Exception(reason)

fun main() {

    val retry = Policy.handle<HttpException> { it.status == 500 }
                       .or<NotConnect>()
                       .onRetry { retryNumber, exception, executionContect ->
                           println("onRetry Handler: $retryNumber for $exception")
                       }
                       .onFailure { exc ->
                          println("Nashod $exc")
                       }
                       .forever()


//    retry {
//        println("Hello World")
//        throw NotConnect("Nemishe")
//    }

    val value = retry(recover = {10}) {
        getIntValue()
    }

    println(value)

}

fun getIntValue(): Int {
    println("Hello World")
    throw NotConnect("Nemishe")
}