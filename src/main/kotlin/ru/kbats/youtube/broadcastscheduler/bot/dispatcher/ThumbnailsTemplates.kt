package ru.kbats.youtube.broadcastscheduler.bot.dispatcher

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineQuery
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.inlinequeryresults.InlineQueryResult
import com.github.kotlintelegrambot.entities.inlinequeryresults.InputMessageContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.bson.types.ObjectId
import ru.kbats.youtube.broadcastscheduler.bot.*
import ru.kbats.youtube.broadcastscheduler.bot.delete
import ru.kbats.youtube.broadcastscheduler.data.ThumbnailsTemplate
import ru.kbats.youtube.broadcastscheduler.states.UserState
import ru.kbats.youtube.broadcastscheduler.thumbnail.Thumbnail
import ru.kbats.youtube.broadcastscheduler.withUpdateUrlSuffix

fun AdminDispatcher.setupThumbnailsTemplatesDispatcher() {
    fun ThumbnailsTemplate.infoMessage(): String = "Шаблон для превью *${name.escapeMarkdown}*\n" +
            "${firstTitle.escapeMarkdown}\n" +
            "${secondTitle.escapeMarkdown}\n" +
            "Лектор: ${lecturerName.escapeMarkdown}\n" +
            "Семестр: ${termNumber.escapeMarkdown}\n" +
            "Цвет: ${color.escapeMarkdown}\n" +
            "[Превью](${application.filesRepository.getThumbnailsTemplatePublicUrl(id).withUpdateUrlSuffix()})"

    callbackQuery("ThumbnailsTemplatesCmd") {
        bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id),
            text = "ThumbnailsTemplates\n",
            replyMarkup = InlineButtons.thumbnailsTemplatesMenu,
        )
    }

    inlineQuery {
        renderInlineListItems("ThumbnailsTemplates") {
            application.repository.getThumbnailsTemplates().map {
                InlineQueryResult.Article(
                    id = "thumbnails_template_${it.id}",
                    thumbUrl = application.filesRepository.getThumbnailsTemplatePublicUrl(it.id),
                    title = it.name,
                    description = "",
                    inputMessageContent = InputMessageContent.Text("thumbnails_template_${it.id}")
                )
            }
        }
    }

    text("thumbnails_template_") {
        val id = message.text?.let { thumbnailsTemplateIdRegexp.matchEntire(it) }?.groups?.get(1)?.value ?: return@text
        bot.delete(message)
        val template = application.repository.getThumbnailsTemplate(id) ?: return@text
        bot.sendMessage(
            ChatId.fromId(message.chat.id),
            template.infoMessage(),
            parseMode = ParseMode.MARKDOWN_V2,
            replyMarkup = InlineButtons.thumbnailsTemplateManage(template)
        )
    }

    callbackQuery("ThumbnailsTemplatesItemEdit") {
        val chatId = ChatId.fromId(callbackQuery.from.id)
        val (op, id) = callbackQueryId("ThumbnailsTemplatesItemEdit")?.split("Cmd")
            ?.takeIf { it.size == 2 }
            ?: return@callbackQuery

        val oldTemplate = application.repository.getThumbnailsTemplate(id)
        oldTemplate ?: return@callbackQuery
        val (text, keyboard) = when (op) {
            "Name" -> "Напишите новое название шаблона для превью видео или отправьте /cancel\\.\n" +
                    "Текущее название, `" + oldTemplate.name.escapeMarkdown + "`" to null

            "Title" -> "Напишите новый заголовок для превью видео, состоящий из двух строк, или отправьте /cancel\\.\n" +
                    "Текущий заголовок: `${oldTemplate.firstTitle.escapeMarkdown}\n" +
                    "${oldTemplate.secondTitle.escapeMarkdown}`" to null

            "Lecturer" -> "Напишите новое имея лектора для превью видео или отправьте /cancel\\.\n" +
                    "Текущий лектор: `${oldTemplate.lecturerName.escapeMarkdown}`" to null

            "Term" -> "Напишите новый номер семестра для превью видео или отправьте /cancel\\.\n" +
                    "Текущий номер семестра: `${oldTemplate.termNumber.escapeMarkdown}`" to null

            "Color" -> "Напишите новый цвет для превью видео или отправьте /cancel\\.\n" +
                    "Текущий цвет: `${oldTemplate.color.escapeMarkdown}`\n" + colorsTgExample to null

            "Image" ->
                "Выберите изображение для превью видео или отправьте /cancel" to InlineButtons.thumbnailsTemplateEditImage

            else -> return@callbackQuery
        }

        val newMessage = bot.sendMessage(chatId, text, ParseMode.MARKDOWN_V2, replyMarkup = keyboard).getOrNull()
        application.userStates[callbackQuery.from.id] =
            UserState.EditingThumbnailsTemplate(
                id,
                op,
                listOfNotNull(callbackQuery.message?.messageId, newMessage?.messageId)
            )
    }

    callbackQuery("ThumbnailsTemplatesNewCmd") {
        val chatId = ChatId.fromId(callbackQuery.from.id)
        val newMessage = bot.sendMessage(
            chatId,
            "Чтобы добавить шаблон превью, напишите название шаблона\\.\n" +
                    "Например, _" + "Математический анализ. К. П. Кохась. M3238-9".escapeMarkdown + "_",
            ParseMode.MARKDOWN_V2
        ).getOrNull()
        application.userStates[callbackQuery.from.id] = UserState.CreatingThumbnailsTemplate(newMessage?.messageId)
    }

    text {
        val chatId = ChatId.fromId(message.chat.id)
        when (val state = application.userStates[message.chat.id]) {
            is UserState.CreatingThumbnailsTemplate -> {
                val name = message.text
                if (name == null) {
                    bot.sendMessage(chatId, "Пожалуйста, отправьте название шаблона превью или отпарьте /cancel")
                    return@text
                }
                state.prevMessageId?.let { bot.deleteMessage(chatId, it) }
                bot.delete(message)
                val newMessage = bot.sendMessage(
                    chatId,
                    "Ok\\! Теперь отправьте две строки заголовка шаблона\\. Например,\n" +
                            "__Математический\n" +
                            "анализ__",
                    parseMode = ParseMode.MARKDOWN_V2
                ).getOrNull()
                application.userStates[message.chat.id] =
                    UserState.CreatingThumbnailsTemplate2(name, newMessage?.messageId)
            }

            is UserState.CreatingThumbnailsTemplate2 -> {
                val parts = message.text?.split("\n")
                if (parts == null || parts.size != 2) {
                    bot.sendMessage(chatId, "Пожалуйста, отправьте две строки или /cancel")
                    return@text
                }
                state.prevMessageId?.let { bot.deleteMessage(chatId, it) }
                bot.delete(message)

                val newMessage = bot.sendMessage(
                    chatId,
                    "Ok\\! Теперь отправьте имя лектора, который ведет лекцию\\. Например, __Никита Голиков__ или __К\\. П\\. Кохась__",
                    parseMode = ParseMode.MARKDOWN_V2
                ).getOrNull()
                application.userStates[message.chat.id] =
                    UserState.CreatingThumbnailsTemplate3(state.name, parts[0], parts[1], newMessage?.messageId)
            }

            is UserState.CreatingThumbnailsTemplate3 -> {
                val lecturerName = message.text
                if (lecturerName == null) {
                    bot.sendMessage(chatId, "Пожалуйста, отправьте имя лектора или отправьте /cancel")
                    return@text
                }
                state.prevMessageId?.let { bot.deleteMessage(chatId, it) }
                bot.delete(message)
                val newMessage = bot.sendMessage(
                    chatId,
                    "Ok\\! Теперь отправьте номер семестра, в котором записывается курс\\. Например, __S1__, __S8__ или __SC__ \\(курс по выбору\\)",
                    parseMode = ParseMode.MARKDOWN_V2
                ).getOrNull()
                application.userStates[message.chat.id] =
                    UserState.CreatingThumbnailsTemplate4(
                        state.name,
                        state.firstLine,
                        state.secondLine,
                        lecturerName,
                        newMessage?.messageId
                    )
            }

            is UserState.CreatingThumbnailsTemplate4 -> {
                val termNumber = message.text
                if (termNumber == null) {
                    bot.sendMessage(chatId, "Пожалуйста, отправьте имя лектора или отправьте /cancel")
                    return@text
                }
                state.prevMessageId?.let { bot.deleteMessage(chatId, it) }
                bot.delete(message)

                val newMessage = bot.sendMessage(
                    chatId,
                    "Ok\\! Теперь отправьте цвет текста в шаблоне\\. Например, \n" +
                            colorsTgExample,
                    parseMode = ParseMode.MARKDOWN_V2
                ).also { println(it) }.getOrNull()
                application.userStates[message.chat.id] =
                    UserState.CreatingThumbnailsTemplate5(
                        state.name,
                        state.firstLine,
                        state.secondLine,
                        state.bottomLine,
                        termNumber,
                        newMessage?.messageId
                    )
            }

            is UserState.CreatingThumbnailsTemplate5 -> {
                val color = message.text
                if (color == null) {
                    bot.sendMessage(chatId, "Пожалуйста, отправьте цвет шаблона или отправьте /cancel")
                    return@text
                }
                state.prevMessageId?.let { bot.deleteMessage(chatId, it) }
                bot.delete(message)

                val template = application.repository.insertThumbnailsTemplate(
                    ThumbnailsTemplate(
                        name = state.name,
                        firstTitle = state.firstLine,
                        secondTitle = state.secondLine,
                        lecturerName = state.bottomLine,
                        termNumber = state.termNumber,
                        color = color,
                    )
                )
                if (template == null) {
                    bot.sendMessage(chatId, "Не получилось создать шаблон превью трансляции")
                    return@text
                }

                Thumbnail.generateTemplate(
                    template,
                    null,
                    application.filesRepository.getThumbnailsTemplatePath(template.id)
                )
                bot.sendMessage(
                    chatId,
                    template.infoMessage(),
                    parseMode = ParseMode.MARKDOWN_V2,
                    replyMarkup = InlineButtons.thumbnailsTemplateManage(template)
                )
            }

            is UserState.EditingThumbnailsTemplate -> {
                val text = message.text ?: return@text
                val oldTemplate = application.repository.getThumbnailsTemplate(state.id) ?: return@text
                val template = when (state.op) {
                    "Name" -> oldTemplate.copy(name = text)
                    "Title" -> {
                        val parts = text.split("\n")
                        if (parts.size != 2) {
                            bot.sendMessage(chatId, "Отправьте две строки")
                        }
                        oldTemplate.copy(firstTitle = parts[0], secondTitle = parts[1])
                    }

                    "Lecturer" -> oldTemplate.copy(lecturerName = text)
                    "Term" -> oldTemplate.copy(termNumber = text)
                    "Color" -> oldTemplate.copy(color = text)

                    "Image" -> {
                        val id = message.text?.let { thumbnailsImageIdRegexp.matchEntire(it) }?.groups?.get(1)?.value ?: return@text
                        val template = application.repository.getThumbnailsImages(id) ?: return@text
                        oldTemplate.copy(imageId = ObjectId(id))
                    }

                    else -> return@text
                }

                application.userStates[message.chat.id] = UserState.Default
                val successUpdate = application.repository.replaceThumbnailsTemplate(template)
                if (!successUpdate) {
                    bot.sendMessage(chatId, "Не удалось изменить шаблон превью")
                    return@text
                }
                val newTemplate = application.repository.getThumbnailsTemplate(template.id.toString()) ?: return@text
                Thumbnail.generateTemplate(
                    newTemplate,
                    newTemplate.imageId?.let { application.filesRepository.getThumbnailsImagePath(it.toString()) },
                    application.filesRepository.getThumbnailsTemplatePath(template.id)
                )
                state.prevMessagesIds.forEach { bot.deleteMessage(chatId, it) }
                bot.delete(message)
                bot.sendMessage(
                    chatId,
                    newTemplate.infoMessage(),
                    parseMode = ParseMode.MARKDOWN_V2,
                    replyMarkup = InlineButtons.thumbnailsTemplateManage(newTemplate)
                )
            }

            else -> {}
        }
    }
}


//private suspend fun Application.createThumbnailsImage(name: String, file: ByteArray): ThumbnailsImage? {
//    val image = Thumbnail.prepareThumbnailsImage(file)
//    val repoImage = repository.insertThumbnailsImage(name) ?: return null
//
//    filesRepository.insertThumbnailsImage(repoImage.id.toString(), image)
//
//    return repoImage
//}
