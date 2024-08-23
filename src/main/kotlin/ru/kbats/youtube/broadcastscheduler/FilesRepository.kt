package ru.kbats.youtube.broadcastscheduler

import org.bson.types.ObjectId
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

class FilesRepository(private val config: Config) {
    private val path = Path.of(".").resolve("filesRepository")
    private val thumbnailsImages = path.resolve("thumbnailsImages")
    private val thumbnailsTemplates = path.resolve("thumbnailsTemplates")

    init {
        thumbnailsImages.createDirectories()
        thumbnailsTemplates.createDirectories()
    }

    fun insertThumbnailsImage(id: String, content: ByteArray) {
        getThumbnailsImagePath(id).writeBytes(content)
    }

    fun getThumbnailsImagePublicUrl(id: String) = config.publicFilesUrl + "/thumbnailsImages/$id.png"
    fun getThumbnailsImagePath(id: String) = thumbnailsImages.resolve("$id.png")

    fun getThumbnailsTemplatePublicUrl(id: ObjectId) = config.publicFilesUrl + "/thumbnailsTemplates/$id.png"
    fun getThumbnailsTemplatePath(id: ObjectId) = thumbnailsTemplates.resolve("$id.png")
}
