import kotlin.reflect.KProperty

abstract class Document {
    fun field(name: String? = null) = Field(name)
}

class Field(val name: String? = null) {
    override fun toString(): String {
        return "Field(name = $name)"
    }

    operator fun getValue(thisRef: Document, prop: KProperty<*>): BoundField {
        return BoundField(name ?: prop.name)
    }
}

class BoundField(val name: String) {
    override fun toString(): String {
        return "BoundField(name = $name)"
    }
}

// === Real documents ===

object ProductDoc : Document() {
    val name by field()
}

fun main() {
    println("Let's design some nice dsl")

    ProductDoc.name
        .also(::println)
}
