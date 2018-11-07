
object ProductDoc {
    val name = Field("name")
}

class Field(val name: String) {
    override fun toString(): String {
        return "Field(name = $name)"
    }
}

fun main() {
    println("Let's design some nice dsl")

    ProductDoc.name
        .also(::println)
}
