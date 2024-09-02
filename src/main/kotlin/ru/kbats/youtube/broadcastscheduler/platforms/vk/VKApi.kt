package ru.kbats.youtube.broadcastscheduler.platforms.vk

import com.google.gson.annotations.SerializedName
import com.vk.api.sdk.client.AbstractQueryBuilder
import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.UserActor
import com.vk.api.sdk.httpclient.HttpTransportClient
import com.vk.api.sdk.objects.Validable
import com.vk.api.sdk.objects.annotations.ApiParam
import com.vk.api.sdk.objects.annotations.Required
import com.vk.api.sdk.objects.video.VideoFull
import com.vk.api.sdk.objects.video.responses.StartStreamingResponse
import com.vk.api.sdk.objects.video.responses.StopStreamingResponse
import com.vk.api.sdk.queries.video.VideoStartStreamingQuery
import org.slf4j.LoggerFactory.getLogger
import ru.kbats.youtube.broadcastscheduler.data.LectureBroadcastPrivacy
import java.nio.file.Path
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
//        vk.video().addToAlbum(actor)
        VideoAddToAlbumQuery(vk, actor)
            .ownerId(-config.groupId)
            .videoId(videoId)
            .targetId(-config.groupId)
            .albumId(albumId)
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
        return "https://vk.com/video-${config.groupId}_${video.id}" +
                (video.accessKey?.let { "?list=$it" } ?: "")
    }

    fun uploadVideoThumbnail(videoId: Int, photo: Path) {
        val r1 = VideoGetThumbUploadUrlQuery(vk, actor)
            .ownerId(-config.groupId)
            .execute()

        val r2 = vk.transportClient!!.post(r1.uploadUrl, mutableMapOf("photo" to photo.toFile()))
        if (r2.statusCode != 200) {
            logger.warn("Failed to upload thumbnails $photo for video $videoId: status code ${r2.statusCode}, ${r2.content}")
            return
        }

        val r3 = VideoSaveUploadedThumbQuery(vk, actor)
            .ownerId(-config.groupId)
            .thumbJson(r2.content)
            .thumbSize(1)
            .setThumb(1)
            .videoId(videoId)
            .executeAsStringWithReturningFullInfo()
        if (r3.statusCode != 200) {
            logger.warn("Failed to set uploaded thumbnails for video $videoId: status code ${r3.statusCode}, ${r3.content}")
        } else {
            logger.info("Successful set uploaded thumbnails for video $videoId")
        }
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

    private class VideoAddToAlbumQuery(client: VkApiClient, actor: UserActor) :
        AbstractQueryBuilder<VideoAddToAlbumQuery, Int?>(
            client,
            "video.addToAlbum",
            Integer.TYPE
        ) {
        init {
            accessToken(actor.accessToken)
        }

        @ApiParam("target_id")
        fun targetId(value: Long) = unsafeParam("target_id", value)

        @ApiParam("album_id")
        fun albumId(value: Int) = unsafeParam("album_id", value)

        @ApiParam("owner_id")
        fun ownerId(value: Long) = unsafeParam("owner_id", value)

        @ApiParam("video_id")
        fun videoId(value: Int) = unsafeParam("video_id", value)

        override fun getThis() = this
        override fun essentialKeys(): MutableCollection<String> = mutableListOf("access_token")
    }

    private class VideoGetThumbUploadUrlQuery(client: VkApiClient, actor: UserActor) :
        AbstractQueryBuilder<VideoGetThumbUploadUrlQuery, GetThumbUploadUrlResponse>(
            client,
            "video.getThumbUploadUrl",
            GetThumbUploadUrlResponse::class.java
        ) {

        init {
            accessToken(actor.accessToken)
        }

        @ApiParam("owner_id")
        fun ownerId(value: Long) = unsafeParam("owner_id", value)

        override fun getThis() = this
        override fun essentialKeys(): MutableCollection<String> = mutableListOf("access_token")
    }

    private class GetThumbUploadUrlResponse : Validable {
        @SerializedName("upload_url")
        @Required
        val uploadUrl: String? = null
    }


    private class VideoSaveUploadedThumbQuery(client: VkApiClient, actor: UserActor) :
        AbstractQueryBuilder<VideoSaveUploadedThumbQuery, Int>(
            client,
            "video.saveUploadedThumb",
            VideoSaveUploadedThumbQuery::class.java
        ) {

        init {
            accessToken(actor.accessToken)
        }

        @ApiParam("owner_id")
        fun ownerId(value: Long) = unsafeParam("owner_id", value)

        @ApiParam("thumb_json")
        fun thumbJson(value: String) = unsafeParam("thumb_json", value)

        @ApiParam("video_id")
        fun videoId(value: Int) = unsafeParam("video_id", value)

        @ApiParam("thumb_size")
        fun thumbSize(value: Int) = unsafeParam("thumb_size", value)

        @ApiParam("set_thumb")
        fun setThumb(value: Int) = unsafeParam("set_thumb", value)

        override fun getThis() = this

        override fun essentialKeys(): MutableCollection<String> {
            return mutableListOf("access_token")
        }
    }
}
