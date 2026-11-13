package com.vknxworks.truectrl.data.notify

import com.vknxworks.truectrl.FakeHttpServer
import com.vknxworks.truectrl.data.api.TnJson
import com.vknxworks.truectrl.data.api.get
import com.vknxworks.truectrl.data.api.long
import com.vknxworks.truectrl.data.api.str
import com.vknxworks.truectrl.data.overview.Severity
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NtfyTest {
    @Test
    fun validatesWhatTheUserTyped() {
        assertEquals(NtfyProblem.NO_SERVER, Ntfy.target(" ", "t", "", "").second)
        assertEquals(NtfyProblem.BAD_SERVER, Ntfy.target("ftp://x", "t", "", "").second)
        assertEquals(NtfyProblem.BAD_SERVER, Ntfy.target("https://ntfy.example.com/?a=b", "t", "", "").second)
        assertEquals(NtfyProblem.NO_TOPIC, Ntfy.target("ntfy.example.com", "", "", "").second)
        assertEquals(NtfyProblem.BAD_TOPIC, Ntfy.target("ntfy.example.com", "disk alerts", "", "").second)
        assertEquals(NtfyProblem.BAD_TOPIC, Ntfy.target("ntfy.example.com", "a".repeat(65), "", "").second)
        // A token never goes over plain HTTP.
        assertEquals(NtfyProblem.TOKEN_OVER_HTTP, Ntfy.target("http://192.168.1.5:8080", "nas", "tk_secret", "").second)
        val (ok, problem) = Ntfy.target("ntfy.example.com/", "/nas_disks-1", " tk_abc ", "AB:CD")
        assertNull(problem)
        assertEquals("https://ntfy.example.com/", ok!!.server.toString())
        assertEquals("nas_disks-1", ok.topic)
        assertEquals("tk_abc", ok.token)
        assertEquals("AB:CD", ok.certPin)
        // Plain HTTP without a token is allowed (the settings screen warns).
        assertNull(Ntfy.target("http://192.168.1.5:8080", "nas", "", "").second)
    }

    @Test
    fun buildsAJsonPublishToTheServerRoot() {
        val target = Ntfy.target("https://ntfy.example.com/sub/", "nas", "tk_abc", "").first!!
        val r = Ntfy.request(target, "Disk health on nas", "Disk sdc in pool media is FAULTED", Severity.CRITICAL)
        assertEquals("POST", r.method)
        assertEquals("https://ntfy.example.com/sub/", r.url.toString())
        assertEquals("Bearer tk_abc", r.header("Authorization"))
        val body = TnJson.parseToJsonElement(Buffer().also { r.body!!.writeTo(it) }.readUtf8())
        assertEquals("nas", body["topic"].str())
        assertEquals("Disk health on nas", body["title"].str())
        assertEquals("Disk sdc in pool media is FAULTED", body["message"].str())
        assertEquals(5L, body["priority"].long())
        assertTrue(r.body!!.contentType().toString().startsWith("application/json"))
    }

    @Test
    fun noTokenNoAuthorizationHeader() {
        val target = Ntfy.target("https://ntfy.example.com", "nas", "", "").first!!
        assertNull(Ntfy.request(target, "t", "m", Severity.WARNING).header("Authorization"))
        assertEquals(4, Ntfy.priority(Severity.WARNING))
    }

    @Test
    fun postsToAFakeNtfyServer() = runBlocking {
        FakeHttpServer { FakeHttpServer.Response(200, """{"id":"x","event":"message"}""") }.use { server ->
            val target = Ntfy.target(server.url, "nas", "", "").first!!
            Ntfy.publish(OkHttpClient(), target, "Disk warning", "Pool media is DEGRADED", Severity.WARNING)
            val req = server.requests.single()
            assertEquals("POST", req.method)
            assertEquals("/", req.path)
            assertEquals("nas", TnJson.parseToJsonElement(req.body)["topic"].str())
            assertNull(req.header("Authorization"))
        }
    }

    @Test
    fun refusedAndRedirectedPostsFailWithoutFollowing() = runBlocking {
        FakeHttpServer { FakeHttpServer.Response(200) }.use { elsewhere ->
            FakeHttpServer { FakeHttpServer.Response(302, headers = mapOf("Location" to "${elsewhere.url}/")) }.use { redirecting ->
                val target = Ntfy.target(redirecting.url, "nas", "", "").first!!
                try {
                    // Even a client that would follow redirects is told not to.
                    Ntfy.publish(OkHttpClient(), target, "t", "m", Severity.WARNING)
                    fail("a redirect must not count as delivered")
                } catch (e: NtfyHttpException) {
                    assertEquals(302, e.code)
                }
                assertTrue(elsewhere.requests.isEmpty())
            }
        }
        FakeHttpServer { FakeHttpServer.Response(403, """{"code":40301,"error":"forbidden"}""") }.use { server ->
            try {
                Ntfy.publish(OkHttpClient(), Ntfy.target(server.url, "nas", "", "").first!!, "t", "m", Severity.WARNING)
                fail("403 must fail")
            } catch (e: NtfyHttpException) {
                assertEquals(403, e.code)
            }
        }
    }
}
