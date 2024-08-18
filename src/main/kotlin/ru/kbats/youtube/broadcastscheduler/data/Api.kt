package ru.kbats.youtube.broadcastscheduler.data

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

data class Admin(@BsonId val id: ObjectId = ObjectId(), val login: String, val comment: String)

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
