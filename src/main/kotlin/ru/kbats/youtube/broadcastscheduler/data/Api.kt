package ru.kbats.youtube.broadcastscheduler.data

import com.google.api.services.youtube.model.LiveStream
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import ru.kbats.youtube.broadcastscheduler.Application

data class Admin(@BsonId val id: ObjectId = ObjectId(), val login: String, val comment: String)

data class ThumbnailsImage(
    @BsonId val id: ObjectId = ObjectId(),
    val name: String,
)

data class ThumbnailsTemplate(
    @BsonId val id: ObjectId = ObjectId(),
    val name: String,
    val firstTitle: String,
    val secondTitle: String,
    val lecturerName: String,
    val termNumber: String,
    val color: String,
    val imageId: ObjectId? = null
)

data class Lesson(
    @BsonId val id: ObjectId = ObjectId(),
    val name: String,
    val title: String,
    val lecturerName: String,
    val termNumber: String,

    val mainTemplateId: ObjectId?,
    val doubleNumerationFormat: Boolean,
    val lectureType: LectureType = LectureType.Lecture,
    val year: String = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .year.toString(), // TODO: use connect year
    // TODO: playlists
    // TODO: streamingSettings

    val lessonPrivacy: LectureBroadcastPrivacy = LectureBroadcastPrivacy.Public,
    val youtubePlaylistId: String? = null,
    val vkPlaylistId: Int? = null,
    val streamKey: StreamKey? = null,

    val currentLectureNumber: Int = 1,
) {
    fun nextLectureNumber() =
        if (doubleNumerationFormat) "$currentLectureNumber-${currentLectureNumber + 1}" else "$currentLectureNumber"

    fun titleTermNumber() = termNumber.toIntOrNull()?.let { "s$it" } ?: termNumber

    fun videoTitle(): String = "[${titleTermNumber()} | ${year}] $title, $lecturerName"

    fun description(): String {
        val term = when (termNumber) {
            "1" -> " в первом семестре"
            "2" -> " во втором семестре"
            "3" -> " в третьем семестре"
            "4" -> " в четвертом семестре"
            "5" -> " в пятом семестре"
            "6" -> " в шестом семестре"
            "7" -> " в седьмом семестре"
            "8" -> " в восьмом семестре"
            "SC" -> " в качестве курса по выбору"
            else -> ""
        }
        val type = if (lectureType == LectureType.Lecture) "лекций" else "практик"
        return "Записи ${type} по курсу «${title}», " +
                "который читается для студентов программы «Прикладная математика и информатика» факультета ИТиП университета ИТМО${term}.\n" +
                "Лектор: $lecturerName"
    }

    fun descriptionFull(application: Application) = buildString {
        append(description())
        if (youtubePlaylistId != null) append("\nhttps://www.youtube.com/playlist?list=${youtubePlaylistId}")
        if (lessonPrivacy == LectureBroadcastPrivacy.Public && vkPlaylistId != null)
            append(
                "\n${application.vkApi.getAlbumUrl(vkPlaylistId)}"
            )
    }
}

@Serializable
sealed class StreamKey {
    @Serializable
    @SerialName("Youtube")
    data class Youtube(val id: String, val name: String, val key: String) : StreamKey() {
        constructor(liveStream: LiveStream) : this(
            liveStream.id,
            liveStream.snippet.title,
            liveStream.cdn.ingestionInfo.streamName
        )
    }

    @Serializable
    @SerialName("Restreamer")
    data class Restreamer(
        @Contextual
        @SerialName("_id")
        val id: ObjectId = ObjectId(),
        val name: String,
        val youtube: Youtube? = null,
        val creationTime: Instant? = Clock.System.now(),
    ) : StreamKey()
}

data class Video(
    @BsonId val id: ObjectId = ObjectId(),
    val title: String,

    val lectureNumber: String,
    val thumbnailsTemplateId: ObjectId,
    val thumbnailsLectureNumber: String?,
    val customTitle: String?,

    val lessonId: ObjectId,
    val state: VideoState,
    val creationTime: Instant,

    val youtubeVideoId: String? = null,
    val vkVideoId: Int? = null,
    val vkStreamKey: String? = null,
)

enum class VideoState {
    New, Scheduled, LiveTest, Live, Recorded
}

data class LectureThumbnails(
    val fileName: String,
    val textColor: String,
)

enum class LectureBroadcastPrivacy {
    Public, Unlisted
}

data class LectureBroadcastScheduling(
    val startDay: Int,
    val startHour: Int,
    val startMinute: Int,
    val enableScheduling: Boolean = false,
    val enableAutoStart: Boolean = false,
    val enableAutoStop: Boolean = false,
    val streamKeyId: String? = null,
)

enum class LectureType {
    Lecture, Practice
}

data class Lecture(
    @BsonId val id: ObjectId = ObjectId(),
    val name: String,
    val title: String,
    val description: String,
    val currentLectureNumber: Int,
    val doubleNumeration: Boolean,
    val lectureType: LectureType = LectureType.Lecture,
    val playlistId: String? = null,
    val thumbnails: LectureThumbnails? = null,
    val scheduling: LectureBroadcastScheduling? = null,
    val privacy: LectureBroadcastPrivacy,
)
