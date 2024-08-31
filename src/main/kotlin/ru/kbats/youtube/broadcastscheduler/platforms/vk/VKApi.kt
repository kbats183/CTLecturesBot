package ru.kbats.youtube.broadcastscheduler.platforms.vk

import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.UserActor
import com.vk.api.sdk.httpclient.HttpTransportClient
import com.vk.api.sdk.objects.video.VideoFull
import com.vk.api.sdk.objects.video.responses.StartStreamingResponse
import com.vk.api.sdk.objects.video.responses.StopStreamingResponse
import com.vk.api.sdk.queries.video.VideoStartStreamingQuery
import org.slf4j.LoggerFactory.getLogger
import ru.kbats.youtube.broadcastscheduler.data.LectureBroadcastPrivacy
import java.util.*

class VKApi(private val config: VKApiConfig) {
    private val vk = VkApiClient(HttpTransportClient())
    private val actor = UserActor(config.userId, config.userToken)


    fun createAlbum(title: String, privacy: LectureBroadcastPrivacy): Int {
        val r = vk.video().addAlbum(actor)
            .groupId(config.groupId)
            .title(title)
            .privacy(if (privacy == LectureBroadcastPrivacy.Public) "all" else "only_me")
            .execute()
        return r.albumId
    }

    fun getAlbumUrl(albumId: Int): String {
        return "https://vk.com/video/playlist/-${config.groupId}_${albumId}"
    }

    fun addVideoToAlbum(albumId: Int, videoId: Int) {
        vk.video().addToAlbum(actor)
            .albumId(albumId)
            .videoId(videoId)
            .execute()
    }

    fun createBroadcast(title: String, description: String, privacy: LectureBroadcastPrivacy): StartStreamingResponse {
        val r = VideoStartStreamingQuery2(vk, actor)
            .groupId(config.groupId)
            .name(title)
            .description(description)
            .publish(false)
            .wallpost(false)
            .privacyView(listOf(if (privacy == LectureBroadcastPrivacy.Public) "all" else "by_link"))
            .execute()
        return r
    }

    fun startStream(videoId: Int): StartStreamingResponse {
        val r = vk.video().startStreaming(actor)
            .groupId(config.groupId)
            .videoId(videoId)
            .publish(true)
            .execute()
        return r
    }

    fun stopStream(videoId: Int): StopStreamingResponse {
        val r = vk.video().stopStreaming(actor)
            .groupId(config.groupId)
            .videoId(videoId)
            .execute()
        return r
    }

    fun getVideo(videoId: Int): VideoFull? {
        val r = vk.video().get(actor)
            .videos("-${config.groupId}_$videoId")
            .execute()
        return r.items.getOrNull(0)
    }

    fun getVideoLink(video: VideoFull): String {
        return "https://vk.com/video-${config.groupId}_${video.id}?list=${video.accessKey}"
    }

    companion object {
        val vk = VkApiClient(HttpTransportClient())
        const val CLIENT_ID = 5681068
        const val REDIRECT_URI = "https://api.vk.com/blank.html"
        private val logger = getLogger(VKApi::class.java)
    }

    class VKApiConfig(val userToken: String, val userId: Long, val groupId: Long)

    private class VideoStartStreamingQuery2(client: VkApiClient, actor: UserActor) :
        VideoStartStreamingQuery(client, actor) {
        override fun build(): MutableMap<String, String> {
            return Collections.unmodifiableMap(super.build().toMutableMap().also {
                it["notify_followers"] = "0"
                it["preparation_check"] = "1"
                it["preparation"] = "1"
            })
        }
        }
}
