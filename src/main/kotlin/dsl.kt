import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

interface Type<T> {
    fun deserialize(v: Any): T
}

abstract class NumberType<T: Number> : Type<T>

object IntType : NumberType<Int>() {
    override fun deserialize(v: Any) = when(v) {
        is Int -> v
        is String -> v.toInt()
        else -> throw IllegalArgumentException()
    }
}

object FloatType : NumberType<Float>() {
    override fun deserialize(v: Any) = when(v) {
        is Int -> v.toFloat()
        is Float -> v
        is String -> v.toFloat()
        else -> throw IllegalArgumentException()
    }
}

abstract class StringType : Type<String> {
    override fun deserialize(v: Any): String {
        return v.toString()
    }
}

object KeywordType : StringType()
object TextType : StringType()

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

    fun <T> field(name: String? = null, type: Type<T>) = Field(name, type)
    fun int(name: String? = null) = field(name, IntType)
    fun float(name: String? = null) = field(name, FloatType)
    fun keyword(name: String? = null) = field(name, KeywordType)
    fun text(name: String? = null) = field(name, TextType)

    fun calcQualifiedName(fieldName: String) = if (_qualifiedName.isNotEmpty()) {
        "$_qualifiedName.$fieldName"
    } else {
        fieldName
    }
}

abstract class SubDocument : FieldSet() {
    fun <V: SubDocument> obj(name: String? = null, factory: () -> V) = SubDocumentProperty(name, factory)

    class SubFieldsProperty<T, V: SubFields<T>>(private val name: String?, private val type: Type<T>, private val factory: () -> V) {
        operator fun provideDelegate(thisRef: SubDocument, prop: KProperty<*>): ReadOnlyProperty<SubDocument, V> {
            println("> SubFieldsProperty.provideDelegate($thisRef, ${prop.name})")
            val subFields by lazy {
                factory().apply {
                    _type = type
                    _name = name ?: prop.name
                    _qualifiedName = thisRef.calcQualifiedName(_name)
                }
            }
            return object : ReadOnlyProperty<SubDocument, V> {
                override fun getValue(thisRef: SubDocument, property: KProperty<*>) = subFields
            }
        }
    }

    class SubDocumentProperty<V: SubDocument>(private val name: String?, private val factory: () -> V) {
        operator fun provideDelegate(thisRef: SubDocument, prop: KProperty<*>): ReadOnlyProperty<SubDocument, V> {
//            println("> SubDocumentProperty.provideDelegate($thisRef, ${prop.name})")
            val subDocument by lazy {
                factory().apply {
                    _name = name ?: prop.name
                    _qualifiedName = thisRef.calcQualifiedName(_name)
                }
            }
            return object : ReadOnlyProperty<SubDocument, V> {
                override fun getValue(thisRef: SubDocument, property: KProperty<*>) = subDocument
            }
        }
    }
}

abstract class Document : SubDocument() {
    val meta = MetaFields()
}

class Field<T>(val name: String? = null, val type: Type<T>) {
    override fun toString(): String {
        return "Field(name = $name)"
    }

    fun <V: SubFields<T>> subFields(factory: () -> V) = SubDocument.SubFieldsProperty(name, type, factory)

    operator fun provideDelegate(thisRef: FieldSet, prop: KProperty<*>): ReadOnlyProperty<FieldSet, BoundField<T>> {
//        println("> Field.provideDelegate($thisRef, ${prop.name})")
        val boundField by lazy {
            val fieldName = name ?: prop.name
            val qualifiedName = thisRef.calcQualifiedName(fieldName)
            BoundField(fieldName, qualifiedName, type)
        }
        return object : ReadOnlyProperty<FieldSet, BoundField<T>> {
             override fun getValue(thisRef: FieldSet, property: KProperty<*>) = boundField
        }
    }
}

class BoundField<T>(val name: String, val qualifiedName: String, type: Type<T>) {
    override fun toString(): String {
        return "BoundField(name = $name, qualifiedName = $qualifiedName)"
    }

    operator fun getValue(thisRef: Source, prop: KProperty<*>): T? {
        return thisRef._source[name] as? T
    }
}

abstract class SubFields<T> : FieldSet() {
    lateinit var _type: Type<T>

    operator fun getValue(thisRef: Source, prop: KProperty<*>): T? {
        println(_name)
        return thisRef._source[_name] as? T
    }
}

class MetaFields : FieldSet() {
    val id by keyword("_id")
    val type by keyword("_type")
    val uid by keyword("_uid")

    val routing by keyword("_routing")
    val parent by keyword("_parent")
}

abstract class Source {
    lateinit var _source: Map<String, Any?>

    fun <V: Source> SubDocument.source(factory: () -> V) = SubSourceProperty(this, factory)

    class SubSourceProperty<V: Source>(private val document: SubDocument, private val factory: () -> V) {
        operator fun provideDelegate(thisRef: Source, prop: KProperty<*>): ReadOnlyProperty<Source, V?> {
            val subSource by lazy {
                thisRef._source
                    .let {
                        it[document._name] as? Map<String, *>
                    }
                    ?.let {
                        factory().apply {
                            _source = it
                        }
                    }
            }
            return object : ReadOnlyProperty<Source, V?> {
                override fun getValue(thisRef: Source, property: KProperty<*>) = subSource
            }
        }
    }
}

// === Real documents ===

object ProductDoc : Document() {
    class NameFields<T> : SubFields<T>() {
        val sort by keyword()
    }

    class CompanyDoc : SubDocument() {
        class OpinionDoc : SubDocument() {
            val count by int()
            val positiveCount by int("positive_count")
        }

        val name by text("n").subFields { NameFields() }
        val userOpinion by obj("user_opinion") { OpinionDoc() }
    }

    val name by text().subFields { NameFields() }
    val status by int()
    val rank by float()
    val company by obj("c") { CompanyDoc() }
}

class ProductSource : Source() {
    class CompanySource : Source() {
        val name by ProductDoc.company.name
    }

    val name: String? by ProductDoc.name
    val status: Int? by ProductDoc.status
    val rank: Float? by ProductDoc.rank
    val company: CompanySource? by ProductDoc.company.source { CompanySource() }
}

fun main() {
    println("Let's design some nice dsl")

    ProductDoc.name
        .also(::println)
    ProductDoc.name.sort
        .also(::println)
    ProductDoc.meta.id
        .also(::println)
    ProductDoc.company.name
        .also(::println)
    ProductDoc.company.name.sort
        .also(::println)
    ProductDoc.company.userOpinion.positiveCount
        .also(::println)

    println()
    val source = mapOf(
        "name" to "Test name",
        "status" to 1,
        "c" to mapOf(
            "n" to "Test company"
        )
    )
    val product = ProductSource().apply { _source = source }
    product.name
        .also { println("name: $it") }
    product.status
        .also { println("status: $it") }
    product.company?.name
        .also { println("company.name: $it") }
}
