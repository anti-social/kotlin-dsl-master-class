import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import io.ktor.http.clone
import io.ktor.http.takeFrom

class Cluster(val httpClient: HttpClient, url: String) {
    private val url = URLBuilder().takeFrom(url)

    operator fun get(indexName: String) = Index(this, indexName)

    suspend fun search(indexName: String, searchQuery: SearchQuery<*>): String {
        val querySerializer = QueryToJsonSerializationSerializer()
        val compiler = Compiler(querySerializer)
        val serializedQuery = querySerializer.obj { compiler.visit(searchQuery, this) }
            .toString()

        val searchUrl = url.clone().apply { path(indexName, searchQuery.docType ?: "_doc", "_search") }.build()
        val response = httpClient.get<String>(searchUrl.toString()) {
            body = serializedQuery
        }
        return response
    }
}

class Index(val cluster: Cluster, val name: String) {
    fun searchQuery(query: QueryExpr) = SearchQuery(query).usingIndex(this)
    fun searchQuery(block: SearchQuery.QueryCtx.() -> QueryExpr?) = SearchQuery(block).usingIndex(this)
    fun searchQuery(docType: String, block: SearchQuery.QueryCtx.() -> QueryExpr?) =
        SearchQuery(docType, block).usingIndex(this)

    suspend fun search(searchQuery: SearchQuery<*>) = cluster.search(this.name, searchQuery)
}

suspend fun main() {
    val client = HttpClient(CIO)
    val cluster = Cluster(client, "http://es1.uaprom:9208")
    val index = cluster["ua_trunk_catalog_v1"]
    val sq = index.searchQuery("product") {
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
        .filter(ProductDoc.status.eq(0))

    println(sq.execute())
}