package ru.kbats.youtube.broadcastscheduler.bot.dispatcher

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.inlinequeryresults.InlineQueryResult
import com.github.kotlintelegrambot.entities.inlinequeryresults.InputMessageContent
import ru.kbats.youtube.broadcastscheduler.bot.*
import ru.kbats.youtube.broadcastscheduler.data.LectureBroadcastPrivacy
import ru.kbats.youtube.broadcastscheduler.data.LectureType
import ru.kbats.youtube.broadcastscheduler.data.Lesson
import ru.kbats.youtube.broadcastscheduler.data.StreamKey
import ru.kbats.youtube.broadcastscheduler.states.UserState
import ru.kbats.youtube.broadcastscheduler.withUpdateUrlSuffix

fun AdminDispatcher.setupLessonsDispatcher() {
    fun Lesson.infoMessage(): String = "Записи *${name.escapeMarkdown}*\n" +
            "Заголовок: ${title.escapeMarkdown}\n" +
            "Лектор: ${lecturerName.escapeMarkdown}\n" +
            "Семестр: ${titleTermNumber().escapeMarkdown}\n" +
            "Тип: *${lectureType}*\n" +
            "Доступ: *${lessonPrivacy.toTitle().escapeMarkdown}*\n\n" +
            (mainTemplateId?.let {
                "[Превью](${application.filesRepository.getThumbnailsTemplatePublicUrl(it).withUpdateUrlSuffix()})\n"
            } ?: "") +
            (youtubePlaylistId?.let {
                "[Плейлист youtube](https://www.youtube.com/playlist?list=${it})\n"
            } ?: "") +
            (vkPlaylistId?.let {
                "[Плейлист vk](${application.vkApi.getAlbumUrl(it)})\n"
            } ?: "") +
            "Ключ трансляции: ${streamKey.toTitle()}\n" +
            "\nСледующая лекция: ${nextLectureNumber()}\n"

    fun Bot.sendLesson(chatId: ChatId, lesson: Lesson) {
        sendMessage(
            chatId,
            lesson.infoMessage(),
            parseMode = ParseMode.MARKDOWN_V2,
            replyMarkup = InlineButtons.lessonManage(lesson)
        ).get()
    }

    callbackQuery("LessonsCmd") {
        bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id),
            text = "Записи курсов\n",
            replyMarkup = InlineButtons.lessonsMenu,
        )
    }

    callbackQuery("LessonsNewCmd") {
        val chatId = ChatId.fromId(callbackQuery.from.id)
        val newMessage = bot.sendMessage(
            chatId,
            "Чтобы добавить курс, напишите короткое название курса\\.\n" +
                    "Например, `${"1.MathAn38-39".escapeMarkdown}` или `${"2.OS.Hard".escapeMarkdown}`",
            ParseMode.MARKDOWN_V2
        ).getOrNull()
        application.userStates[callbackQuery.from.id] = UserState.CreatingLesson(
            "Name",
            Lesson(
                name = "",
                title = "",
                lecturerName = "",
                termNumber = "",
                doubleNumerationFormat = false,
                mainTemplateId = null
            ),
            prevMessagesIds = listOfNotNull(newMessage?.messageId)
        )
    }

    text {
        val chatId = ChatId.fromId(message.chat.id)
        val state = application.userStates[message.chat.id]
        if (state is UserState.CreatingLesson) {
            val (newLesson, nextStep, nextText) = when (state.step) {
                "Name" -> Triple(
                    state.lesson.copy(name = text),
                    "Title",
                    "Ok\\! Теперь напишите формальное название курса, которое будет использоваться в заголовке видео\\." +
                            "Например, `Математический анализ` или `Операционные системы`"
                )

                "Title" -> Triple(
                    state.lesson.copy(title = text),
                    "LecturerName",
                    "Ok\\! Теперь напишите имя лектора, который читает эту лекцию\\. " +
                            "Например, `Никита Голиков` или `К\\. П\\. Кохась`"
                )

                "LecturerName" -> Triple(
                    state.lesson.copy(lecturerName = text),
                    "TermNumber",
                    "Ok\\! Теперь напишите номер семестра, в котором читается этот курс: число или сокращение `SC` \\(для курсов по выбору\\)\\."
                )

                "TermNumber" -> {
                    if (text.toIntOrNull() in 1..8 || text == "SC") {
                        Triple(
                            state.lesson.copy(termNumber = text),
                            "Finish",
                            ""
                        )
                    } else {
                        Triple(
                            state.lesson,
                            "TermNumber",
                            "Номер семестра, в котором читается этот курс, должен быть числом или сокращением SC \\(для курсов по выбору\\)\\."
                        )
                    }
                }

                else -> return@text
            }

            state.prevMessagesIds.forEach { bot.deleteMessage(chatId, it) }
            bot.delete(message)
            if (nextStep == "Finish") {
                val created = application.repository.insertLesson(newLesson)
                if (created == null) {
                    bot.sendMessage(
                        chatId,
                        "Не получилось создать новый курс",
                        parseMode = ParseMode.MARKDOWN_V2
                    ).getOrNull()
                } else {
                    bot.sendLesson(chatId, created)
                }
                application.userStates[message.chat.id] = UserState.Default
                return@text
            }

            val newMessage = bot.sendMessage(
                chatId,
                nextText,
                parseMode = ParseMode.MARKDOWN_V2
            ).getOrNull()
            application.userStates[message.chat.id] =
                UserState.CreatingLesson(nextStep, newLesson, listOfNotNull(newMessage?.messageId))
        }
    }

    inlineQuery {
        renderInlineListItems("Lessons") {
            application.repository.getLessons().map {
                InlineQueryResult.Article(
                    id = "lesson_${it.id}",
                    thumbUrl = it.mainTemplateId?.let {
                        application.filesRepository.getThumbnailsTemplatePublicUrl(it)
                    },
                    title = it.name,
                    description = "[${it.termNumber}] ${it.title}, ${it.lecturerName}",
                    inputMessageContent = InputMessageContent.Text("lesson_${it.id}")
                )
            }
        }
    }

    text("lesson_") {
        val id = message.text?.let { lessonIdRegexp.matchEntire(it) }?.groups?.get(1)?.value ?: return@text
        bot.delete(message)
        val lesson = application.repository.getLesson(id) ?: return@text
        bot.sendLesson(ChatId.fromId(message.chat.id), lesson)
    }

    callbackQuery("LessonSettingsEditThumbnailsTemplateCmd") {
        val id = callbackQueryId("LessonSettingsEditThumbnailsTemplateCmd") ?: return@callbackQuery
        val lesson = application.repository.getLesson(id) ?: return@callbackQuery

        val newMessage = bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id),
            "Шаблон превью курса *${lesson.name.escapeMarkdown}*" + (lesson.mainTemplateId?.let {
                "\n\n[Превью](${application.filesRepository.getThumbnailsTemplatePublicUrl(it).withUpdateUrlSuffix()})"
            } ?: ""),
            parseMode = ParseMode.MARKDOWN_V2,
            replyMarkup = InlineButtons.lessonsEditThumbnailsMenu(lesson),
        ).get()
        application.userStates[callbackQuery.from.id] =
            UserState.ChoosingLessonThumbnailsTemplate(
                id,
                listOfNotNull(callbackQuery.message?.messageId, newMessage.messageId),
                prevState = application.userStates[callbackQuery.from.id]
            )
    }

    callbackQuery("ThumbnailsTemplatesItemBackCmd") {
        val state = application.userStates[callbackQuery.from.id]
        if (state is UserState.ChoosingLessonThumbnailsTemplate) {
            state.prevMessagesIds.forEach { bot.deleteMessage(ChatId.fromId(callbackQuery.from.id), it) }
            application.userStates[callbackQuery.from.id] = state.prevState
            callbackQuery.message?.let { bot.delete(it) }

            val lesson = application.repository.getLesson(state.lessonId) ?: return@callbackQuery
            bot.sendLesson(ChatId.fromId(callbackQuery.from.id), lesson)
        }
    }

    callbackQuery("LessonsSettingsEditThumbnailsTemplateCancelCmd") {
        val state = application.userStates[callbackQuery.from.id]
        if (state is UserState.ChoosingLessonThumbnailsTemplate) {
            application.userStates[callbackQuery.from.id] = state.prevState
            callbackQuery.message?.let { bot.delete(it) }
        }
    }

    callbackQuery("LessonChangeNumber") {
        val op = "LessonChangeNumberIncCmd".takeIf { callbackQuery.data.startsWith(it) } ?: "LessonChangeNumberDecCmd"
        val opNumber = if (op == "LessonChangeNumberIncCmd") 1 else -1
        val chatId = ChatId.fromId(callbackQuery.from.id)
        val id = callbackQueryId(op) ?: return@callbackQuery
        val oldLesson = application.repository.getLesson(id) ?: return@callbackQuery
        val successUpdate = application.repository.replaceLesson(
            oldLesson.copy(currentLectureNumber = oldLesson.currentLectureNumber + opNumber * if (oldLesson.doubleNumerationFormat) 2 else 1)
        )
        if (!successUpdate) {
            bot.sendMessage(chatId, "Не удалось изменить курс")
            return@callbackQuery
        }
        val lesson = application.repository.getLesson(oldLesson.id.toString()) ?: return@callbackQuery
        callbackQuery.message?.let { bot.delete(it) }
        bot.sendLesson(chatId, lesson)
    }

    callbackQuery("LessonSettingsMenuCmd") {
        val id = callbackQueryId("LessonSettingsMenuCmd") ?: return@callbackQuery
        val lesson = application.repository.getLesson(id) ?: return@callbackQuery
        callbackQuery.message?.let { bot.delete(it) }
        bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id),
            lesson.infoMessage(),
            parseMode = ParseMode.MARKDOWN_V2,
            replyMarkup = InlineButtons.lessonSettings(lesson)
        ).get()
    }

    callbackQuery("LessonSettingsBackCmd") {
        val id = callbackQueryId("LessonSettingsBackCmd") ?: return@callbackQuery
        val lesson = application.repository.getLesson(id) ?: return@callbackQuery
        callbackQuery.message?.let { bot.delete(it) }
        bot.sendLesson(ChatId.fromId(callbackQuery.from.id), lesson)
    }

    callbackQuery("LessonSettingsEdit") {
        val chatId = ChatId.fromId(callbackQuery.from.id)
        val (op, id) = callbackQueryId("LessonSettingsEdit")?.split("Cmd")
            ?.takeIf { it.size == 2 }
            ?: return@callbackQuery

        val oldLesson = application.repository.getLesson(id) ?: return@callbackQuery
        val (text, keyboard) = when (op) {
            "Name" -> "Напишите новое название курса или отправьте /cancel\\.\n" +
                    "Текущее название, `" + oldLesson.name.escapeMarkdown + "`" to null

            "Title" -> "Напишите новый заголовок для записей курса или отправьте /cancel\\.\n" +
                    "Текущий заголовок: `${oldLesson.title.escapeMarkdown}`" to null

            "Lecturer" -> "Напишите новое имея лектора для записей курса или отправьте /cancel\\.\n" +
                    "Текущий лектор: `${oldLesson.lecturerName.escapeMarkdown}`" to null

            "TermNumber" -> "Напишите новый номер семестра для записей курса или отправьте /cancel\\.\n" +
                    "Текущий номер семестра: `${oldLesson.termNumber.escapeMarkdown}`" to null

            "Privacy" -> "Напишите тип доступа `Public` или `Unlisted` или /cancel\\.\n" +
                    "Текущий тип доступа: `${oldLesson.lessonPrivacy}`" to null

            "Type" -> "Напишите тип записей `Lecture` или `Practice` или /cancel\\.\n" +
                    "Текущий тип: `${oldLesson.lectureType}`" to null

            else -> return@callbackQuery
        }

        val newMessage = bot.sendMessage(chatId, text, ParseMode.MARKDOWN_V2, replyMarkup = keyboard).getOrNull()
        application.userStates[callbackQuery.from.id] =
            UserState.EditingLesson(
                id,
                op,
                listOfNotNull(callbackQuery.message?.messageId, newMessage?.messageId),
                application.userStates[callbackQuery.from.id],
            )
    }

    text {
        val chatId = ChatId.fromId(message.chat.id)
        when (val state = application.userStates[message.chat.id]) {
            is UserState.ChoosingLessonThumbnailsTemplate -> {
                val id = text.let { thumbnailsTemplateIdRegexp.matchEntire(it) }?.groups?.get(1)?.value ?: return@text
                val oldLesson = application.repository.getLesson(state.lessonId) ?: return@text
                val template = application.repository.getThumbnailsTemplate(id) ?: return@text

                if (!application.repository.replaceLesson(oldLesson.copy(mainTemplateId = template.id))) {
                    bot.sendMessage(chatId, "Не удалось изменить шаблон превью")
                    return@text
                }
                val lesson = application.repository.getLesson(state.lessonId) ?: return@text

                bot.delete(message)
                state.prevMessagesIds.forEach { bot.deleteMessage(chatId, it) }
                bot.sendLesson(ChatId.fromId(message.chat.id), lesson)
            }

            is UserState.EditingLesson -> {
                val oldLesson = application.repository.getLesson(state.id) ?: return@text
                val lesson = when (state.op) {
                    "Name" -> oldLesson.copy(name = text)
                    "Title" -> oldLesson.copy(title = text)
                    "Lecturer" -> oldLesson.copy(lecturerName = text)
                    "TermNumber" -> oldLesson.copy(termNumber = text)
                    "Privacy" -> {
                        if (text != "Public" && text != "Unlisted") {
                            val m = bot.sendMessage(
                                chatId,
                                "Некорректный тип доступа, нужно отправить один из двух типов доступа или нажать /cancel",
                                replyMarkup = InlineButtons.hideCallbackButton
                            ).get()
                            application.userStates[message.chat.id] = UserState.EditingLesson(
                                state.id,
                                state.op,
                                state.prevMessagesIds + message.messageId + m.messageId,
                                state.prevState
                            )
                            return@text
                        }
                        oldLesson.copy(lessonPrivacy = LectureBroadcastPrivacy.valueOf(text))
                    }

                    "Type" -> {
                        if (text != "Lecture" && text != "Practice") {
                            val m = bot.sendMessage(
                                chatId,
                                "Некорректный тип записей, нужно отправить один из двух типов или нажать /cancel",
                                replyMarkup = InlineButtons.hideCallbackButton
                            ).get()
                            application.userStates[message.chat.id] = UserState.EditingLesson(
                                state.id,
                                state.op,
                                state.prevMessagesIds + message.messageId + m.messageId,
                                state.prevState
                            )
                            return@text
                        }
                        oldLesson.copy(lectureType = LectureType.valueOf(text))
                    }

                    else -> return@text
                }

                application.userStates[message.chat.id] = state.prevState
                val successUpdate = application.repository.replaceLesson(lesson)
                if (!successUpdate) {
                    bot.sendMessage(chatId, "Не удалось изменить курс")
                    return@text
                }
                val newLesson = application.repository.getLesson(lesson.id.toString()) ?: return@text
                state.prevMessagesIds.forEach { bot.deleteMessage(chatId, it) }
                bot.delete(message)
                bot.sendMessage(
                    chatId,
                    newLesson.infoMessage(),
                    parseMode = ParseMode.MARKDOWN_V2,
                    replyMarkup = InlineButtons.lessonSettings(newLesson)
                )
            }

            else -> {}
        }
    }
    callbackQuery("LessonSettingsEditStreamKeyCmd") {
        val id = callbackQueryId("LessonSettingsEditStreamKeyCmd") ?: return@callbackQuery
        val lesson = application.repository.getLesson(id) ?: return@callbackQuery

        val newMessage = bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id),
            "Ключ трансляции курса *${lesson.name.escapeMarkdown}*\n\n" + lesson.streamKey.toTitle(),
            parseMode = ParseMode.MARKDOWN_V2,
            replyMarkup = InlineButtons.lessonsEditStreamKey(lesson),
        ).get()
        application.userStates[callbackQuery.from.id] =
            UserState.ChoosingLessonStreamKey(
                id,
                listOfNotNull(callbackQuery.message?.messageId, newMessage.messageId),
                prevState = application.userStates[callbackQuery.from.id]
            )
    }

    callbackQuery("LessonsEditStreamKeyRestreamerCmd") {
        val state = application.userStates[callbackQuery.from.id]
        if (state !is UserState.ChoosingLessonStreamKey) {
            return@callbackQuery
        }
        val key = application.repository.genRestreamerKey()
        val ytLiveStream = application.youtubeApi.createStream("__r${key.name}")
        val keyWithYt = key.copy(youtube = StreamKey.Youtube(ytLiveStream))
        require(application.repository.replaceRestreamerKey(keyWithYt))
        val lesson =
            requireNotNull(application.repository.getLesson(state.lessonId)) { "Failed to find lesson ${state.lessonId}" }
        require(application.repository.replaceLesson(lesson.copy(streamKey = keyWithYt)))
        val newLesson = requireNotNull(application.repository.getLesson(state.lessonId))

        application.userStates[callbackQuery.from.id] = state.prevState
        state.prevMessagesIds.forEach { bot.deleteMessage(chatId = ChatId.fromId(callbackQuery.from.id), it) }
        callbackQuery.message?.let { bot.delete(it) }
        bot.sendLesson(chatId = ChatId.fromId(callbackQuery.from.id), newLesson)
    }

    inlineQuery {
        renderInlineListItems("StreamKeyForLessons") {
            val x = application.youtubeApi.getStreams()
            x.map {
                InlineQueryResult.Article(
                    id = "lesson_stream_key_${it.id}",
                    title = it.snippet.title,
                    description = it.cdn.ingestionInfo.streamName,
                    inputMessageContent = InputMessageContent.Text("lesson_stream_key_${it.id}")
                )
            }
        }
    }

    text("lesson_stream_key_") {
        val state = application.userStates[message.chat.id]
        if (state !is UserState.ChoosingLessonStreamKey) {
            return@text
        }
        val id = message.text?.let { lessonStreamKeyIdRegexp.matchEntire(it) }?.groups?.get(1)?.value ?: return@text
        val lesson = requireNotNull(application.repository.getLesson(state.lessonId))
        bot.delete(message)
        val streamKey = application.youtubeApi.getStream(id) ?: return@text
        require(application.repository.replaceLesson(lesson.copy(streamKey = StreamKey.Youtube(streamKey))))
        val newLesson = requireNotNull(application.repository.getLesson(state.lessonId))

        application.userStates[message.chat.id] = state.prevState
        state.prevMessagesIds.forEach { bot.deleteMessage(chatId = ChatId.fromId(message.chat.id), it) }
        bot.sendLesson(chatId = ChatId.fromId(message.chat.id), newLesson)
    }

    callbackQuery("LessonsEditStreamKeyCancelCmd") {
        val state = application.userStates[callbackQuery.from.id]
        if (state is UserState.ChoosingLessonThumbnailsTemplate) {
            application.userStates[callbackQuery.from.id] = state.prevState
        }
        callbackQuery.message?.let { bot.delete(it) }
    }

    callbackQuery("LessonCreatePlaylistYTCmd") {
        val id = callbackQueryId("LessonCreatePlaylistYTCmd") ?: return@callbackQuery
        val lesson = requireNotNull(application.repository.getLesson(id)) { "No such lesson with id $id" }
        val playlistId =
            application.youtubeApi.createPlaylist(lesson.videoTitle(), lesson.description(), lesson.lessonPrivacy)
        require(application.repository.replaceLesson(lesson.copy(youtubePlaylistId = playlistId))) { "Failed to update lesson $id" }
        val newLesson = requireNotNull(application.repository.getLesson(id)) { "No such lesson with id $id" }
        callbackQuery.message?.let { bot.delete(it) }
        bot.sendLesson(ChatId.fromId(callbackQuery.from.id), newLesson)
    }

    callbackQuery("LessonCreatePlaylistVKCmd") {
        val id = callbackQueryId("LessonCreatePlaylistVKCmd") ?: return@callbackQuery
        val lesson = requireNotNull(application.repository.getLesson(id)) { "No such lesson with id $id" }
        val playlistId = application.vkApi.createAlbum(lesson.videoTitle(), lesson.lessonPrivacy)
        require(application.repository.replaceLesson(lesson.copy(vkPlaylistId = playlistId))) { "Failed to update lesson $id" }
        val newLesson = requireNotNull(application.repository.getLesson(id)) { "No such lesson with id $id" }
        callbackQuery.message?.let { bot.delete(it) }
        bot.sendLesson(ChatId.fromId(callbackQuery.from.id), newLesson)
    }

    callbackQuery("LessonAddVideoAskCmd") {
        val chatId = ChatId.fromId(callbackQuery.from.id)
        val lessonId = callbackQueryId("LessonAddVideoAskCmd") ?: return@callbackQuery
        val lesson = application.repository.getLesson(lessonId) ?: return@callbackQuery
        bot.sendMessage(
            chatId,
            "Создать видео с номером ${lesson.nextLectureNumber()}?",
            replyMarkup = InlineButtons.addVideoNewVideoAsk(lesson)
        ).get()
    }
}
