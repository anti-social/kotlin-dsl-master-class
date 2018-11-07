import java.lang.IllegalStateException
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

abstract class FieldSet {
    var _name = ""
        set(value) {
            if (_name.isNotEmpty()) throw IllegalStateException()
            field = value
        }
    var _qualifiedName = ""
        set(value) {
            if (_qualifiedName.isNotEmpty()) throw IllegalStateException()
            field = value
        }

    fun field(name: String? = null) = Field(name)

    fun calcQualifiedName(fieldName: String) = if (_qualifiedName.isNotEmpty()) {
        "$_qualifiedName.$fieldName"
    } else {
        fieldName
    }
}

abstract class Document : FieldSet() {
    class SubFieldsProperty<V: SubFields>(private val factory: () -> V) {
        operator fun provideDelegate(thisRef: Document, prop: KProperty<*>): ReadOnlyProperty<Document, V> {
            println("> SubFieldsProperty.provideDelegate($thisRef, ${prop.name})")
            val subFields = factory().apply {
                _name = prop.name
                _qualifiedName = thisRef.calcQualifiedName(_name)
            }
            return object : ReadOnlyProperty<Document, V> {
                override fun getValue(thisRef: Document, property: KProperty<*>) = subFields
            }
        }
    }
}

class Field(val name: String? = null) {
    override fun toString(): String {
        return "Field(name = $name)"
    }

    fun <V: SubFields> subFields(factory: () -> V) = Document.SubFieldsProperty(factory)

    operator fun provideDelegate(thisRef: FieldSet, prop: KProperty<*>): ReadOnlyProperty<FieldSet, BoundField> {
        println("> Field.provideDelegate($thisRef, ${prop.name})")
        val fieldName = name ?: prop.name
        val qualifiedName = thisRef.calcQualifiedName(fieldName)
        val boundField = BoundField(fieldName, qualifiedName)
        return object : ReadOnlyProperty<FieldSet, BoundField> {
             override fun getValue(thisRef: FieldSet, property: KProperty<*>) = boundField
        }
    }
}

class BoundField(val name: String, val qualifiedName: String) {
    override fun toString(): String {
        return "BoundField(name = $name, qualifiedName = $qualifiedName)"
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
    ProductDoc.name.sort
        .also(::println)
}
