# Creating DSL with Kotlin or "delegate it"

We'll try to create a DSL for building Elasticsearch queries

Our goal is to allow to define a document like:

```kotlin
object ProductDoc : Document() {
    class NameFields<T> : SubFields<T>() {
        val sort by keyword()
    }
    
    class CompanyDoc : SubDocument() {
        val id by int()
        val name by text().subFields { NameFields() }
    }
    
    val id by int()
    val name by text().subFields { NameFields() }
    val company by obj { CompanyDoc() }
    val rank by float()
}

```

And building queries like:

```kotlin
SearchQuery {
    functionScore(
        multiMatch(
            "Test name",
            listOf(ProductDoc.name, ProductDoc.company.name),
            type = multiMatch.type.crossFields
        ),
        functions = listOf(
            fieldValueFactor(ProductDoc.rank, 5.0)
        )
    )
}
```
