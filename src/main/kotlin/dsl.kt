
object ProductDoc {
    val name = "Test"
}

fun main() {
    println("Let's design some nice dsl")

    ProductDoc.name
        .also(::println)
}
