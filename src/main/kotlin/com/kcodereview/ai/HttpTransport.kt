package com.kcodereview.ai

import com.intellij.util.net.HttpConfigurable
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors

/**
 * Shared HTTP for LLM + remote config.
 * Tight timeouts so reviews fail fast instead of hanging on "Reviewing…".
 */
object HttpTransport {

    /** TCP connect must succeed quickly. */
    val connectTimeout: Duration = Duration.ofSeconds(8)

    /** Full request (connect + TLS + response) for LLM calls. */
    val llmRequestTimeout: Duration = Duration.ofSeconds(45)

    /** Remote prompt / config fetch. */
    val configRequestTimeout: Duration = Duration.ofSeconds(5)

    val sharedHttpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(connectTimeout)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .proxy(ideAwareProxySelector())
        .executor(
            Executors.newCachedThreadPool { r ->
                Thread(r, "k-code-review-http").apply { isDaemon = true }
            },
        )
        .build()

    fun send(request: HttpRequest): HttpResponse<String> =
        try {
            sharedHttpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (ex: Exception) {
            throw mapNetworkError(ex)
        }

    fun mapNetworkError(ex: Throwable): IllegalStateException {
        val msg = ex.message.orEmpty()
        val cause = generateSequence(ex) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
        val lower = ("$msg $cause").lowercase()
        return when {
            "timed out" in lower || "timeout" in lower || "http connect" in lower ->
                IllegalStateException(
                    "LLM connection timed out. Check internet / VPN / proxy " +
                        "(Settings → Appearance & Behavior → System Settings → HTTP Proxy), " +
                        "then retry. Details: ${ex.javaClass.simpleName}: ${msg.take(200)}",
                    ex,
                )
            "unknown host" in lower || "nodename nor servname" in lower ->
                IllegalStateException(
                    "Cannot resolve LLM host (DNS). Check network and try again. Details: ${msg.take(200)}",
                    ex,
                )
            else ->
                IllegalStateException(
                    "LLM network error: ${ex.javaClass.simpleName}: ${msg.take(300)}",
                    ex,
                )
        }
    }

    private fun ideAwareProxySelector(): ProxySelector =
        object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                if (uri == null) return listOf(Proxy.NO_PROXY)
                return try {
                    val conf = HttpConfigurable.getInstance()
                    if (!conf.USE_HTTP_PROXY || conf.PROXY_HOST.isNullOrBlank()) {
                        ProxySelector.getDefault()?.select(uri) ?: listOf(Proxy.NO_PROXY)
                    } else {
                        val type = if (conf.PROXY_TYPE_IS_SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
                        listOf(Proxy(type, InetSocketAddress(conf.PROXY_HOST, conf.PROXY_PORT)))
                    }
                } catch (_: Throwable) {
                    ProxySelector.getDefault()?.select(uri) ?: listOf(Proxy.NO_PROXY)
                }
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: java.io.IOException?) {
                ProxySelector.getDefault()?.connectFailed(uri, sa, ioe)
            }
        }
}
