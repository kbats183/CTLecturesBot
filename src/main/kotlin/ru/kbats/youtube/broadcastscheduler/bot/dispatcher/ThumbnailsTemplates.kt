package ru.kbats.youtube.broadcastscheduler.bot.dispatcher

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.inlinequeryresults.InlineQueryResult
import com.github.kotlintelegrambot.entities.inlinequeryresults.InputMessageContent
import ru.kbats.youtube.broadcastscheduler.bot.*
import ru.kbats.youtube.broadcastscheduler.data.ThumbnailsTemplate
import ru.kbats.youtube.broadcastscheduler.states.UserState
import ru.kbats.youtube.broadcastscheduler.thumbnail.Thumbnail
import ru.kbats.youtube.broadcastscheduler.withUpdateUrlSuffix
import kotlin.random.Random

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
        if (application.userStates[message.chat.id] is UserState.Default) {
            val id =
                message.text?.let { thumbnailsTemplateIdRegexp.matchEntire(it) }?.groups?.get(1)?.value ?: return@text
            bot.delete(message)
            val template = application.repository.getThumbnailsTemplate(id) ?: return@text
            bot.sendMessage(
                ChatId.fromId(message.chat.id),
                template.infoMessage(),
                parseMode = ParseMode.MARKDOWN_V2,
                replyMarkup = InlineButtons.thumbnailsTemplateManage(template, application.userStates[message.chat.id])
            )
        }
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
                    "Текущий цвет: `${oldTemplate.color.escapeMarkdown}`\n" + itmoColorsTgExample to null

            "Image" ->
                "Выберите изображение для превью видео или отправьте /cancel" to InlineButtons.thumbnailsTemplateEditImage

            else -> return@callbackQuery
        }

        val newMessage = bot.sendMessage(chatId, text, ParseMode.MARKDOWN_V2, replyMarkup = keyboard).getOrNull()
        application.userStates[callbackQuery.from.id] =
            UserState.EditingThumbnailsTemplate(
                id,
                op,
                listOfNotNull(callbackQuery.message?.messageId, newMessage?.messageId),
                application.userStates[callbackQuery.from.id],
            )
    }

    callbackQuery("ThumbnailsTemplatesNewCmd") {
        val chatId = ChatId.fromId(callbackQuery.from.id)
        val newMessage = bot.sendMessage(
            chatId,
            "Чтобы добавить курс, напишите короткое название курса\\.\n" +
                    "Например, `${"1.MathAn38-39".escapeMarkdown}` или `${"2.OS.Hard".escapeMarkdown}`",
            ParseMode.MARKDOWN_V2
        ).getOrNull()
        application.userStates[callbackQuery.from.id] = UserState.CreatingThumbnailsTemplate(
            "Name",
            ThumbnailsTemplate(
                name = "",
                firstTitle = "",
                secondTitle = "",
                lecturerName = "",
                termNumber = "",
                color = ""
            ),
            newMessage?.messageId,
            application.userStates[callbackQuery.from.id],
        )
    }

    suspend fun updateTemplate(
        template: ThumbnailsTemplate,
        bot: Bot,
        message: Message,
        prevMessagesIds: List<Long>,
        prevState: UserState
    ) {
        val chatId = ChatId.fromId(message.chat.id)
        val successUpdate = application.repository.replaceThumbnailsTemplate(template)
        if (!successUpdate) {
            bot.sendMessage(chatId, "Не удалось изменить шаблон превью")
            return
        }
        val newTemplate = application.repository.getThumbnailsTemplate(template.id.toString()) ?: return
        Thumbnail.generateTemplate(
            newTemplate,
            newTemplate.imageId?.let { application.filesRepository.getThumbnailsImagePath(it.toString()) },
            application.filesRepository.getThumbnailsTemplatePath(template.id)
        )
        prevMessagesIds.forEach { bot.deleteMessage(chatId, it) }
        bot.delete(message)
        bot.sendMessage(
            chatId,
            newTemplate.infoMessage(),
            parseMode = ParseMode.MARKDOWN_V2,
            replyMarkup = InlineButtons.thumbnailsTemplateManage(newTemplate, prevState)
        )
    }

    text {
        val chatId = ChatId.fromId(message.chat.id)
        when (val state = application.userStates[message.chat.id]) {
            is UserState.CreatingThumbnailsTemplate -> {
                val (newTemplate, nextStep, nextText) = when (state.step) {
                    "Name" -> Triple(
                        state.template.copy(name = text),
                        "Title",
                        "Ok\\! Теперь отправьте две строки заголовка шаблона\\. Например,\n" +
                                "`Математический\nанализ`"
                    )

                    "Title" -> {
                        val parts = text.split("\n")
                        if (parts.size != 2) {
                            Triple(
                                state.template,
                                "Title",
                                "Пожалуйста, отправьте две строки с заголовком шаблона или /cancel"
                            )
                        } else {
                            Triple(
                                state.template.copy(firstTitle = parts[0], secondTitle = parts[1]),
                                "LecturerName",
                                "Ok\\! Теперь отправьте имя лектора, который ведет лекцию\\. Например, `Никита Голиков` или `К\\. П\\. Кохась`"
                            )
                        }

                    }

                    "LecturerName" -> Triple(
                        state.template.copy(lecturerName = text),
                        "TermNumber",
                        "Ok\\! Теперь отправьте номер семестра, в котором записывается курс\\. Например, `S1`, `S8` или `SC` \\(курс по выбору\\)"
                    )

                    "TermNumber" -> Triple(
                        state.template.copy(termNumber = text),
                        "Color",
                        "Ok\\! Теперь отправьте цвет текста в шаблоне\\. Например, \n" +
                                itmoColorsTgExample
                    )

                    "Color" -> Triple(state.template.copy(color = text), "Finish", "")

                    else -> return@text
                }

                state.prevMessageId?.let { bot.deleteMessage(chatId, it) }
                bot.delete(message)

                if (nextStep == "Finish") {
                    val created = application.repository.insertThumbnailsTemplate(newTemplate)
                    if (created == null) {
                        bot.sendMessage(
                            chatId,
                            "Не получилось создать новый шаблон превью",
                            parseMode = ParseMode.MARKDOWN_V2
                        ).get()
                    } else {
                        Thumbnail.generateTemplate(
                            newTemplate,
                            newTemplate.imageId?.let { application.filesRepository.getThumbnailsImagePath(it.toString()) },
                            application.filesRepository.getThumbnailsTemplatePath(created.id)
                        )
                        bot.sendMessage(
                            chatId,
                            created.infoMessage(),
                            parseMode = ParseMode.MARKDOWN_V2,
                            replyMarkup = InlineButtons.thumbnailsTemplateManage(created, state.prevState)
                        ).get()
                    }
                    application.userStates[message.chat.id] = state.prevState
                } else {
                    val newMessage = bot.sendMessage(
                        chatId,
                        nextText,
                        parseMode = ParseMode.MARKDOWN_V2
                    ).get()
                    application.userStates[message.chat.id] =
                        UserState.CreatingThumbnailsTemplate(
                            nextStep,
                            newTemplate,
                            newMessage.messageId,
                            state.prevState
                        )
                }
            }

            is UserState.EditingThumbnailsTemplate -> {
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
                        val id = message.text?.let { thumbnailsImageIdRegexp.matchEntire(it) }?.groups?.get(1)?.value
                            ?: return@text
                        val img = application.repository.getThumbnailsImage(id) ?: return@text
                        oldTemplate.copy(imageId = img.id)
                    }

                    else -> return@text
                }

                application.userStates[message.chat.id] = state.prevState
                updateTemplate(template, bot, message, state.prevMessagesIds, state.prevState)
            }

            else -> {}
        }
    }

    callbackQuery("ThumbnailsImageItemBackCmd") {
        val state = application.userStates[callbackQuery.from.id]
        if (state is UserState.EditingThumbnailsTemplate && state.op == "Image") {
            val imageId =
                callbackQueryId("ThumbnailsImageItemBackCmd")?.let { application.repository.getThumbnailsImage(it)?.id }
                    ?: return@callbackQuery
            val oldTemplate = application.repository.getThumbnailsTemplate(state.id) ?: return@callbackQuery
            val message = callbackQuery.message ?: return@callbackQuery
            updateTemplate(oldTemplate.copy(imageId = imageId), bot, message, state.prevMessagesIds, state.prevState)
            application.userStates[callbackQuery.from.id] = state.prevState
        }
    }

    callbackQuery("LessonsEditThumbnailsTemplateEditCmd") {
        val state = application.userStates[callbackQuery.from.id]
        if (state is UserState.ChoosingLessonThumbnailsTemplate) {
            val templateId =
                application.repository.getLesson(state.lessonId)?.mainTemplateId?.toString() ?: return@callbackQuery
            val template = application.repository.getThumbnailsTemplate(templateId) ?: return@callbackQuery
            val newMessage = bot.sendMessage(
                ChatId.fromId(callbackQuery.from.id),
                template.infoMessage(),
                parseMode = ParseMode.MARKDOWN_V2,
                replyMarkup = InlineButtons.thumbnailsTemplateManage(template, state)
            ).get()
            application.userStates[callbackQuery.from.id] = UserState.ChoosingLessonThumbnailsTemplate(
                state.lessonId,
                state.prevMessagesIds + newMessage.messageId,
                state.prevState
            )
        }
    }

    callbackQuery("LessonsEditThumbnailsTemplateNewCmd") {
        val state = application.userStates[callbackQuery.from.id]
        if (state is UserState.ChoosingLessonThumbnailsTemplate) {
            val chatId = ChatId.fromId(callbackQuery.from.id)
            val lesson = application.repository.getLesson(state.lessonId) ?: return@callbackQuery
            val titleWords = lesson.title.split(" ")
            val firstLineWords = StringBuilder(titleWords.getOrNull(0) ?: "")
            val secondLineWords = StringBuilder()
            for (word in titleWords.slice(1 until titleWords.size)) {
                if (firstLineWords.length + 1 + word.length <= 15) {
                    firstLineWords.append(' ')
                    firstLineWords.append(word)
                } else {
                    secondLineWords.append(' ')
                    secondLineWords.append(word)
                }
            }

            val t = ThumbnailsTemplate(
                name = lesson.name,
                firstTitle = firstLineWords.toString(),
                secondTitle = secondLineWords.toString().let { it.substring(minOf(it.length, 1)) },
                lecturerName = lesson.lecturerName,
                termNumber = if (lesson.termNumber.toIntOrNull() != null) lesson.titleTermNumber() else "lesson",
                color = itmoColors[Random.nextInt(itmoColors.size)]
            )
            val template = application.repository.insertThumbnailsTemplate(t)
            if (template == null) {
                bot.sendMessage(chatId, "Не удалось создать шаблон для превью").get()
            } else {
                application.repository.replaceLesson(lesson.copy(mainTemplateId = template.id))
                Thumbnail.generateTemplate(
                    template,
                    template.imageId?.let { application.filesRepository.getThumbnailsImagePath(it.toString()) },
                    application.filesRepository.getThumbnailsTemplatePath(template.id)
                )
                val newMessage = bot.sendMessage(
                    ChatId.fromId(callbackQuery.from.id),
                    template.infoMessage(),
                    parseMode = ParseMode.MARKDOWN_V2,
                    replyMarkup = InlineButtons.thumbnailsTemplateManage(template, state)
                ).get()
                application.userStates[callbackQuery.from.id] = UserState.ChoosingLessonThumbnailsTemplate(
                    state.lessonId,
                    state.prevMessagesIds + newMessage.messageId,
                    state.prevState
                )
            }
        }
    }
}
