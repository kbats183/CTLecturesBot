package ru.kbats.youtube.broadcastscheduler.data

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

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

    // TODO: playlists
    // TODO: streamingSettings

    val currentLectureNumber: Int = 1,
    //type of numeration
) {
    fun nextLectureNumber() =
        if (doubleNumerationFormat) "$currentLectureNumber-${currentLectureNumber + 1}" else "$currentLectureNumber"

    fun titleTermNumber() = if (termNumber == "SC") "sc" else "s$termNumber"
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
