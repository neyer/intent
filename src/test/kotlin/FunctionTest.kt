import com.intentevolved.com.intentevolved.voluntas.VoluntasIds
import com.intentevolved.com.intentevolved.voluntas.VoluntasIntentService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FunctionTest {

    private fun freshService() = VoluntasIntentService.new("Function Test Root")

    // --- Successful invocation ---

    @Test
    fun `invoke with positional ref applies field correctly`() {
        val service = freshService()
        val intent = service.addIntent("target intent", 0L)

        val funcId = service.defineFunction("do", listOf("intentId"))
        service.addBodyOp(funcId, VoluntasIds.DEFINES_FIELD, listOf(
            service.paramRef(0),
            service.literalStore.getOrCreate("done"),
            service.literalStore.getOrCreate("BOOL")
        ))
        service.addBodyOp(funcId, VoluntasIds.SETS_FIELD, listOf(
            service.paramRef(0),
            service.literalStore.getOrCreate("done"),
            service.literalStore.getOrCreate(true)
        ))

        service.invokeFunction(funcId, listOf(intent.id()))

        assertEquals(true, service.getById(intent.id())!!.fieldValues()["done"])
    }

    @Test
    fun `invoke with named ref applies field correctly`() {
        val service = freshService()
        val intent = service.addIntent("target intent", 0L)

        val funcId = service.defineFunction("do", listOf("intentId"))
        service.addBodyOp(funcId, VoluntasIds.DEFINES_FIELD, listOf(
            service.paramRef("intentId"),
            service.literalStore.getOrCreate("done"),
            service.literalStore.getOrCreate("BOOL")
        ))
        service.addBodyOp(funcId, VoluntasIds.SETS_FIELD, listOf(
            service.paramRef("intentId"),
            service.literalStore.getOrCreate("done"),
            service.literalStore.getOrCreate(true)
        ))

        service.invokeFunction(funcId, listOf(intent.id()))

        assertEquals(true, service.getById(intent.id())!!.fieldValues()["done"])
    }

    @Test
    fun `positional and named refs are interchangeable within the same function`() {
        val service = freshService()
        val intent = service.addIntent("target intent", 0L)

        val funcId = service.defineFunction("do", listOf("intentId"))
        // Define the field using named ref, set it using positional ref
        service.addBodyOp(funcId, VoluntasIds.DEFINES_FIELD, listOf(
            service.paramRef("intentId"),
            service.literalStore.getOrCreate("done"),
            service.literalStore.getOrCreate("BOOL")
        ))
        service.addBodyOp(funcId, VoluntasIds.SETS_FIELD, listOf(
            service.paramRef(0),
            service.literalStore.getOrCreate("done"),
            service.literalStore.getOrCreate(true)
        ))

        service.invokeFunction(funcId, listOf(intent.id()))

        assertEquals(true, service.getById(intent.id())!!.fieldValues()["done"])
    }

    // --- Bad parameter references at definition time ---

    @Test
    fun `addBodyOp throws for out-of-range positional ref`() {
        val service = freshService()
        val funcId = service.defineFunction("do", listOf("intentId")) // only $0 is valid

        val ex = assertThrows<IllegalArgumentException> {
            service.addBodyOp(funcId, VoluntasIds.SETS_FIELD, listOf(
                service.paramRef(1), // $1 doesn't exist
                service.literalStore.getOrCreate("done"),
                service.literalStore.getOrCreate(true)
            ))
        }
        assert(ex.message!!.contains("\$1"))
    }

    @Test
    fun `addBodyOp throws for unknown named ref`() {
        val service = freshService()
        val funcId = service.defineFunction("do", listOf("intentId"))

        val ex = assertThrows<IllegalArgumentException> {
            service.addBodyOp(funcId, VoluntasIds.SETS_FIELD, listOf(
                service.paramRef("bogus"), // no param named "bogus"
                service.literalStore.getOrCreate("done"),
                service.literalStore.getOrCreate(true)
            ))
        }
        assert(ex.message!!.contains("\$bogus"))
    }

    @Test
    fun `addBodyOp throws for positional ref on zero-parameter function`() {
        val service = freshService()
        val funcId = service.defineFunction("noop", emptyList())

        val ex = assertThrows<IllegalArgumentException> {
            service.addBodyOp(funcId, VoluntasIds.SETS_FIELD, listOf(
                service.paramRef(0)
            ))
        }
        assert(ex.message!!.contains("none")) // error should say no valid params
    }

    @Test
    fun `addBodyOp throws for named ref on zero-parameter function`() {
        val service = freshService()
        val funcId = service.defineFunction("noop", emptyList())

        val ex = assertThrows<IllegalArgumentException> {
            service.addBodyOp(funcId, VoluntasIds.SETS_FIELD, listOf(
                service.paramRef("anything")
            ))
        }
        assert(ex.message!!.contains("none"))
    }
}
