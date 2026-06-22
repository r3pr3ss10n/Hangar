package eu.r3pr3ss10n.hangar.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies server-address normalisation used before building requests. */
class ServerUrlProviderTest {

    @Test
    fun addsHttpsWhenSchemeMissing() {
        assertEquals("https://hangar.example.com", ServerUrlProvider.normalize("hangar.example.com"))
    }

    @Test
    fun preservesExplicitHttpScheme() {
        assertEquals("http://192.168.1.10:8080", ServerUrlProvider.normalize("http://192.168.1.10:8080"))
    }

    @Test
    fun trimsTrailingSlashesAndWhitespace() {
        assertEquals("https://drive.local", ServerUrlProvider.normalize("  https://drive.local/  "))
    }

    @Test
    fun setNullClearsBaseUrl() {
        val provider = ServerUrlProvider()
        provider.set("https://example.com")
        provider.set(null)
        assertNull(provider.baseUrl)
    }

    @Test
    fun setParsesHostAndPort() {
        val provider = ServerUrlProvider()
        provider.set("http://10.0.0.5:9000")
        val url = provider.baseUrl
        assertEquals("10.0.0.5", url?.host)
        assertEquals(9000, url?.port)
        assertEquals("http", url?.scheme)
    }
}
