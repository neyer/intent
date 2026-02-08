import com.intentevolved.*
import com.intentevolved.com.intentevolved.voluntas.VoluntasIntentService
import com.intentevolved.com.intentevolved.voluntas.VoluntasIntentServiceGrpcImpl
import com.intentevolved.com.intentevolved.voluntas.VoluntasServiceGrpcImpl
import com.intentevolved.com.intentevolved.voluntas.VoluntasIds
import io.grpc.ManagedChannelBuilder
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import voluntas.v1.*
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class VoluntasRuntimeTest {

    companion object {
        private lateinit var service: VoluntasIntentService
        private lateinit var server: io.grpc.Server
        private lateinit var channel: io.grpc.ManagedChannel
        private lateinit var intentStub: IntentServiceGrpcKt.IntentServiceCoroutineStub
        private lateinit var voluntasStub: VoluntasServiceGrpcKt.VoluntasServiceCoroutineStub
        private var port: Int = 0

        @TempDir
        @JvmStatic
        lateinit var tempDir: Path

        @BeforeAll
        @JvmStatic
        fun startServer() {
            val fileName = tempDir.resolve("test_runtime.pb").toString()
            service = VoluntasIntentService.new("Runtime Test Root")

            server = ServerBuilder
                .forPort(0) // random free port
                .addService(VoluntasIntentServiceGrpcImpl(service, fileName))
                .addService(VoluntasServiceGrpcImpl(service, fileName))
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start()

            port = server.port

            channel = ManagedChannelBuilder
                .forAddress("localhost", port)
                .usePlaintext()
                .build()

            intentStub = IntentServiceGrpcKt.IntentServiceCoroutineStub(channel)
            voluntasStub = VoluntasServiceGrpcKt.VoluntasServiceCoroutineStub(channel)
        }

        @AfterAll
        @JvmStatic
        fun stopServer() {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
            server.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `submitOp CreateIntent returns success`() = runBlocking {
        val request = SubmitOpRequest.newBuilder()
            .setCreateIntent(CreateIntent.newBuilder()
                .setText("Test intent via gRPC")
                .setParentId(0L))
            .build()

        val response = intentStub.submitOp(request)

        assertTrue(response.success)
        assertTrue(response.message.contains("added intent"))
        assertTrue(response.id > 0)
    }

    @Test
    fun `getIntent returns the root intent`() = runBlocking {
        val response = intentStub.getIntent(
            GetIntentRequest.newBuilder().setId(0L).build()
        )

        assertTrue(response.found)
        assertEquals(0L, response.intent.id)
        assertEquals("Runtime Test Root", response.intent.text)
    }

    @Test
    fun `getIntent returns not found for unknown id`() = runBlocking {
        val response = intentStub.getIntent(
            GetIntentRequest.newBuilder().setId(99999L).build()
        )

        assertFalse(response.found)
        assertTrue(response.error.contains("99999"))
    }

    @Test
    fun `submitOp and getIntent round trip`() = runBlocking {
        val createResponse = intentStub.submitOp(
            SubmitOpRequest.newBuilder()
                .setCreateIntent(CreateIntent.newBuilder()
                    .setText("Round trip intent")
                    .setParentId(0L))
                .build()
        )
        assertTrue(createResponse.success)
        val id = createResponse.id

        val getResponse = intentStub.getIntent(
            GetIntentRequest.newBuilder().setId(id).build()
        )
        assertTrue(getResponse.found)
        assertEquals("Round trip intent", getResponse.intent.text)
        assertEquals(id, getResponse.intent.id)
    }

    @Test
    fun `submitOp UpdateIntentText works`() = runBlocking {
        val createResponse = intentStub.submitOp(
            SubmitOpRequest.newBuilder()
                .setCreateIntent(CreateIntent.newBuilder()
                    .setText("Before edit")
                    .setParentId(0L))
                .build()
        )
        val id = createResponse.id

        val editResponse = intentStub.submitOp(
            SubmitOpRequest.newBuilder()
                .setUpdateIntent(UpdateIntentText.newBuilder()
                    .setId(id)
                    .setNewText("After edit"))
                .build()
        )
        assertTrue(editResponse.success)

        val getResponse = intentStub.getIntent(
            GetIntentRequest.newBuilder().setId(id).build()
        )
        assertEquals("After edit", getResponse.intent.text)
    }

    @Test
    fun `submitOp UpdateIntentParent works`() = runBlocking {
        val parent1 = intentStub.submitOp(
            SubmitOpRequest.newBuilder()
                .setCreateIntent(CreateIntent.newBuilder()
                    .setText("Parent A")
                    .setParentId(0L))
                .build()
        )
        val parent2 = intentStub.submitOp(
            SubmitOpRequest.newBuilder()
                .setCreateIntent(CreateIntent.newBuilder()
                    .setText("Parent B")
                    .setParentId(0L))
                .build()
        )
        val child = intentStub.submitOp(
            SubmitOpRequest.newBuilder()
                .setCreateIntent(CreateIntent.newBuilder()
                    .setText("Movable child")
                    .setParentId(parent1.id))
                .build()
        )

        val moveResponse = intentStub.submitOp(
            SubmitOpRequest.newBuilder()
                .setUpdateIntentParent(UpdateIntentParent.newBuilder()
                    .setId(child.id)
                    .setParentId(parent2.id))
                .build()
        )
        assertTrue(moveResponse.success)

        val scope = intentStub.getFocalScope(
            GetFocalScopeRequest.newBuilder().setId(parent2.id).build()
        )
        assertTrue(scope.found)
        assertTrue(scope.childrenList.any { it.id == child.id })
    }

    @Test
    fun `getFocalScope returns ancestry and children`() = runBlocking {
        val parent = intentStub.submitOp(
            SubmitOpRequest.newBuilder()
                .setCreateIntent(CreateIntent.newBuilder()
                    .setText("Scope parent")
                    .setParentId(0L))
                .build()
        )
        val child1 = intentStub.submitOp(
            SubmitOpRequest.newBuilder()
                .setCreateIntent(CreateIntent.newBuilder()
                    .setText("Scope child 1")
                    .setParentId(parent.id))
                .build()
        )
        val child2 = intentStub.submitOp(
            SubmitOpRequest.newBuilder()
                .setCreateIntent(CreateIntent.newBuilder()
                    .setText("Scope child 2")
                    .setParentId(parent.id))
                .build()
        )

        val scope = intentStub.getFocalScope(
            GetFocalScopeRequest.newBuilder().setId(parent.id).build()
        )

        assertTrue(scope.found)
        assertEquals(parent.id, scope.focus.id)
        assertTrue(scope.ancestryList.isNotEmpty()) // at least root
        assertTrue(scope.childrenList.any { it.id == child1.id })
        assertTrue(scope.childrenList.any { it.id == child2.id })
    }

    @Test
    fun `getFocalScope returns error for unknown id`() = runBlocking {
        val scope = intentStub.getFocalScope(
            GetFocalScopeRequest.newBuilder().setId(99999L).build()
        )
        assertFalse(scope.found)
        assertTrue(scope.error.contains("99999"))
    }

    // --- Voluntas-native service tests ---

    @Test
    fun `submitRelationship creates an entity`() = runBlocking {
        val textLitId = service.literalStore.getOrCreate("Direct gRPC intent")

        val rel = Relationship.newBuilder()
            .setId(500L)
            .addParticipants(VoluntasIds.INSTANTIATES)
            .addParticipants(VoluntasIds.STRING_INTENT_TYPE)
            .addParticipants(textLitId)
            .addParticipants(0L) // parent = root
            .build()

        val response = voluntasStub.submitRelationship(
            SubmitRelationshipRequest.newBuilder()
                .setRelationship(rel)
                .build()
        )

        assertTrue(response.success)
        assertTrue(response.message.contains("created entity 500"))

        // Verify via IntentService endpoint
        val getResponse = intentStub.getIntent(
            GetIntentRequest.newBuilder().setId(500L).build()
        )
        assertTrue(getResponse.found)
        assertEquals("Direct gRPC intent", getResponse.intent.text)
    }

    @Test
    fun `submitLiteral registers a literal`() = runBlocking {
        val litId = VoluntasIds.literalId(1000L)
        val literal = Literal.newBuilder()
            .setId(litId)
            .setStringVal("test literal value")
            .build()

        val response = voluntasStub.submitLiteral(
            SubmitLiteralRequest.newBuilder()
                .setLiteral(literal)
                .build()
        )

        assertTrue(response.success)
        assertTrue(response.message.contains(litId.toString()))

        // Verify the literal is accessible
        val stored = service.literalStore.getById(litId)
        assertNotNull(stored)
        assertEquals("test literal value", stored!!.stringVal)
    }

    @Test
    fun `submitRelationship with sets_field updates text`() = runBlocking {
        // Create an intent first
        val createResp = intentStub.submitOp(
            SubmitOpRequest.newBuilder()
                .setCreateIntent(CreateIntent.newBuilder()
                    .setText("Before voluntas edit")
                    .setParentId(0L))
                .build()
        )
        val intentId = createResp.id

        // Use Voluntas-native API to set the text field
        val fieldNameLitId = service.literalStore.getOrCreate("text")
        val newTextLitId = service.literalStore.getOrCreate("After voluntas edit")

        val rel = Relationship.newBuilder()
            .setId(600L)
            .addParticipants(VoluntasIds.SETS_FIELD)
            .addParticipants(intentId)
            .addParticipants(fieldNameLitId)
            .addParticipants(newTextLitId)
            .build()

        val response = voluntasStub.submitRelationship(
            SubmitRelationshipRequest.newBuilder()
                .setRelationship(rel)
                .build()
        )
        assertTrue(response.success)

        // Verify via IntentService
        val getResponse = intentStub.getIntent(
            GetIntentRequest.newBuilder().setId(intentId).build()
        )
        assertEquals("After voluntas edit", getResponse.intent.text)
    }

    @Test
    fun `do command flow - addField then setFieldValue preserves text`() = runBlocking {
        // Create an intent
        val createResp = intentStub.submitOp(
            SubmitOpRequest.newBuilder()
                .setCreateIntent(CreateIntent.newBuilder()
                    .setText("Intent to mark done")
                    .setParentId(0L))
                .build()
        )
        assertTrue(createResp.success)
        val intentId = createResp.id

        // Verify text is correct before do
        val beforeGet = intentStub.getIntent(
            GetIntentRequest.newBuilder().setId(intentId).build()
        )
        assertEquals("Intent to mark done", beforeGet.intent.text)

        // Add 'done' field (like DoCommand does)
        val addFieldResp = intentStub.submitOp(
            SubmitOpRequest.newBuilder()
                .setAddField(AddField.newBuilder()
                    .setIntentId(intentId)
                    .setFieldName("done")
                    .setFieldType(FieldType.FIELD_TYPE_BOOL))
                .build()
        )
        assertTrue(addFieldResp.success)

        // Set 'done' field to true (like DoCommand does)
        val setValueResp = intentStub.submitOp(
            SubmitOpRequest.newBuilder()
                .setSetFieldValue(SetFieldValue.newBuilder()
                    .setIntentId(intentId)
                    .setFieldName("done")
                    .setBoolValue(true))
                .build()
        )
        assertTrue(setValueResp.success)

        // Verify text is still correct after do
        val afterGet = intentStub.getIntent(
            GetIntentRequest.newBuilder().setId(intentId).build()
        )
        assertEquals("Intent to mark done", afterGet.intent.text)
        assertTrue(afterGet.intent.fieldValuesMap.containsKey("done"))
        assertTrue(afterGet.intent.fieldValuesMap["done"]!!.boolValue)

        // Verify focal scope still shows correct texts
        val scope = intentStub.getFocalScope(
            GetFocalScopeRequest.newBuilder().setId(intentId).build()
        )
        assertTrue(scope.found)
        assertEquals("Intent to mark done", scope.focus.text)

        // Root should still have its text in ancestry
        val rootInAncestry = scope.ancestryList.find { it.id == 0L }
        assertNotNull(rootInAncestry)
        assertEquals("Runtime Test Root", rootInAncestry!!.text)
    }
}
