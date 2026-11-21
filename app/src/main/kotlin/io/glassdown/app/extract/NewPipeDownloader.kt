package io.glassdown.app.extract

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

class NewPipeDownloader private constructor(
    private val client: OkHttpClient,
) : Downloader() {

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val url = request.url()
        val method = request.httpMethod()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val builder = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)

        headers.forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }

        val body = dataToSend?.toRequestBody(null, 0, dataToSend.size)
        when (method) {
            "GET", "HEAD" -> builder.method(method, null)
            "POST" -> builder.post(body ?: ByteArray(0).toRequestBody(null))
            else -> builder.method(method, body)
        }

        client.newCall(builder.build()).execute().use { resp ->
            val responseBody = resp.body?.string()
            if (resp.code == 429) {
                throw ReCaptchaException("reCaptcha challenge requested", url)
            }
            val responseHeaders = resp.headers.toMultimap()
            return Response(
                resp.code,
                resp.message,
                responseHeaders,
                responseBody,
                resp.request.url.toString(),
            )
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

        @Volatile
        private var instance: NewPipeDownloader? = null

        fun getInstance(): NewPipeDownloader {
            return instance ?: synchronized(this) {
                instance ?: NewPipeDownloader(buildClient()).also { instance = it }
            }
        }

        private fun buildClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
