package ru.kbats.youtube.broadcastscheduler

import com.mongodb.MongoClientSettings
import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Instant
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistries
import org.bson.types.ObjectId
import ru.kbats.youtube.broadcastscheduler.data.*
import kotlin.random.Random

class Repository(db: MongoDatabase) {
    val admin = db.getCollection<Admin>("admin")
    val lecture = db.getCollection<Lecture>("lecture")
    private val thumbnailsImage = db.getCollection<ThumbnailsImage>("thumbnailsImage")
    private val thumbnailsTemplate = db.getCollection<ThumbnailsTemplate>("thumbnailsTemplate")
    private val lesson = db.getCollection<Lesson>("lesson")
    private val videos = db.getCollection<Video>("video")
    private val restreamerKeys = db.getCollection<StreamKey.Restreamer>("restreamerKey")

    suspend fun getAdmins(): List<Admin> {
        return admin.find().toList()
    }

    suspend fun addAdmin(login: String, commentary: String) {
        admin.insertOne(Admin(login = login, comment = commentary))
    }

    suspend fun getLectures(): List<Lecture> {
        return lecture.find().toList().sortedBy { it.name }
    }

    suspend fun getLecture(id: String): Lecture? {
        return lecture.find(eq("_id", ObjectId(id))).firstOrNull()
    }

    suspend fun updateLecture(id: String, mutator: (Lecture) -> Lecture) {
        val l = getLecture(id) ?: return
        lecture.replaceOne(eq("_id", l.id), mutator(l))
    }

    suspend fun getThumbnailsImages(): List<ThumbnailsImage> {
        return thumbnailsImage.find().toList().sortedBy { it.name }
    }

    suspend fun getThumbnailsImage(id: String): ThumbnailsImage? {
        return thumbnailsImage.find(eq("_id", ObjectId(id))).firstOrNull()
    }

    suspend fun insertThumbnailsImage(name: String): ThumbnailsImage? {
        val r = thumbnailsImage.insertOne(ThumbnailsImage(name = name)).insertedId ?: return null
        return thumbnailsImage.find(eq("_id", r)).firstOrNull()
    }

    suspend fun getThumbnailsTemplates(): List<ThumbnailsTemplate> {
        return thumbnailsTemplate.find().toList().sortedBy { it.name }
    }

    suspend fun getThumbnailsTemplate(id: String): ThumbnailsTemplate? {
        return thumbnailsTemplate.find(eq("_id", ObjectId(id))).firstOrNull()
    }

    suspend fun insertThumbnailsTemplate(template: ThumbnailsTemplate): ThumbnailsTemplate? {
        val r = thumbnailsTemplate.insertOne(template).insertedId ?: return null
        return thumbnailsTemplate.find(eq("_id", r)).firstOrNull()
    }

    suspend fun replaceThumbnailsTemplate(template: ThumbnailsTemplate): Boolean {
        val r = thumbnailsTemplate.replaceOne(eq("_id", template.id), template)
        return r.matchedCount == 1L
    }

    suspend fun getLessons(): List<Lesson> {
        return lesson.find().toList().sortedBy { it.name }
    }

    suspend fun getLesson(id: String): Lesson? {
        return lesson.find(eq("_id", ObjectId(id))).firstOrNull()
    }

    suspend fun insertLesson(l: Lesson): Lesson? {
        val r = lesson.insertOne(l).insertedId ?: return null
        return lesson.find(eq("_id", r)).firstOrNull()
    }

    suspend fun replaceLesson(l: Lesson): Boolean {
        val r = lesson.replaceOne(eq("_id", l.id), l)
        return r.matchedCount == 1L
    }

    suspend fun getVideosByLesson(lessonId: String): List<Video> {
        return videos.find(eq("lessonId", ObjectId(lessonId))).toList().sortedBy { it.creationTime }
    }

    suspend fun getVideo(id: String): Video? {
        return videos.find(eq("_id", ObjectId(id))).firstOrNull()
    }

    suspend fun insertVideo(video: Video): Video? {
        val r = videos.insertOne(video).insertedId ?: return null
        return videos.find(eq("_id", r)).firstOrNull()
    }

    suspend fun replaceVideo(video: Video): Boolean {
        val r = videos.replaceOne(eq("_id", video.id), video)
        return r.matchedCount == 1L
    }

    suspend fun genRestreamerKey(): StreamKey.Restreamer {
        while (true) {
            val name = Random.Default.nextInt(10000, 99999).toString()
            if (restreamerKeys.find().toList().any { it.name == name }) continue

            val key = StreamKey.Restreamer(name = name)
            val id = requireNotNull(restreamerKeys.insertOne(key).insertedId) { "Failed to insert $key into mongo" }
            return restreamerKeys.find(eq("_id", id)).first()
        }
    }

    suspend fun replaceRestreamerKey(key: StreamKey.Restreamer): Boolean {
        val r = restreamerKeys.replaceOne(eq("_id",key.id), key)
        return r.matchedCount == 1L
    }
}

fun getRepository(config: Config): Repository {
    val client = MongoClient.create(connectionString = config.mongoDBConnectionString)
//    val client = KMongo.createClient(config.mongoDBConnectionString).coroutine
    val db = client.getDatabase(config.mongoDBBase)
        .withCodecRegistry(codecRegistry)
    return Repository(db)
}

private val codecRegistry = CodecRegistries.fromRegistries(
    CodecRegistries.fromCodecs(InstantCodec()),
    MongoClientSettings.getDefaultCodecRegistry()
)

private class InstantCodec : Codec<Instant> {
    override fun encode(writer: BsonWriter, value: Instant, encoderContext: EncoderContext) {
        writer.writeDateTime(value.toEpochMilliseconds())
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): Instant {
        return Instant.fromEpochMilliseconds(reader.readDateTime())
    }

    override fun getEncoderClass(): Class<Instant> {
        return Instant::class.java
    }
}

