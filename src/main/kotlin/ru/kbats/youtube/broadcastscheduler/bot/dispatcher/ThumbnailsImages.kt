package ru.kbats.youtube.broadcastscheduler.bot.dispatcher

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.inlinequeryresults.InlineQueryResult
import com.github.kotlintelegrambot.entities.inlinequeryresults.InputMessageContent
import ru.kbats.youtube.broadcastscheduler.Application
import ru.kbats.youtube.broadcastscheduler.bot.*
import ru.kbats.youtube.broadcastscheduler.data.ThumbnailsImage
import ru.kbats.youtube.broadcastscheduler.states.UserState
import ru.kbats.youtube.broadcastscheduler.thumbnail.Thumbnail

fun AdminDispatcher.setupThumbnailsImagesDispatcher() {
    fun ThumbnailsImage.infoMessage(): String = "Изображение для превью ${id}\n" +
            "Name: ${name.escapeMarkdown}\n" +
            "[Image](${application.filesRepository.getThumbnailsImagePublicUrl(id.toString())})"

    callbackQuery("ThumbnailsImagesCmd") {
        bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id), text = "ThumbnailsImages",
            replyMarkup = InlineButtons.thumbnailsImagesMenu,
        )
    }

    inlineQuery {
        renderInlineListItems("ThumbnailsImages", listOf(addThumbnailsImageInlineResult)) {
            application.repository.getThumbnailsImages()
                .map {
                    InlineQueryResult.Article(
                        id = "thumbnails_image_${it.id}",
                        thumbUrl = application.filesRepository.getThumbnailsImagePublicUrl(it.id.toString()),
                        title = it.name,
                        description = "",
                        inputMessageContent = InputMessageContent.Text("thumbnails_image_${it.id}"),
                        replyMarkup = null,
                    )
                }
        }
    }

    text("thumbnails_image_") {
        val id = message.text?.let { thumbnailsImageIdRegexp.matchEntire(it) }?.groups?.get(1)?.value ?: return@text
        val template = application.repository.getThumbnailsImages(id) ?: return@text
        if (application.userStates[message.chat.id] is UserState.Default) {
            bot.delete(message)
            bot.sendMessage(
                ChatId.fromId(message.chat.id),
                template.infoMessage(),
                parseMode = ParseMode.MARKDOWN_V2,
            )
        }
    }


    callbackQuery("ThumbnailsImagesNewCmd") {
        application.userStates[callbackQuery.from.id] = UserState.CreatingThumbnailsImage
    }

    text {
        val chatId = ChatId.fromId(message.chat.id)
        when (val state = application.userStates[message.chat.id]) {
            is UserState.CreatingThumbnailsImage -> {
                val name = message.text
                if (name == null) {
                    bot.sendMessage(
                        chatId,
                        "Please, send only text message with name of Thumbnails Photo, or send /cancel"
                    )
                    return@text
                }
                bot.sendMessage(
                    chatId,
                    "Ok! Then send you thumbnails image as a document with aspect ratio 7x12 (or 630x1080).\nOr send /cancel to cancel."
                )
                application.userStates[message.chat.id] = UserState.CreatingThumbnailsImage2(name)
            }

            else -> {}
        }
    }

    document {
        val chatId = ChatId.fromId(message.chat.id)
        when (val state = application.userStates[message.chat.id]) {
            is UserState.CreatingThumbnailsImage2 -> {
                val document = message.document
                println("photo=${document}")
                if (document == null) {
                    bot.sendMessage(
                        chatId,
                        "You didn't send photo, please send it or send /cancel to cancel."
                    )
                } else {
                    val doc = message.document ?: return@document
                    if (doc.mimeType != "image/jpeg" && doc.mimeType != "image/png") {
                        bot.sendMessage(chatId, "Incorrect type of document, please send jpg or png. Or send /cancel")
                    }
                    val file = bot.downloadFileBytes(doc.fileId) ?: return@document

                    val image = application.createThumbnailsImage(state.name, file)
                    application.userStates[message.chat.id] = UserState.Default

                    if (image == null) {
                        bot.sendMessage(chatId, "Failed to save ThumbnailsImage")
                    } else {
                        bot.sendMessage(chatId, image.infoMessage(), parseMode = ParseMode.MARKDOWN_V2)
                    }
                }
            }

            else -> {
//                application.createThumbnailsImage("test", image)
            }
        }
    }

}

private suspend fun Application.createThumbnailsImage(name: String, file: ByteArray): ThumbnailsImage? {
    val image = Thumbnail.prepareThumbnailsImage(file)
    val repoImage = repository.insertThumbnailsImage(name) ?: return null

    filesRepository.insertThumbnailsImage(repoImage.id.toString(), image)

    return repoImage
}
