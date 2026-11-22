package com.vknxworks.truectrl.data.system

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellSessionTest {
    private class FakeSocket : WebSocket {
        var closedWith: Int? = null
        override fun request(): Request = Request.Builder().url("https://nas.example/websocket/shell").build()
        override fun queueSize(): Long = 0
        override fun send(text: String): Boolean = true
        override fun send(bytes: ByteString): Boolean = true
        override fun close(code: Int, reason: String?): Boolean { closedWith = code; return true }
        override fun cancel() {}
    }

    private val ignore = object : ShellSession.Listener {
        override fun onConnected() {}
        override fun onBytes(bytes: ByteArray) {}
        override fun onClosed(code: Int, reason: String) {}
        override fun onFailure(error: Throwable) {}
    }

    @Test fun closedWhileTheTokenIsOnItsWayNeverOpensTheShell() = runBlocking {
        val token = CompletableDeferred<JsonElement>()
        var opened = 0
        val s = ShellSession({ _, _ -> token.await() }, { _, _: WebSocketListener -> opened++; FakeSocket() })
        val job = async(start = CoroutineStart.UNDISPATCHED) { s.connect(ignore) }
        s.close() // Reconnect tapped while auth.generate_token is still running
        token.complete(JsonPrimitive("t0ken"))
        job.await()
        assertEquals(0, opened)
    }

    @Test fun closedJustAsTheSocketOpensClosesItAtOnce() = runBlocking {
        val fake = FakeSocket()
        lateinit var s: ShellSession
        s = ShellSession({ _, _ -> JsonPrimitive("t0ken") }, { _, _: WebSocketListener -> s.close(); fake })
        s.connect(ignore)
        assertEquals(1000, fake.closedWith)
    }

    @Test fun anOpenSessionStaysOpenUntilClosed() = runBlocking {
        val fake = FakeSocket()
        val s = ShellSession({ _, _ -> JsonPrimitive("t0ken") }, { _, _: WebSocketListener -> fake })
        s.connect(ignore)
        assertEquals(null, fake.closedWith)
        s.close()
        assertTrue(fake.closedWith == 1000)
    }
}
