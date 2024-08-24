package ru.kbats.youtube.broadcastscheduler.bot.dispatcher

import com.github.kotlintelegrambot.Bot
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
    fun ThumbnailsImage.infoMessage(): String = "Изображение для превью\n*${name.escapeMarkdown}*\n\n" +
            "[Image](${application.filesRepository.getThumbnailsImagePublicUrl(id.toString())})"

    callbackQuery("ThumbnailsImagesCmd") {
        bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id), text = "ThumbnailsImages",
            replyMarkup = InlineButtons.thumbnailsImagesMenu,
        )
    }

    inlineQuery {
        renderInlineListItems(
            "ThumbnailsImages", listOf(
                InlineQueryResult.Article(
                    id = "__add",
                    title = "Загрузить новое изображение для превью",
                    description = "",
                    inputMessageContent = InputMessageContent.Text("thumbnails_image__new")
                )
            )
        ) {
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
        val template = application.repository.getThumbnailsImage(id) ?: return@text
        if (application.userStates[message.chat.id] is UserState.Default) {
            bot.delete(message)
            bot.sendMessage(
                ChatId.fromId(message.chat.id),
                template.infoMessage(),
                parseMode = ParseMode.MARKDOWN_V2,
            )
        }
    }

    text {
        val chatId = ChatId.fromId(message.chat.id)
        val state = application.userStates[message.chat.id]
        if (state is UserState.CreatingThumbnailsImage && state.step == "Name") {
            state.prevMessagesIds.forEach { bot.deleteMessage(chatId, it) }
            bot.delete(message)
            val newMessage = bot.sendMessage(
                chatId,
                "Ok! Теперь отправьте изображение в качестве документа с соотношением сторон 7x12 (or 630x1080).\nИли отправьте /cancel, чтобы отменить."
            ).get()
            application.userStates[message.chat.id] = UserState.CreatingThumbnailsImage(
                "Image",
                state.image.copy(name = text),
                listOf(newMessage.messageId),
                state.prevState
            )
        }
    }

    document {
        val chatId = ChatId.fromId(message.chat.id)
        val state = application.userStates[message.chat.id]
        if (state is UserState.CreatingThumbnailsImage && state.step == "Image") {
            if (media.mimeType != "image/jpeg" && media.mimeType != "image/png") {
                val newMessage =
                    bot.sendMessage(chatId, "Incorrect type of document, please send jpg or png. Or send /cancel").get()
                application.userStates[message.chat.id] = UserState.CreatingThumbnailsImage(
                    "Image",
                    state.image,
                    state.prevMessagesIds + newMessage.messageId,
                    state.prevState
                )
                return@document
            }

            val file = bot.downloadFileBytes(media.fileId) ?: return@document
            val image = application.createThumbnailsImage(state.image.name, file)

            state.prevMessagesIds.forEach { bot.deleteMessage(chatId, it) }
            bot.delete(message)
            application.userStates[message.chat.id] = state.prevState

            if (image == null) {
                bot.sendMessage(chatId, "Failed to save ThumbnailsImage")
            } else {
                bot.sendMessage(
                    chatId,
                    image.infoMessage(),
                    parseMode = ParseMode.MARKDOWN_V2,
                    replyMarkup = InlineButtons.thumbnailsImagesManage(image, state.prevState)
                )
            }
        }
    }

    fun newThumbnailsImageAction(bot: Bot, chatId: Long) {
        val newMessage = bot.sendMessage(
            ChatId.fromId(chatId),
            "Чтобы добавить изображение для шаблона превью, отправьте название изображения, например, `Скаков П. С.` или `Процессор`",
            parseMode = ParseMode.MARKDOWN_V2,
        ).get()
        application.userStates[chatId] = UserState.CreatingThumbnailsImage(
            "Name",
            ThumbnailsImage(name = ""),
            listOf(newMessage.messageId),
            application.userStates[chatId]
        )
    }
    callbackQuery("ThumbnailsImagesNewCmd") {
        newThumbnailsImageAction(bot, callbackQuery.from.id)
    }
    text("thumbnails_image__new") {
        bot.delete(message)
        newThumbnailsImageAction(bot, message.chat.id)
    }
}

private suspend fun Application.createThumbnailsImage(name: String, file: ByteArray): ThumbnailsImage? {
    val image = Thumbnail.prepareThumbnailsImage(file)
    val repoImage = repository.insertThumbnailsImage(name) ?: return null

    filesRepository.insertThumbnailsImage(repoImage.id.toString(), image)

    return repoImage
}
