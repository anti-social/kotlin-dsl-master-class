import kotlinx.serialization.json.*

interface QuerySerializer {
    interface ObjectCtx {
        fun field(name: String, value: Any?)
        fun array(name: String, block: ArrayCtx.() -> Unit)
        fun obj(name: String, block: ObjectCtx.() -> Unit)
    }

    interface ArrayCtx {
        fun value(value: Any?)
        fun array(block: ArrayCtx.() -> Unit)
        fun obj(block: ObjectCtx.() -> Unit)
    }

    fun obj(block: ObjectCtx.() -> Unit): Any
    fun array(block: ArrayCtx.() -> Unit): Any
}

class QueryToMapSerializer(
    private val ignoreNullValues: Boolean = false,
    private val mapFactory: () -> MutableMap<String, Any?> = ::HashMap,
    private val arrayFactory: () -> MutableList<Any?> = ::ArrayList
) : QuerySerializer {

    private inner class ObjectCtx(
        private val map: MutableMap<String, Any?>
    ) : QuerySerializer.ObjectCtx {
        override fun field(name: String, value: Any?) {
            if (value != null || !ignoreNullValues) {
                map[name] = value
            }
        }

        override fun array(name: String, block: QuerySerializer.ArrayCtx.() -> Unit) {
            val childArray = arrayFactory()
            ArrayCtx(childArray).block()
            field(name, childArray)
        }

        override fun obj(name: String, block: QuerySerializer.ObjectCtx.() -> Unit) {
            val childMap = mapFactory()
            ObjectCtx(childMap).block()
            field(name, childMap)
        }
    }

    inner class ArrayCtx(
        private val array: MutableList<Any?>
    ) : QuerySerializer.ArrayCtx {
        override fun value(value: Any?) {
            if (value != null || !ignoreNullValues) {
                array.add(value)
            }
        }

        override fun array(block: QuerySerializer.ArrayCtx.() -> Unit) {
            val childArray = arrayFactory()
            ArrayCtx(childArray).block()
            value(childArray)
        }

        override fun obj(block: QuerySerializer.ObjectCtx.() -> Unit) {
            val childMap = mapFactory()
            ObjectCtx(childMap).block()
            value(childMap)
        }
    }

    override fun obj(block: QuerySerializer.ObjectCtx.() -> Unit): Map<String, Any?> {
        val map = mapFactory()
        ObjectCtx(map).block()
        return map
    }

    override fun array(block: QuerySerializer.ArrayCtx.() -> Unit): List<Any?> {
        val list = arrayFactory()
        ArrayCtx(list).block()
        return list
    }
}

class QueryToJsonSerializationSerializer : QuerySerializer {
    private class ObjectCtx(private val jsonBuilder: JsonBuilder) : QuerySerializer.ObjectCtx {
        override fun field(name: String, value: Any?) = with(jsonBuilder) {
            when (value) {
                is Number? -> name to value
                is Boolean? -> name to value
                is String? -> name to value
                else -> name to value.toString()
            }
        }

        override fun obj(name: String, block: QuerySerializer.ObjectCtx.() -> Unit) = with(jsonBuilder) {
            name to json {
                ObjectCtx(this).block()
            }
        }

        override fun array(name: String, block: QuerySerializer.ArrayCtx.() -> Unit) = with(jsonBuilder) {
            name to jsonArray {
                ArrayCtx(this).block()
            }
        }
    }

    class ArrayCtx(private val arrayBuilder: JsonArrayBuilder) : QuerySerializer.ArrayCtx {
        override fun value(value: Any?) = with(arrayBuilder) {
            when(value) {
                is Number? -> +value
                is Boolean? -> +value
                is String? -> +value
                else -> +value.toString()
            }
        }

        override fun obj(block: QuerySerializer.ObjectCtx.() -> Unit) = with(arrayBuilder) {
            +json {
                ObjectCtx(this).block()
            }
        }

        override fun array(block: QuerySerializer.ArrayCtx.() -> Unit) = with(arrayBuilder) {
            +jsonArray {
                ArrayCtx(this).block()
            }
        }
    }

    override fun obj(block: QuerySerializer.ObjectCtx.() -> Unit): JsonObject {
        return json {
            ObjectCtx(this).block()
        }
    }

    override fun array(block: QuerySerializer.ArrayCtx.() -> Unit): JsonArray {
        return jsonArray {
            ArrayCtx(this).block()
        }
    }
}

class Compiler(private val serializer: QuerySerializer) {
    fun visit(searchQuery: SearchQuery<*>, ctx: QuerySerializer.ObjectCtx) = ctx.run {
        obj("query") {
            searchQuery.query?.let { query ->
                visit(query)
            }
        }
    }

    fun QuerySerializer.ObjectCtx.visit(expr: Expr): Unit = when(expr) {
        is Term -> visit(expr)
        is Match -> visit(expr)
        is MultiMatch -> visit(expr)
        is FunctionScore -> visit(expr)
        is Func -> visit(expr)
        else -> throw IllegalArgumentException()
    }

    fun QuerySerializer.ObjectCtx.visit(query: Term) {
        obj(query.name) {
            field(query.field._qualifiedName, query.other)
        }
    }

    fun QuerySerializer.ObjectCtx.visit(query: Match) {
        obj(query.name) {
            field(query.field._qualifiedName, query.other)
        }
    }

    fun QuerySerializer.ObjectCtx.visit(query: MultiMatch) {
        obj(query.name) {
            field("query", query.query)
            array("fields") {
                query.fields.forEach { value(it._qualifiedName) }
            }
        }
    }

    fun QuerySerializer.ObjectCtx.visit(expr: FunctionScore) {
        obj(expr.name) {
            expr.query?.let { query ->
                obj("query") {
                    visit(query)
                }
            }
            if (expr.boost != null) {
                field("boost", expr.boost)
            }
            array("functions") {
                expr.functions.forEach { obj { visit(it) } }
            }
        }
    }

    fun QuerySerializer.ObjectCtx.visit(func: Func): Unit  {
        when(func) {
            is Weight -> field(func.name, func.weight)
            is FieldValueFactor -> {
                obj(func.name) {
                    field("field", func.field._qualifiedName)
                }
            }
        }
        if (func.filter != null) {
            obj("filter") { visit(func.filter) }
        }
    }
}

fun main() {
    val sq = SearchQuery {
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

    val querySerializer = QueryToJsonSerializationSerializer()
    val compiler = Compiler(querySerializer)
    querySerializer.obj { compiler.visit(sq, this) }
        .also(::println)
}
