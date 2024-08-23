package ru.kbats.youtube.broadcastscheduler.thumbnail

import org.bson.types.ObjectId
import ru.kbats.youtube.broadcastscheduler.Application
import ru.kbats.youtube.broadcastscheduler.config
import ru.kbats.youtube.broadcastscheduler.data.LectureThumbnails
import ru.kbats.youtube.broadcastscheduler.data.ThumbnailsTemplate
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.isReadable
import kotlin.io.path.outputStream
import kotlin.math.min


object Thumbnail {
    private const val fontSize = 210
    private val fontInputStream = Thumbnail::class.java.classLoader.getResourceAsStream("Golos-Text_Bold.ttf")
    private val font = Font.createFont(Font.TRUETYPE_FONT, fontInputStream)

    fun prepareThumbnailsImage(input: ByteArray): ByteArray {
        val image = ImageIO.read(ByteArrayInputStream(input))
        val minHeight = min(image.height, (image.width.toDouble() * 12 / 7).toInt())
        val minWidth = min(image.width, (minHeight.toDouble() * 7 / 12).toInt())
        val xPos = (image.width - minWidth) / 2
        val yPos = (image.height - minHeight) / 2

        val output = ByteArrayOutputStream()
        ImageIO.write(image.getSubimage(xPos, yPos, minWidth, minHeight), "png", output)
        return output.toByteArray()
    }

    fun generateTemplate(template: ThumbnailsTemplate, imagePath: Path?, outputPath: Path) {
        val image = BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB)
        val graphics = image.graphics
        val color = getColor(template.color)

        graphics.color = color
        graphics.fillRoundRect(344, 73, 141, 83, 22, 22)

        graphics.color = Color.BLACK
        graphics.font = font.deriveFont(64.toFloat())
        graphics.drawString("КТ", 370, 137)

        graphics.color = Color.WHITE
        graphics.font = font.deriveFont(64.toFloat())
        graphics.drawString("ИТМО", 115, 137)


        graphics.color = Color.WHITE
        graphics.font = font.deriveFont(110.toFloat())
        graphics.drawString(template.firstTitle, 115, 418)

        graphics.color = color
        graphics.font = font.deriveFont(110.toFloat())
        graphics.drawString(template.secondTitle, 115, 543)

        graphics.color = Color.WHITE
        graphics.font = font.deriveFont(60.toFloat())
        graphics.drawString(template.lecturerName, 115, 788)

        graphics.font = font.deriveFont(fontSize.toFloat())
        graphics.color = Color.WHITE
        graphics.drawString(template.termNumber ?: "", 115, 992)

        graphics.font = font.deriveFont(fontSize.toFloat())
        graphics.color = color
        graphics.drawString("L2", 439, 992)

        imagePath?.let {
            val img = ImageIO.read(imagePath.toFile())
            graphics.drawImage(img, 1290, 0, 630, 1080, null)
        }

        outputPath.outputStream().use { ImageIO.write(image, "png", it) }
    }

    fun generate(templateDir: Path, thumbnails: LectureThumbnails, lectureTitleNumber: String): File {
        val thumbnailsPath = templateDir.resolve(thumbnails.fileName)
        if (!thumbnailsPath.isReadable()) {
            throw ThumbnailGenerationException("No thumbnails template file $thumbnailsPath")
        }
        val thumbnailsGeneratePath = thumbnailsPath.resolveSibling("generate")
        Files.createDirectories(thumbnailsGeneratePath)
        val outFilePath = thumbnailsGeneratePath.resolve(thumbnails.fileName)
        generate(
            thumbnailsPath,
            getColor(thumbnails.textColor),
            lectureTitleNumber,
            outFilePath,
        )
        return outFilePath.toFile()
    }

    fun generate(
        templateFile: Path,
        color: Color?,
        text: String,
        outFilePath: Path,
        positionX: Int = 439,
        positionY: Int = 992
    ) {
        val image: BufferedImage = ImageIO.read(Files.newInputStream(templateFile))
        val graphics = image.graphics
        graphics.font = font.deriveFont(fontSize.toFloat())
        graphics.color = color ?: Color.WHITE
        graphics.drawString(text, positionX, positionY)
        ImageIO.createImageInputStream(image) // TODO: what?
        ImageIO.write(image, "png", outFilePath.toFile())
    }

    fun getColor(string: String): Color? {
        try {
            return Color.decode(
                "0x" + when (string) {
                    "tart" -> "f9393f"
                    "honey" -> "ffcc33"
                    "yellow" -> "ffff33"
                    "green" -> "d0ff14"
                    "capri" -> "00ccff"
                    "bluetiful" -> "0b68fe"
                    "violet" -> "7f00ff"
                    "pink" -> "fc74fd"
                    else -> string
                }
            )
        } catch (e: NumberFormatException) {
            return null
        }
    }

    class ThumbnailGenerationException(override val message: String) : Exception()
}

fun main() {
//    val fileName = "math_stat_3338-9 (3).png"
//    Thumbnail.generate(Path.of(fileName), Color.decode("0xffee00"), "P1", Path.of("gen_$fileName"))
    val application = Application(config())
    val template = ThumbnailsTemplate(
        name = "Математический анализ. КП Кохась. M3238-9",
        firstTitle = "Математический",
        secondTitle = "анализ",
        lecturerName = "Константин Кохась",
        termNumber = "S4",
        color = "yellow",
        imageId = ObjectId("66c31424011835161eef8b65"),
    )
    Thumbnail.generateTemplate(
        template,
        application.filesRepository.getThumbnailsImagePath("66c31424011835161eef8b65"),
        Path.of("filesRepository/test.png")
    )
}
