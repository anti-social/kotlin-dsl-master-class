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

interface FieldOperations {
    val _name: String
    val _qualifiedName: String

    fun eq(other: Any?) = Term(this, other)
    fun match(other: Any?) = Match(this, other)
}

interface Expr {
    val name: String
}

interface QueryExpr : Expr

class Term(val field: FieldOperations, val other: Any?) : QueryExpr {
    override val name = "term"

    override fun toString(): String {
        return "Term(field = $field, other = $other)"
    }
}

class Match(val field: FieldOperations, val other: Any?) : QueryExpr {
    override val name = "match"

    override fun toString(): String {
        return "Match(field = $field, other = $other)"
    }
}

class MultiMatch(
    val query: String,
    val fields: List<FieldOperations>,
    val type: Type? = null,
    val boost: Double? = null
) : QueryExpr {
    enum class Type {
        BEST_FIELDS, MOST_FIELDS, CROSS_FIELDS, PHRASE, PHRASE_PREFIX;
    }

    override val name = "multi_match"

    override fun toString(): String {
        return "MultiMatch(query = $query, fields = $fields, type = $type, boost = $boost)"
    }
}

class Bool(
    val filter: List<QueryExpr>? = null,
    val should: List<QueryExpr>? = null,
    val must: List<QueryExpr>? = null,
    val mustNot: List<QueryExpr>? = null
) : QueryExpr {
    override val name = "bool"

    companion object {
        fun filter(vararg exprs: QueryExpr) = Bool(filter = exprs.toList())
        fun should(vararg exprs: QueryExpr) = Bool(should = exprs.toList())
        fun must(vararg exprs: QueryExpr) = Bool(must = exprs.toList())
        fun mustNot(vararg exprs: QueryExpr) = Bool(mustNot = exprs.toList())
    }

    override fun toString(): String {
        return "Bool(filter = $filter, should = $should, must = $must, must_not = $mustNot)"
    }
}

class FunctionScore(
    val query: QueryExpr?,
    val boost: Double? = null,
    val scoreMode: FunctionScore.ScoreMode? = null,
    val boostMode: FunctionScore.BoostMode? = null,
    val functions: List<Func>
) : QueryExpr {
    enum class ScoreMode {
        MULTIPLY, SUM, AVG, FIRST, MAX, MIN
    }
    enum class BoostMode {
        MULTIPLY, REPLACE, SUM, AVG, MAX, MIN
    }

    override val name = "function_score"

    override fun toString(): String {
        return """FunctionScore(
            |query = $query,
            |boost = $boost,
            |scoreMode = $scoreMode,
            |boostMode = $boostMode,
            |functions = $functions)""".trimMargin()
    }
}

abstract class Func(val filter: QueryExpr?) : Expr

class Weight(val weight: Double, filter: QueryExpr?) : Func(filter) {
    override val name = "weight"

    override fun toString(): String {
        return "Weight(weight = $weight, filter = $filter)"
    }
}

class FieldValueFactor(
    val field: FieldOperations,
    val factor: Double? = null,
    val missing: Double? = null,
    filter: QueryExpr? = null
) : Func(filter) {
    override val name = "field_value_factor"

    override fun toString(): String {
        return "FieldValueFactor(field = $field, factor = $factor, missing = $missing, filter = $filter)"
    }
}

open class SearchQuery<T: SearchQuery<T>>(var docType: String? = null, var query: QueryExpr? = null) {
    val filters = mutableListOf<QueryExpr>()

    object QueryCtx {
        val bool = Bool

        fun multiMatch(
            query: String,
            fields: List<FieldOperations>,
            type: MultiMatch.Type? = null,
            boost: Double? = null
        ) = MultiMatch(query, fields, type, boost)

        fun functionScore(
            query: QueryExpr?,
            boost: Double? = null,
            scoreMode: FunctionScore.ScoreMode? = null,
            boostMode: FunctionScore.BoostMode? = null,
            functions: List<Func>
        ) = FunctionScore(query, boost, scoreMode, boostMode, functions)

        fun weight(weight: Double, filter: QueryExpr? = null) = Weight(weight, filter)

        fun fieldValueFactor(
            field: FieldOperations,
            factor: Double? = null,
            missing: Double? = null,
            filter: QueryExpr? = null
        ) = FieldValueFactor(field, factor, missing, filter)
    }

    constructor(query: QueryExpr? = null) : this(null, query)
    constructor(block: QueryCtx.() -> QueryExpr?) : this(null, QueryCtx.block())
    constructor(docType: String? = null, block: QueryCtx.() -> QueryExpr?) : this(docType, QueryCtx.block())

    @Suppress("UNCHECKED_CAST")
    protected fun self(): T = this as T

    fun usingIndex(index: Index) = BoundSearchQuery(index, docType, this)

    fun query(block: QueryCtx.() -> QueryExpr?): T = query(QueryCtx.block())

    fun query(query: QueryExpr?): T {
        this.query = query
        return self()
    }

    fun filter(vararg filters: QueryExpr): T {
        this.filters += filters
        return self()
    }

    override fun toString(): String {
        return "SearchQuery(query = $query, filters = $filters)"
    }
}

class BoundSearchQuery(
    val index: Index,
    docType: String?,
    searchQuery: SearchQuery<*>
) : SearchQuery<BoundSearchQuery>(docType, searchQuery.query) {
    init {
        filters += searchQuery.filters
    }

    suspend fun execute() = index.search(this)
}

abstract class FieldSet : FieldOperations {
    override var _name = ""
        set(value) {
            if (_name.isNotEmpty()) throw IllegalStateException()
            field = value
        }
    override var _qualifiedName = ""
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

    override fun toString(): String {
        return "FieldSet(_name = $_name, _qualifiedName = $_qualifiedName)"
    }
}

abstract class SubDocument : FieldSet() {
    fun <V: SubDocument> obj(name: String? = null, factory: () -> V) = SubDocumentProperty(name, factory)

    class SubFieldsProperty<T, V: SubFields<T>>(private val name: String?, private val type: Type<T>, private val factory: () -> V) {
        operator fun provideDelegate(thisRef: SubDocument, prop: KProperty<*>): ReadOnlyProperty<SubDocument, V> {
//            println("> SubFieldsProperty.provideDelegate($thisRef, ${prop.name})")
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

class BoundField<T>(val name: String, val qualifiedName: String, val type: Type<T>) : FieldOperations {
    override val _name = name
    override val _qualifiedName = qualifiedName

    override fun toString(): String {
        return "BoundField(name = $name, qualifiedName = $qualifiedName)"
    }

    fun required() = Source.RequiredFieldProperty(this)
    operator fun unaryPlus() = required()

    operator fun getValue(thisRef: Source, prop: KProperty<*>): T? {
        return thisRef._source[name]
            ?.let { type.deserialize(it) }
    }
}

abstract class SubFields<T> : FieldSet() {
    lateinit var _type: Type<T>

    operator fun getValue(thisRef: Source, prop: KProperty<*>): T? {
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
        fun required() = RequiredSubSourceProperty(document, factory)
        operator fun unaryPlus() = required()

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

    class RequiredSubSourceProperty<V: Source>(private val document: SubDocument, private val factory: () -> V) {
        operator fun provideDelegate(thisRef: Source, prop: KProperty<*>): ReadOnlyProperty<Source, V> {
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
                    ?: throw IllegalArgumentException()
            }
            return object : ReadOnlyProperty<Source, V> {
                override fun getValue(thisRef: Source, property: KProperty<*>) = subSource
            }
        }
    }

    class RequiredFieldProperty<T>(private val field: BoundField<T>) {
        operator fun provideDelegate(thisRef: Source, prop: KProperty<*>): ReadOnlyProperty<Source, T> {
            val value by lazy {
                field.type.deserialize(
                    thisRef._source[field.name]
                        ?: throw IllegalArgumentException()
                )
            }
            return object : ReadOnlyProperty<Source, T> {
                override fun getValue(thisRef: Source, property: KProperty<*>) = value
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

        val id by int()
        val name by text().subFields { NameFields() }
        val userOpinion by obj("user_opinion") { OpinionDoc() }
    }

    val id by int()
    val name by text().subFields { NameFields() }
    val status by int()
    val rank by float()
    val company by obj { CompanyDoc() }
}

class ProductSource : Source() {
    class CompanySource : Source() {
        val id: Int by +ProductDoc.company.id
        val name by ProductDoc.company.name
    }

    val id: Int by +ProductDoc.id
    val name: String? by ProductDoc.name
    val status: Int? by ProductDoc.status
    val rank: Float? by ProductDoc.rank
    val company: CompanySource by +ProductDoc.company.source { CompanySource() }
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
        "id" to "111",
        "name" to "Test name",
        "status" to 1,
        "company" to mapOf(
            "id" to "7",
            "name" to "Test company"
        )
    )
    val product = ProductSource().apply { _source = source }
    product.id
        .also { println("id: $it") }
    product.name
        .also { println("name: $it") }
    product.status
        .also { println("status: $it") }
    product.company.id
        .also { println("company.id: $it") }
    product.company.name
        .also { println("company.name: $it") }

    println()
    ProductDoc.status.eq(1)
        .also(::println)
    ProductDoc.name.match("Test name")
        .also(::println)
    Bool(
        filter = listOf(ProductDoc.status.eq(0)),
        should = listOf(
            FunctionScore(
                MultiMatch(
                    "Test term",
                    listOf(ProductDoc.name, ProductDoc.company.name),
                    type = MultiMatch.Type.CROSS_FIELDS
                ),
                functions = listOf(
                    Weight(2.0, ProductDoc.company.userOpinion.count.eq(2)),
                    FieldValueFactor(ProductDoc.rank, 5.0)
                )
        ))
    )
        .also(::println)

    println()
    SearchQuery(
        FunctionScore(
            MultiMatch(
                "Test term",
                listOf(ProductDoc.name, ProductDoc.company.name),
                type = MultiMatch.Type.CROSS_FIELDS
            ),
            functions = listOf(
                Weight(2.0, ProductDoc.company.userOpinion.count.eq(2)),
                FieldValueFactor(ProductDoc.rank, 5.0)
            )
        )
    )
        .filter(ProductDoc.status.eq(0))
        .also(::println)

    SearchQuery {
        functionScore(
            multiMatch(
                "Test term",
                listOf(ProductDoc.name, ProductDoc.company.name),
                type = MultiMatch.Type.CROSS_FIELDS
            ),
            functions = listOf(
                weight(2.0, ProductDoc.company.userOpinion.count.eq(2)),
                fieldValueFactor(ProductDoc.rank, 5.0)
            )
        )
    }
        .also(::println)
}
