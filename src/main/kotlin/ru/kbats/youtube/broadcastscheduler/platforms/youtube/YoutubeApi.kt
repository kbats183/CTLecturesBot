package ru.kbats.youtube.broadcastscheduler.platforms.youtube

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.http.AbstractInputStreamContent
import com.google.api.client.util.DateTime
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import ru.kbats.youtube.broadcastscheduler.data.LectureBroadcastPrivacy
import ru.kbats.youtube.broadcastscheduler.youtube.Auth
import java.io.IOException
import java.util.*
import kotlin.time.Duration.Companion.hours

class YoutubeApi(credential: Credential) {
    private val youtube = YouTube.Builder(Auth.HTTP_TRANSPORT, Auth.JSON_FACTORY, credential)
        .setApplicationName("youtube-cmdline-createbroadcast-sample").build()

    fun getStreams(): List<LiveStream> {
        return youtube.liveStreams().list("id,snippet,cdn,status").setMine(true).setMaxResults(50L)
            .execute().items!!.sortedBy { it.snippet.title }
    }

    fun getStream(id: String): LiveStream? =
        youtube.liveStreams().list("id,snippet,cdn,status").setId(id).setMaxResults(50L).execute().items.firstOrNull()

    fun createStream(title: String): LiveStream {
        // Create a snippet with the video stream's title.
        val streamSnippet = LiveStreamSnippet()
        streamSnippet.title = title

        // Define the content distribution network settings for the
        // video stream. The settings specify the stream's format and
        // ingestion type. See:
        // https://developers.google.com/youtube/v3/live/docs/liveStreams#cdn
        val cdnSettings = CdnSettings()
        cdnSettings.format = "1080p"
        cdnSettings.ingestionType = "rtmp"
        cdnSettings.resolution = "variable"
        cdnSettings.frameRate = "variable"
        val stream = LiveStream()
        stream.kind = "youtube#liveStream"
        stream.snippet = streamSnippet
        stream.cdn = cdnSettings

        try {
            // Construct and execute the API request to insert the stream.
            val liveStreamInsert = youtube.liveStreams().insert("snippet,cdn", stream)
            return liveStreamInsert.execute()!!
        } catch (e: IOException) {
            throw FailedApiRequestException(e)
        }
    }

    fun getBroadcasts(broadcastStatus: String? = null): MutableList<LiveBroadcast> {
        return youtube.liveBroadcasts().list("id,snippet,contentDetails,status")
            .setBroadcastStatus(broadcastStatus ?: "all").setMaxResults(50L).execute().items
    }

    fun getBroadcast(id: String): LiveBroadcast? {
        val response =
            youtube.liveBroadcasts().list("id,snippet,contentDetails,status").setId(id).setMaxResults(50L).execute()
        return response.items.firstOrNull()
    }

    /*
    Changes status of broadcast by id. From ready to testing, from testing to live, from live to complete
     */
    fun transitionBroadcast(id: String, newStatus: String): LiveBroadcast {
        return youtube.liveBroadcasts().transition(newStatus, id, "id,snippet,contentDetails,status").execute()
    }

    fun createBroadcast(
        title: String,
        description: String,
        startTime: Instant? = null,
        endTime: Instant? = null,
        privacy: LectureBroadcastPrivacy,
    ): LiveBroadcast {
        val broadcastSnippet = LiveBroadcastSnippet()
        broadcastSnippet.title = title
        broadcastSnippet.description = description
        val startTimeF = startTime ?: Clock.System.now()
        val endTimeF = endTime ?: (startTimeF + 12.hours)
        broadcastSnippet.scheduledStartTime = DateTime(startTimeF.toEpochMilliseconds())
        broadcastSnippet.scheduledEndTime = DateTime(endTimeF.toEpochMilliseconds())

        val status = LiveBroadcastStatus()
        status.privacyStatus = privacy.name.lowercase(Locale.getDefault())

        val broadcast = LiveBroadcast()
        broadcast.kind = "youtube#liveBroadcast"
        broadcast.snippet = broadcastSnippet
        broadcast.status = status

        return youtube.liveBroadcasts().insert("snippet,status", broadcast).execute()
    }

    fun uploadVideoThumbnail(videoId: String, thumbnailPng: AbstractInputStreamContent): ThumbnailSetResponse? {
        return youtube.thumbnails().set(videoId, thumbnailPng).execute()
    }

    fun bindBroadcastStream(broadcastId: String, streamId: String): LiveBroadcast? {
        return youtube.liveBroadcasts().bind(broadcastId, "id,snippet,contentDetails,status")
            .setStreamId(streamId).execute()
    }

    fun updateVideo(
        videoId: String,
        title: String,
        description: String,
        privacy: LectureBroadcastPrivacy,
    ): Video? {
        val video = Video()
        video.id = videoId
        video.snippet = VideoSnippet().also { videoSnippet ->
            videoSnippet.title = title
            videoSnippet.description = description
            videoSnippet.categoryId = "27" // education
        }
        video.status = VideoStatus().apply {
            privacyStatus = privacy.name.lowercase(Locale.getDefault())
        }

        return youtube.videos().update("snippet,status", video).execute()
    }

    fun getVideo(id: String): Video? {
        val response =
            youtube.videos().list("id,snippet,contentDetails,status").setId(id).setMaxResults(50L).execute()
        return response.items.firstOrNull()
    }

    fun createPlaylist(title: String, description: String, privacy: LectureBroadcastPrivacy): String {
        val playlist = Playlist()
        playlist.snippet = PlaylistSnippet()
        playlist.snippet.title = title
        playlist.snippet.description = description
        playlist.status = PlaylistStatus()
        playlist.status.privacyStatus = privacy.name.lowercase(Locale.US)
        return youtube.playlists().insert("snippet,status", playlist).execute().id
    }

    fun getPlaylistItems(playlistId: String? = null, videoId: String? = null): List<PlaylistItem> {
        val request = youtube.playlistItems().list("id,snippet,status,contentDetails")
        playlistId?.let { request.playlistId = it }
        request.videoId = videoId
        request.maxResults = 50
        return request.execute().items!!
    }

    fun addVideoToPlaylist(playlistId: String, videoId: String): PlaylistItem? {
        val item = PlaylistItem()
        item.snippet = PlaylistItemSnippet().apply {
            this.playlistId = playlistId
            this.resourceId = ResourceId().apply {
                this.kind = "youtube#video"
                this.videoId = videoId
            }
        }
        return youtube.playlistItems().insert("id,snippet", item).execute()!!
    }

    fun getAllVideosBefore(channelId: String?, limit: Int = 10000, publishedAfter: String?): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        var pageToken: String? = null
        for (i in 1..100) {
            println("page $i $pageToken")
            val request = youtube.search().list("snippet")
                .setChannelId(channelId)
                .setType("type")
                .setOrder("date")
                .setPageToken(pageToken)
                .setMaxResults(50)
            publishedAfter?.let { request.publishedAfter = DateTime.parseRfc3339(publishedAfter) }
            val response = request.execute()
            results += response.items!!
            pageToken = response.nextPageToken ?: break
            Thread.sleep(1000)
        }
        return results
    }
}
