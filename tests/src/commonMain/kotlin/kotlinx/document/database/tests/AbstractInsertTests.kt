package kotlinx.document.database.tests

import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.document.database.DataStore
import kotlinx.document.database.getObjectCollection
import kotlinx.serialization.json.JsonPrimitive

abstract class AbstractInsertTests(store: DataStore) : BaseTest(store) {
    @Test
    @JsName("inserts_and_retrieves_a_document_without_index")
    fun `inserts and retrieves a document without index`() =
        runDatabaseTest {
            val collection = db.getObjectCollection<TestUser>("test")
            val testUser =
                TestUser(
                    name = "mario",
                    age = 20,
                    addresses = listOf(Address("street", 1)),
                )

            val expected = collection.insert(testUser)

            assertEquals(
                expected = expected,
                actual = collection.iterateAll().first(),
                message = "Collection should have 1 element",
            )
        }

    @Test
    @JsName("inserts_and_retrieves_a_document_with_index")
    fun `inserts and retrieves a document with index`() =
        runDatabaseTest {
            val collection = db.getObjectCollection<TestUser>("test")
            collection.createIndex("name")
            val testUser =
                TestUser(
                    name = "mario",
                    age = 20,
                    addresses = listOf(Address("street", 1)),
                )

            val expected = collection.insert(testUser)

            assertEquals(
                expected = expected,
                actual = collection.iterateAll().first(),
                message = "Collection should have 1 element",
            )

            assertEquals(
                expected =
                    collection.details()
                        .indexes
                        .getValue("name")
                        .getValue(JsonPrimitive(expected.name)),
                actual = setOf(expected.id),
                message = "Index should be the one of Mario",
            )
        }
}
