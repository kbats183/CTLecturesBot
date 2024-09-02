package ru.kbats.youtube.broadcastscheduler.platforms.restreamer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.HttpClients
import org.slf4j.LoggerFactory.getLogger


class Restreamer(private val apiUrl: String) {
    val rtmpUrl: String = "rtmp://kbats.ru"

    fun createStreamKey(key: String, targets: List<String>) {
        val request = HttpPost(apiUrl)
        request.addJsonEntity(Stream(key, targets))
        client { client ->
            client.execute(request).use {
                logger.info("Creating restreamer key $key response ${it.statusLine}")
                if (it.statusLine.statusCode != 200) {
                    throw RuntimeException("Failed exit code ${it.statusLine}")
                }
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun getStreamKeyStatus(key: String): StreamStatus? {
        val request = HttpGet("$apiUrl/$key/status")
        return client { client ->
            client.execute(request).use {
                logger.info("Getting restreamer status key for $key response ${it.statusLine}")
                if (it.statusLine.statusCode == 404) {
                    return@client null
                }
                if (it.statusLine.statusCode != 200) {
                    throw RuntimeException("Failed exit code ${it.statusLine}")
                }
                return@client Json.decodeFromStream<StreamStatus>(it.entity.content)
            }
        }
    }

    fun addTargetToStream(key: String, target: String) {
        val request = HttpPost("$apiUrl/$key/targets")
        request.addJsonEntity(TargetInfo(target))
        client { client ->
            client.execute(request).use {
                logger.info("Adding target to restreamer key $key response ${it.statusLine}")
                if (it.statusLine.statusCode != 200) {
                    throw RuntimeException("Failed exit code ${it.statusLine}")
                }
            }
        }
    }

    @Serializable
    data class Stream(val name: String, val targets: List<String>)

    @Serializable
    data class TargetInfo(val target: String)

    @Serializable
    data class StreamStatus(
        @SerialName("is_live")
        val isLive: Boolean,
        val bitrate: Long,
        @SerialName("last_frame_time")
        val lastFrameTime: Long
    )

    private inline fun <reified T> HttpEntityEnclosingRequestBase.addJsonEntity(obj: T) {
        entity = StringEntity(
            Json.encodeToString(obj),
            ContentType.APPLICATION_JSON
        )
    }

    private fun <T> client(block: (client: CloseableHttpClient) -> T): T {
        return HttpClientBuilder.create()
            .setDefaultRequestConfig(requestConfig)
            .build()
            .use(block)
    }

    companion object {
        val logger = getLogger(Restreamer::class.java)
        private val requestConfig = RequestConfig.custom()
            .setConnectTimeout(3000)
            .setConnectionRequestTimeout(3000)
            .setSocketTimeout(3000)
            .build()
    }
}
