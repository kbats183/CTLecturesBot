package ru.kbats.youtube.broadcastscheduler.bot.dispatcher

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.entities.inlinequeryresults.InlineQueryResult
import com.github.kotlintelegrambot.entities.inlinequeryresults.InputMessageContent
import com.google.api.client.http.ByteArrayContent
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import ru.kbats.youtube.broadcastscheduler.bot.*
import ru.kbats.youtube.broadcastscheduler.bot.Dispatch.logger
import ru.kbats.youtube.broadcastscheduler.data.*
import ru.kbats.youtube.broadcastscheduler.states.UserState
import ru.kbats.youtube.broadcastscheduler.thumbnail.Thumbnail
import java.io.ByteArrayOutputStream

private fun defaultVideoTitle(lesson: Lesson, lessonNumber: String) =
    "${lesson.videoTitle()}, ${lesson.lectureType.toTitle()} $lessonNumber"

private fun thumbnailsLectureNumber(lesson: Lesson, video: Video) =
    video.thumbnailsLectureNumber ?: when (lesson.lectureType) {
        LectureType.Lecture -> "L${video.lectureNumber}"
        LectureType.Practice -> "P${video.lectureNumber}"
    }

fun AdminDispatcher.setupVideosDispatcher() {
    suspend fun Video.infoStateMessage(): String {
        val lesson = application.repository.getLesson(lessonId.toString()) ?: return ""
        if (lesson.streamKey is StreamKey.Youtube && youtubeVideoId != null) {
            val liveStream = application.youtubeApi.getStream(lesson.streamKey.id)!!
            val broadcast = application.youtubeApi.getBroadcast(youtubeVideoId)!!
            return buildString {
                append("Youtube stream key: `${liveStream.cdn.ingestionInfo.streamName}`\n")
                liveStream.status?.let { append("Youtube stream key status: ${it.streamStatus}, ${it.healthStatus.status}\n") }
                broadcast.status?.let { append("Youtube video state: ${it.emojy()}${it.lifeCycleStatus}\n") }
            }
        }
        return ""
    }

    suspend fun Video.infoMessage(): String {
        val streamStatus =
            if (state == VideoState.LiveTest || state == VideoState.Live) "\n" + infoStateMessage() else ""

        return "Видео *${title.escapeMarkdown}*\n" +
                (thumbnailsLectureNumber?.let { "Номер лекции в заголовке: ${it.escapeMarkdown}\n" }
                    ?: "Номер лекции: ${lectureNumber.escapeMarkdown}\n") +
                "Статус: ${state.toTitle()}\n\n" +
                (youtubeVideoId?.let { "[Youtube видео](https://www.youtube.com/watch?v=${it})\n" } ?: "") +
                (vkVideoId?.let { "[VK видео](https://www.youtube.com/watch?v=${it})\n" } ?: "") +
                streamStatus
    }
//            (mainTemplateId?.let {
//                "[Превью](${application.filesRepository.getThumbnailsTemplatePublicUrl(it).withUpdateUrlSuffix()})\n\n"
//            } ?: "\n\n") +
//            "Следующая лекция: ${nextLectureNumber()}\n"

    suspend fun Bot.sendVideo(chatId: ChatId, video: Video) {
        sendMessage(
            chatId,
            video.infoMessage(),
            parseMode = ParseMode.MARKDOWN_V2,
            replyMarkup = InlineButtons.videoManage(video)
        ).get()
    }

    callbackQuery("LessonAddVideoCmd") {
        val chatId = ChatId.fromId(callbackQuery.from.id)
        val lessonId = callbackQueryId("LessonAddVideoCmd") ?: return@callbackQuery
        val lesson = application.repository.getLesson(lessonId) ?: return@callbackQuery
        val templateId = lesson.mainTemplateId
        if (templateId == null) {
            bot.sendMessage(chatId, "Нельзя создать видео для курса, у которого не задан шаблон превью")
            // TODO: или можно?
            return@callbackQuery
        }

        val lectureNumber = lesson.nextLectureNumber()
        val v = Video(
            lectureNumber = lectureNumber,
            title = defaultVideoTitle(lesson, lectureNumber),
            customTitle = null,
            lessonId = lesson.id,
            thumbnailsTemplateId = templateId,
            thumbnailsLectureNumber = null,
            state = VideoState.New,
            creationTime = Clock.System.now(),
        )
        val video = application.repository.insertVideo(v) ?: return@callbackQuery
        bot.sendVideo(chatId, video)
    }

    callbackQuery("VideoItemThumbnailsGenCmd") {
        val id = callbackQueryId("VideoItemThumbnailsGenCmd") ?: return@callbackQuery
        val video = application.repository.getVideo(id) ?: return@callbackQuery
        val lesson = application.repository.getLesson(video.lessonId.toString()) ?: return@callbackQuery
        val template =
            application.repository.getThumbnailsTemplate(video.thumbnailsTemplateId.toString()) ?: return@callbackQuery

        val generatingMessage = bot.sendMessage(ChatId.fromId(callbackQuery.from.id), text = "Generating ...").get()
        try {
            val byteBuffer = ByteArrayOutputStream()
            byteBuffer.use { buffer ->
                Thumbnail.generateThumbnail(
                    template,
                    template.imageId?.let { application.filesRepository.getThumbnailsImagePath(it.toString()) },
                    buffer,
                    thumbnailsLectureNumber(lesson, video)
                )
            }
            bot.sendDocument(
                ChatId.fromId(callbackQuery.from.id),
                TelegramFile.ByByteArray(byteBuffer.toByteArray(), "thumbnails_$id.png"),
                replyMarkup = InlineButtons.hideCallbackButton,
            )
        } catch (e: Throwable) {
            bot.sendMessage(ChatId.fromId(callbackQuery.from.id), text = e.message ?: e::class.java.name)
            throw e
        } finally {
            bot.delete(generatingMessage)
        }
    }

    callbackQuery("VideoItemRefreshCmd") {
        val id = callbackQueryId("VideoItemRefreshCmd") ?: return@callbackQuery
        val video = application.repository.getVideo(id) ?: return@callbackQuery
        callbackQuery.message?.let {
            bot.editMessageText(
                ChatId.fromId(it.chat.id),
                it.messageId,
                text = video.infoMessage(),
                parseMode = ParseMode.MARKDOWN_V2,
                replyMarkup = InlineButtons.videoManage(video)
            )
        }
    }

    inlineQuery {
        renderInlineListItems("VideosLesson") {
            val lessonId = inlineQuery.query.split(" ").first().substring("VideosLesson".length)
            application.repository.getVideosByLesson(lessonId).map {
                InlineQueryResult.Article(
                    id = "video_${it.id}",
//                    thumbUrl = TODO
                    title = it.title,
                    description = it.state.toTitle() + ", " + it.creationTime,
                    inputMessageContent = InputMessageContent.Text("video_${it.id}")
                )
            }
        }
    }

    text("video_") {
        val id = message.text?.let { videoIdRegexp.matchEntire(it) }?.groups?.get(1)?.value ?: return@text
        bot.delete(message)
        val video = application.repository.getVideo(id) ?: return@text
        bot.sendVideo(ChatId.fromId(message.chat.id), video)
    }

    callbackQuery("VideoItemEdit") {
        val chatId = ChatId.fromId(callbackQuery.from.id)
        val (op, id) = callbackQueryId("VideoItemEdit")?.split("Cmd")
            ?.takeIf { it.size == 2 }
            ?: return@callbackQuery

        val oldVideo = application.repository.getVideo(id) ?: return@callbackQuery
        val (text, keyboard) = when (op) {
            "LectureNumber" -> "Напишите номер лекции \\(число\\) для видео или отправьте /cancel\\.\n" +
                    "Текущий номер лекции `" + oldVideo.lectureNumber.escapeMarkdown + "`" to null

            "CustomTitle" -> "Напишите новый специальный заголовок видео или отправьте /useTemplate, если нужно генерировать название по шаблону и номеру лекции\\.\n" +
                    "Текущий заголовок: `${oldVideo.title.escapeMarkdown}`" to null

            else -> return@callbackQuery
        }

        val newMessage = bot.sendMessage(chatId, text, ParseMode.MARKDOWN_V2, replyMarkup = keyboard).get()
        application.userStates[callbackQuery.from.id] =
            UserState.EditingVideo(
                id,
                op,
                listOfNotNull(callbackQuery.message?.messageId, newMessage.messageId),
                application.userStates[callbackQuery.from.id],
            )
    }

    text {
        val chatId = ChatId.fromId(message.chat.id)
        when (val state = application.userStates[message.chat.id]) {
            is UserState.EditingVideo -> {
                val oldVideo = application.repository.getVideo(state.id) ?: return@text
                val lesson = application.repository.getLesson(oldVideo.lessonId.toString()) ?: return@text

                if (state.op == "CustomTitle" && !text.startsWith("/useTemplate")) {
                    val newMessage = bot.sendMessage(
                        chatId,
                        "Теперь отправьте номер лекции, который необходимо написать на обложке, например `L1` или `P2`",
                        parseMode = ParseMode.MARKDOWN_V2
                    ).get()
                    application.userStates[message.chat.id] = UserState.EditingVideo(
                        state.id,
                        "CustomTitleNumber",
                        state.prevMessagesIds + message.messageId + newMessage.messageId,
                        state.prevState,
                        listOf(text),
                    )
                    return@text
                }

                val video = when (state.op) {
                    "LectureNumber" -> oldVideo.copy(
                        lectureNumber = text,
                        title = oldVideo.customTitle ?: defaultVideoTitle(lesson, text)
                    )

                    "CustomTitle" -> oldVideo.copy(
                        title = defaultVideoTitle(lesson, oldVideo.lectureNumber),
                        customTitle = null,
                        thumbnailsLectureNumber = null,
                    )

                    "CustomTitleNumber" -> oldVideo.copy(
                        title = state.buffer[0],
                        customTitle = state.buffer[0],
                        thumbnailsLectureNumber = text
                    )

                    else -> return@text
                }

                application.userStates[message.chat.id] = state.prevState
                val successUpdate = application.repository.replaceVideo(video)
                if (!successUpdate) {
                    bot.sendMessage(chatId, "Не удалось изменить видео")
                    return@text
                }
                val newVideo = application.repository.getVideo(video.id.toString()) ?: return@text
                state.prevMessagesIds.forEach { bot.deleteMessage(chatId, it) }
                bot.delete(message)
                bot.sendVideo(chatId, newVideo)
            }

            else -> {}
        }
    }

    callbackQuery("VideoItemScheduleStreamCmd") {
        val id = callbackQueryId("VideoItemScheduleStreamCmd") ?: return@callbackQuery
        val video = application.repository.getVideo(id) ?: return@callbackQuery
        if (video.state != VideoState.New) return@callbackQuery
        val lesson = application.repository.getLesson(video.lessonId.toString()) ?: return@callbackQuery
        val chatId = ChatId.fromId(callbackQuery.from.id)

        val creatingMessage = bot.sendMessage(chatId, "Scheduling ...").get()

        if (lesson.streamKey == null) {
            bot.sendMessage(
                chatId,
                "Невозможно запланировать трансляцию для записи курса без настроенного ключа трансляции.\nЗадайте ключ трансляции в настройках курса.",
                replyMarkup = InlineButtons.hideCallbackButton
            )
            return@callbackQuery
        }


        val ytVideo = application.youtubeApi.createBroadcast(
            video.title,
            lesson.descriptionFull(),
            privacy = lesson.lessonPrivacy
        )
        logger.info("Created ytVideo id ${ytVideo.id}")

        val template = application.repository.getThumbnailsTemplate(video.thumbnailsTemplateId.toString())
        if (template != null) {
            val byteBuffer = ByteArrayOutputStream()
            byteBuffer.use { buffer ->
                Thumbnail.generateThumbnail(
                    template,
                    template.imageId?.let { application.filesRepository.getThumbnailsImagePath(it.toString()) },
                    buffer,
                    thumbnailsLectureNumber(lesson, video)
                )
            }
            application.youtubeApi.uploadVideoThumbnail(
                ytVideo.id,
                ByteArrayContent("image/png", byteBuffer.toByteArray())
            )
        }
        if (lesson.youtubePlaylistId != null) {
            application.youtubeApi.addVideoToPlaylist(lesson.youtubePlaylistId, ytVideo.id)
        }

        // create vk ....

        val newVideo = application.repository.getVideo(id)?.copy(
            youtubeVideoId = ytVideo.id,
            state = VideoState.Scheduled
        ) ?: return@callbackQuery
        require(application.repository.replaceVideo(newVideo)) { "Failed to update video" }
        callbackQuery.message?.let { bot.delete(it) }
        delay(1000)
        bot.delete(creatingMessage)
        bot.sendVideo(chatId, newVideo)
    }

    callbackQuery("VideoItemStartTestingCmd") {
        val id = callbackQueryId("VideoItemStartTestingCmd") ?: return@callbackQuery
        val video = application.repository.getVideo(id) ?: return@callbackQuery
        if (video.state != VideoState.Scheduled) return@callbackQuery
        val lesson = application.repository.getLesson(video.lessonId.toString()) ?: return@callbackQuery
        val chatId = ChatId.fromId(callbackQuery.from.id)

        if (lesson.streamKey == null) {
            bot.sendMessage(
                chatId,
                "Нет ключа трансляции для этого предмета",
                replyMarkup = InlineButtons.hideCallbackButton
            )
            return@callbackQuery
        }

        if (lesson.streamKey is StreamKey.Youtube && video.youtubeVideoId != null) {
            application.youtubeApi.bindBroadcastStream(video.youtubeVideoId, lesson.streamKey.id)
            if (application.youtubeApi.getBroadcast(video.youtubeVideoId)?.status?.lifeCycleStatus == "created") {
                application.youtubeApi.transitionBroadcast(video.youtubeVideoId, "testing")
            }
        }

        val newVideo = application.repository.getVideo(id)?.copy(state = VideoState.LiveTest) ?: return@callbackQuery
        require(application.repository.replaceVideo(newVideo))
        callbackQuery.message?.let { bot.delete(it) }
        bot.sendVideo(chatId, newVideo)
    }

    callbackQuery("VideoItemStartStreamingCmd") {
        val id = callbackQueryId("VideoItemStartStreamingCmd") ?: return@callbackQuery
        val video = application.repository.getVideo(id) ?: return@callbackQuery
        if (video.state != VideoState.LiveTest) return@callbackQuery
        val lesson = application.repository.getLesson(video.lessonId.toString()) ?: return@callbackQuery
        val chatId = ChatId.fromId(callbackQuery.from.id)

        if (lesson.streamKey is StreamKey.Youtube && video.youtubeVideoId != null) {
            val broadcast = application.youtubeApi.getBroadcast(video.youtubeVideoId)
            if (broadcast?.status?.lifeCycleStatus == "ready" || broadcast?.status?.lifeCycleStatus == "testStarting") {
                application.youtubeApi.transitionBroadcast(video.youtubeVideoId, "testing")
                callbackQuery.message?.let { bot.delete(it) }
                bot.sendVideo(chatId, video)
                return@callbackQuery
            } else if (broadcast?.status?.lifeCycleStatus != "testing" && broadcast?.status?.lifeCycleStatus != "testStarting") {
                bot.sendMessage(
                    chatId,
                    "Нельзя начать youtube трансляцию, которая не находится в режиме тестирования",
                    replyMarkup = InlineButtons.hideCallbackButton
                )
                return@callbackQuery
            }
            application.youtubeApi.transitionBroadcast(video.youtubeVideoId, "live")
        }

        val newVideo = application.repository.getVideo(id)?.copy(state = VideoState.Live) ?: return@callbackQuery
        require(application.repository.replaceVideo(newVideo))
        callbackQuery.message?.let { bot.delete(it) }
        bot.sendVideo(chatId, newVideo)
    }

    callbackQuery("VideoItemStopStreamingCmd") {
        val id = callbackQueryId("VideoItemStopStreamingCmd") ?: return@callbackQuery
        val video = application.repository.getVideo(id) ?: return@callbackQuery
        if (video.state != VideoState.Live) return@callbackQuery
        callbackQuery.message?.let { bot.delete(it) }
        bot.sendMessage(
            chatId = ChatId.fromId(callbackQuery.from.id),
            "Остановить трансляцию?",
            replyMarkup = InlineButtons.videoStopStream(video)
        )
    }

    callbackQuery("VideoItemStopStreamingCancelCmd") {
        val id = callbackQueryId("VideoItemStopStreamingCancelCmd") ?: return@callbackQuery
        val video = application.repository.getVideo(id) ?: return@callbackQuery
        if (video.state != VideoState.Live) return@callbackQuery
        callbackQuery.message?.let { bot.delete(it) }
        bot.sendVideo(chatId = ChatId.fromId(callbackQuery.from.id), video)
    }

    callbackQuery("VideoItemStopStreamingConfirmCmd") {
        val id = callbackQueryId("VideoItemStopStreamingConfirmCmd") ?: return@callbackQuery
        val video = application.repository.getVideo(id) ?: return@callbackQuery
        if (video.state != VideoState.Live) return@callbackQuery
        val lesson = application.repository.getLesson(video.lessonId.toString()) ?: return@callbackQuery
        val chatId = ChatId.fromId(callbackQuery.from.id)

        if (lesson.streamKey is StreamKey.Youtube && video.youtubeVideoId != null) {
            val broadcast = application.youtubeApi.getBroadcast(video.youtubeVideoId)
            if (broadcast?.status?.lifeCycleStatus == "live") {
                application.youtubeApi.transitionBroadcast(video.youtubeVideoId, "complete")
            } else if (broadcast?.status?.lifeCycleStatus != "complete") {
                bot.sendMessage(
                    chatId = ChatId.fromId(callbackQuery.from.id),
                    "Не возможно завершить трансляцию на youtube",
                    replyMarkup = InlineButtons.hideCallbackButton
                )
                return@callbackQuery
            }
        }

        val newVideo = application.repository.getVideo(id)?.copy(state = VideoState.Recorded) ?: return@callbackQuery
        require(application.repository.replaceVideo(newVideo))

        callbackQuery.message?.let { bot.delete(it) }
        bot.sendVideo(chatId, newVideo)
    }
}
