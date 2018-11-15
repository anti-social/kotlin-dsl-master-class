import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get

suspend fun main() {
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
    val serializedQuery = querySerializer.obj { compiler.visit(sq, this) }
        .toString()

    val client = HttpClient(CIO)
    val response = client.get<String>("http://es1.uaprom:9208/ua_trunk_catalog_v1/product/_search") {
        body = serializedQuery
    }
    println(response)
}