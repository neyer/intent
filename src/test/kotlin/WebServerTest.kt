import com.intentevolved.com.intentevolved.server.configureWebApp
import com.intentevolved.com.intentevolved.voluntas.VoluntasIntentService
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import com.google.gson.Gson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class WebServerTest {

    private val gson = Gson()

    private fun createTestService(): VoluntasIntentService {
        return VoluntasIntentService.new("Test Root Intent")
    }

    @Test
    fun `health endpoint returns ok`() = testApplication {
        val service = createTestService()
        application {
            configureWebApp(service, service)
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = gson.fromJson(response.bodyAsText(), Map::class.java)
        assertEquals("ok", body["status"])
    }

    @Test
    fun `get intent 0 returns root intent`() = testApplication {
        val service = createTestService()
        application {
            configureWebApp(service, service)
        }

        val response = client.get("/api/intent/0")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = gson.fromJson(response.bodyAsText(), Map::class.java)
        assertEquals(0.0, body["id"])
        assertEquals("Test Root Intent", body["text"])
    }

    @Test
    fun `get nonexistent intent returns 404`() = testApplication {
        val service = createTestService()
        application {
            configureWebApp(service, service)
        }

        val response = client.get("/api/intent/99999")
        assertEquals(HttpStatusCode.NotFound, response.status)

        val body = gson.fromJson(response.bodyAsText(), Map::class.java)
        assertEquals("Intent not found", body["error"])
    }
}
