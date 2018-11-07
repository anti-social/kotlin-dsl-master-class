import kotlin.reflect.KProperty

abstract class FieldSet {
    fun field(name: String? = null) = Field(name)
}

abstract class Document : FieldSet() {
    class SubFieldsProperty<V: SubFields>(private val factory: () -> V) {
        operator fun getValue(thisRef: Document, prop: KProperty<*>): V {
            return factory()
        }
    }
}

class Field(val name: String? = null) {
    override fun toString(): String {
        return "Field(name = $name)"
    }

    fun <V: SubFields> subFields(factory: () -> V) = Document.SubFieldsProperty(factory)

    operator fun getValue(thisRef: FieldSet, prop: KProperty<*>): BoundField {
        return BoundField(name ?: prop.name)
    }
}

class BoundField(val name: String) {
    override fun toString(): String {
        return "BoundField(name = $name)"
    }
}

abstract class SubFields : FieldSet()

// === Real documents ===

object ProductDoc : Document() {
    class NameFields : SubFields() {
        val sort by field()
    }

    val name by field().subFields { NameFields() }
}

fun main() {
    println("Let's design some nice dsl")

    ProductDoc.name
        .also(::println)
    ProductDoc.name.sort
        .also(::println)
}
