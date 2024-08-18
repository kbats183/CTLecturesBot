package ru.kbats.youtube.broadcastscheduler

import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId
import ru.kbats.youtube.broadcastscheduler.data.Admin
import ru.kbats.youtube.broadcastscheduler.data.Lecture

class Repository(db: MongoDatabase) {
    val admin = db.getCollection<Admin>("admin")
    val lecture = db.getCollection<Lecture>("lecture")

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
}

fun getRepository(config: Config): Repository {
    val client = MongoClient.create(connectionString = config.mongoDBConnectionString)
//    val client = KMongo.createClient(config.mongoDBConnectionString).coroutine
    val db = client.getDatabase(config.mongoDBBase)
    return Repository(db)
}
