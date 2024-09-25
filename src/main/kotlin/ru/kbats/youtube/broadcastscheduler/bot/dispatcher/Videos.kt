package ru.kbats.youtube.broadcastscheduler.bot.dispatcher

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.entities.inlinequeryresults.InlineQueryResult
import com.github.kotlintelegrambot.entities.inlinequeryresults.InputMessageContent
import com.google.api.client.http.FileContent
import com.google.api.services.youtube.model.LiveBroadcast
import com.vk.api.sdk.objects.video.VideoFull
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import ru.kbats.youtube.broadcastscheduler.YoutubeVideoIDMatcher
import ru.kbats.youtube.broadcastscheduler.bot.*
import ru.kbats.youtube.broadcastscheduler.bot.Dispatch.logger
import ru.kbats.youtube.broadcastscheduler.data.*
import ru.kbats.youtube.broadcastscheduler.states.UserState
import ru.kbats.youtube.broadcastscheduler.thumbnail.Thumbnail
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.outputStream

private fun defaultVideoTitle(lesson: Lesson, lessonNumber: String) =
    "${lesson.videoTitle()}, ${lesson.lectureType.toTitle()} $lessonNumber"

private fun thumbnailsLectureNumber(lesson: Lesson, video: Video) =
    video.thumbnailsLectureNumber ?: when (lesson.lectureType) {
        LectureType.Lecture -> "L${video.lectureNumber}"
        LectureType.Practice -> "P${video.lectureNumber}"
    }

fun AdminDispatcher.setupVideosDispatcher() {
    suspend fun Video.infoStateMessage(liveBroadcast: LiveBroadcast?, vkVideo: VideoFull?): String {
        val lesson = application.repository.getLesson(lessonId.toString()) ?: return ""
        return buildString {
            fun statusYoutube(streamKey: StreamKey.Youtube) {
                val liveStream = application.youtubeApi.getStream(streamKey.id)!!

                liveStream.status?.let { append("Youtube stream key status: ${it.streamStatus}, ${it.healthStatus.status}\n") }
                liveBroadcast?.status?.let { append("Youtube video state: ${it.emojy()}${it.lifeCycleStatus}\n") }
            }

            if (lesson.streamKey is StreamKey.Youtube && youtubeVideoId != null) {
                append("*Youtube*: stream key `${lesson.streamKey.key.escapeMarkdown}`\n")
                statusYoutube(lesson.streamKey)
            } else if (lesson.streamKey is StreamKey.Restreamer) {
                val status = application.restreamer.getStreamKeyStatus(lesson.streamKey.name)
                append(
                    "*Рестример*: " + (status?.let { (if (it.isLive) "\uD83D\uDFE2" else "\uD83D\uDD34") + "  ${it.bitrate}kbps" }
                        ?: "No key setup") +
                            "\n  Custom server `${application.restreamer.rtmpUrl.escapeMarkdown}`\n" +
                            "  Stream key `${lesson.streamKey.name.escapeMarkdown}`\n"
                )

                if (lesson.streamKey.youtube != null && youtubeVideoId != null) {
                    statusYoutube(lesson.streamKey.youtube)
                }
//                if (vkVideo != null) {
//                }
            }
        }

    }

    suspend fun Video.infoMessage(ytVideo: LiveBroadcast?, vkVideo: VideoFull?): String {
        val streamStatus =
            if (state == VideoState.Scheduled || state == VideoState.LiveTest || state == VideoState.Live) "\n" +
                    infoStateMessage(ytVideo, vkVideo) else ""

        return "Видео *${title.escapeMarkdown}*\n" +
                (thumbnailsLectureNumber?.let { "Номер лекции в заголовке: ${it.escapeMarkdown}\n" }
                    ?: "Номер лекции: ${lectureNumber.escapeMarkdown}\n") +
                "Статус: ${state.toTitle()}\n\n" +
                (vkVideo?.let { "[VK видео](${application.vkApi.getVideoLink(it)})\n" } ?: "") +
                (youtubeVideoId?.let {
                    "[Youtube видео](https://www.youtube.com/watch?v=${it})\n"
                } ?: "") +
                streamStatus + "\n${id.toString().escapeMarkdown}"
    }
//            (mainTemplateId?.let {
//                "[Превью](${application.filesRepository.getThumbnailsTemplatePublicUrl(it).withUpdateUrlSuffix()})\n\n"
//            } ?: "\n\n") +
//            "Следующая лекция: ${nextLectureNumber()}\n"

    suspend fun Bot.sendVideo(chatId: ChatId, video: Video) {
        val ytVideo = video.youtubeVideoId?.let { application.youtubeApi.getBroadcast(it) }
        val vkVideo = video.vkVideoId?.let { application.vkApi.getVideo(it) }
        sendMessage(
            chatId,
            video.infoMessage(ytVideo, vkVideo),
            parseMode = ParseMode.MARKDOWN_V2,
            replyMarkup = InlineButtons.videoManage(video, ytVideo)
        ).get()
    }

    callbackQuery("LessonAddVideoCmd") {
        callbackQuery.message?.let { bot.delete(it) }
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
        val ytVideo = video.youtubeVideoId?.let { application.youtubeApi.getBroadcast(it) }
        val vkVideo = video.vkVideoId?.let { application.vkApi.getVideo(it) }
        callbackQuery.message?.let {
            bot.editMessageText(
                ChatId.fromId(it.chat.id),
                it.messageId,
                text = video.infoMessage(ytVideo, vkVideo),
                parseMode = ParseMode.MARKDOWN_V2,
                replyMarkup = InlineButtons.videoManage(video, ytVideo)
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

            is UserState.ApplyingLessonTemplateToVideo -> {
                val videoId = state.id
                val video = requireNotNull(application.repository.getVideo(videoId)).copy()
                val lesson = requireNotNull(application.repository.getLesson(video.lessonId.toString()))
                val template =
                    lesson.mainTemplateId?.let { application.repository.getThumbnailsTemplate(it.toString()) }

                if (state.platform == "YT") {
                    val ytVideoId = YoutubeVideoIDMatcher.match(text) ?: return@text Unit.also {
                        val m = bot.sendMessage(
                            chatId,
                            text = "Некорректный id video, попробуйте еще раз или нажмите /cancel"
                        ).get()
                        application.userStates[message.chat.id] = UserState.ApplyingLessonTemplateToVideo(
                            state.id,
                            state.platform,
                            state.prevMessagesIds + message.messageId + m.messageId,
                            state.prevState
                        )

                    }
                    if (application.youtubeApi.getVideo(ytVideoId) == null) {
                        bot.sendMessage(chatId, text = "Нет видео с таким $ytVideoId")
                        application.userStates[message.chat.id] = state.prevState
                        return@text
                    }

                    val applyingMessage = bot.sendMessage(chatId, text = "Applying ...").get()

                    application.youtubeApi.updateVideo(
                        ytVideoId,
                        title = video.title,
                        description = lesson.descriptionFull(application),
                        privacy = lesson.lessonPrivacy
                    )
                    if (template != null) {
                        val generatedPhoto = generateThumbnails(template, lesson, video)
                        application.youtubeApi.uploadVideoThumbnail(
                            ytVideoId,
                            FileContent("image/png", generatedPhoto.toFile())
                        )
                    }
                    if (lesson.youtubePlaylistId != null) {
                        application.youtubeApi.addVideoToPlaylist(lesson.youtubePlaylistId, ytVideoId)
                    }
                    application.repository.replaceVideo(video.copy(youtubeVideoId = ytVideoId))
                    bot.delete(applyingMessage)
                } else if (state.platform == "VK") {
                    val vkVideoId = text.toIntOrNull()
                    val vkVideo = vkVideoId?.let { application.vkApi.getVideo(it) }
                    if (vkVideo == null) {
                        bot.sendMessage(chatId, text = "Нет видео с таким id $vkVideoId")
                        application.userStates[message.chat.id] = state.prevState
                        return@text
                    }
                    val applyingMessage = bot.sendMessage(chatId, text = "Applying ...").get()

                    application.vkApi.editVideo(
                        vkVideoId,
                        title = video.title,
                        description = lesson.descriptionFull(application),
                        privacy = lesson.lessonPrivacy
                    )
                    if (template != null) {
                        val generatedPhoto = generateThumbnails(template, lesson, video)
                        application.vkApi.uploadVideoThumbnail(vkVideoId, generatedPhoto)
                    }
                    if (lesson.vkPlaylistId != null) {
                        application.vkApi.addVideoToAlbum(lesson.vkPlaylistId, vkVideoId)
                    }
                    application.repository.replaceVideo(video.copy(vkVideoId = vkVideoId))
                    bot.delete(applyingMessage)
                }
                state.prevMessagesIds.forEach { bot.deleteMessage(chatId, it) }
                bot.delete(message)
                application.userStates[message.chat.id] = state.prevState
                val newVideo = application.repository.getVideo(videoId)!!.copy(state = VideoState.Recorded)
                bot.sendVideo(chatId, newVideo)
            }

            else -> {}
        }
    }

    callbackQuery("VideoItemScheduleStreamAskCmd") {
        val id = callbackQueryId("VideoItemScheduleStreamAskCmd") ?: return@callbackQuery
        val video = application.repository.getVideo(id) ?: return@callbackQuery
        if (video.state != VideoState.New) return@callbackQuery
        bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id),
            "Запланировать лекцию ${video.title}?",
            replyMarkup = InlineButtons.scheduleStreamVideoAsk(video)
        ).get()
    }

    callbackQuery("VideoItemScheduleStreamCmd") {
        val id = callbackQueryId("VideoItemScheduleStreamCmd") ?: return@callbackQuery
        val video = application.repository.getVideo(id) ?: return@callbackQuery
        if (video.state != VideoState.New) return@callbackQuery
        val lesson = application.repository.getLesson(video.lessonId.toString()) ?: return@callbackQuery
        callbackQuery.message?.let { bot.delete(it) }
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
            lesson.descriptionFull(application),
            privacy = lesson.lessonPrivacy
        )
        logger.info("Created ytVideo id ${ytVideo.id}")

        if (lesson.youtubePlaylistId != null) {
            application.youtubeApi.addVideoToPlaylist(lesson.youtubePlaylistId, ytVideo.id)
        }

        var vkVideoId: Int? = null
        var vkStreamKey: String? = null
        if (lesson.streamKey is StreamKey.Restreamer) {
            // create vk ....
            logger.info("Creating vk video ${video.title}....")
            val v = application.vkApi.createBroadcast(
                video.title,
                lesson.descriptionFull(application),
                lesson.lessonPrivacy
            )
            vkVideoId = v.videoId
            vkStreamKey = v.stream.url.toString() + v.stream.key
            logger.info("Stream key for VK ${v.stream} $vkStreamKey")
            if (lesson.vkPlaylistId != null) {
                logger.info("Adding video to album ${video.title}....")
                application.vkApi.addVideoToAlbum(lesson.vkPlaylistId, vkVideoId)
            }
            val videoFull = application.vkApi.getVideo(vkVideoId)

            // restreamer
            logger.info("Adding restreamer key")
            val x = listOfNotNull(
                lesson.streamKey.youtube?.let { "rtmp://a.rtmp.youtube.com/live2/${it.key}" },
            )
            application.restreamer.createStreamKey(lesson.streamKey.name, x)
        }

        val template = application.repository.getThumbnailsTemplate(video.thumbnailsTemplateId.toString())
        if (template != null) {
            val generatedPhoto = generateThumbnails(template, lesson, video)
            application.youtubeApi.uploadVideoThumbnail(
                ytVideo.id,
                FileContent("image/png", generatedPhoto.toFile())
            )

            if (vkVideoId != null) {
                application.vkApi.uploadVideoThumbnail(vkVideoId, generatedPhoto)
            }
        }


        val newVideo = application.repository.getVideo(id)?.copy(
            youtubeVideoId = ytVideo.id,
            vkVideoId = vkVideoId,
            vkStreamKey = vkStreamKey,
            state = VideoState.Scheduled
        ) ?: return@callbackQuery
        require(application.repository.replaceVideo(newVideo)) { "Failed to update video" }
        callbackQuery.message?.let { bot.delete(it) }
        delay(3000) // only for perfect youtube thumbnail
        bot.delete(creatingMessage)
        bot.sendVideo(chatId, newVideo)
    }

    callbackQuery("VideoItemStartTestingCmd") {
        val id = callbackQueryId("VideoItemStartTestingCmd") ?: return@callbackQuery
        val video = application.repository.getVideo(id) ?: return@callbackQuery
        if (video.state != VideoState.Scheduled && video.state != VideoState.LiveTest) return@callbackQuery
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

        fun testYoutube(youtubeVideoId: String, streamKey: StreamKey.Youtube) {
            logger.info("Binding youtube stream to youtube video $youtubeVideoId ...")
            application.youtubeApi.bindBroadcastStream(youtubeVideoId, streamKey.id)
            val status = application.youtubeApi.getBroadcast(youtubeVideoId)?.status?.lifeCycleStatus
            if (status == "created" || status == "ready") {
                application.youtubeApi.transitionBroadcast(youtubeVideoId, "testing")
            }
        }
        if (lesson.streamKey is StreamKey.Youtube && video.youtubeVideoId != null) {
            testYoutube(video.youtubeVideoId, lesson.streamKey)
        }
        if (lesson.streamKey is StreamKey.Restreamer) {
            val x = listOfNotNull(
                lesson.streamKey.youtube?.let { "rtmp://a.rtmp.youtube.com/live2/${it.key}" },
            )
            application.restreamer.createStreamKey(lesson.streamKey.name, x)

            if (lesson.streamKey.youtube != null && video.youtubeVideoId != null) {
                testYoutube(video.youtubeVideoId, lesson.streamKey.youtube)
            }
            // also test vk here?
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

        fun startYoutubeStream(videoId: String): Boolean {
            val broadcastStatus = application.youtubeApi.getBroadcast(videoId)?.status?.lifeCycleStatus
            if (broadcastStatus == "created" || broadcastStatus == "ready") {
                logger.info("Testing youtube video ${videoId}...")
                application.youtubeApi.transitionBroadcast(videoId, "testing")
            } else if (broadcastStatus == "testing") {
                logger.info("Starting youtube video ${videoId}...")
                application.youtubeApi.transitionBroadcast(videoId, "live")
            }

            return broadcastStatus == "live" || broadcastStatus == "liveStarting"
        }

        var broadcastReady = false
        if (lesson.streamKey is StreamKey.Youtube && video.youtubeVideoId != null) {
            broadcastReady = startYoutubeStream(video.youtubeVideoId)
        }
        if (lesson.streamKey is StreamKey.Restreamer) {
            broadcastReady = application.restreamer.getStreamKeyStatus(lesson.streamKey.name)?.isLive == true
            if (lesson.streamKey.youtube != null) {
                broadcastReady = broadcastReady &&
                        video.youtubeVideoId != null && startYoutubeStream(video.youtubeVideoId)
            }
            if (video.vkVideoId != null && video.vkStreamKey != null) {
                logger.info("Starting VK video ${video.vkVideoId}...")
//                Not needed:
//                application.vkApi.startStream(video.vkVideoId)
                application.restreamer.addTargetToStream(lesson.streamKey.name, video.vkStreamKey)
            }
        }

        val newVideo = if (broadcastReady) {
            application.repository.getVideo(id)?.copy(state = VideoState.Live) ?: return@callbackQuery
        } else {
            video
        }
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
        application.repository.getLesson(video.lessonId.toString()) ?: return@callbackQuery
        val chatId = ChatId.fromId(callbackQuery.from.id)

        if (video.youtubeVideoId != null) {
            val broadcast = application.youtubeApi.getBroadcast(video.youtubeVideoId)
            val videoStatus = broadcast?.status?.lifeCycleStatus
            logger.info("Stopping youtube video ${video.youtubeVideoId} with status $videoStatus")
            if (videoStatus == "live") {
                application.youtubeApi.transitionBroadcast(video.youtubeVideoId, "complete")
            } else if (videoStatus != "complete") {
                bot.sendMessage(
                    chatId = ChatId.fromId(callbackQuery.from.id),
                    "Не возможно завершить трансляцию на youtube",
                    replyMarkup = InlineButtons.hideCallbackButton
                )
                return@callbackQuery
            }
        }
        if (video.vkVideoId != null) {
            logger.info("Stopping vk video ${video.vkVideoId}")
            application.vkApi.stopStream(video.vkVideoId)
        }

        val newVideo = application.repository.getVideo(id)?.copy(state = VideoState.Recorded) ?: return@callbackQuery
        require(application.repository.replaceVideo(newVideo))

        callbackQuery.message?.let { bot.delete(it) }
        bot.sendVideo(chatId, newVideo)
    }

    command("restreamer_update") {
        val chatId = ChatId.fromId(message.chat.id)
        if (args.size != 1) {
            bot.sendMessage(
                chatId,
                "Нужен 1 аргумент: id видео, на котором нужно обновить ретример, который нужно обновить"
            )
            return@command
        }

        val video = application.repository.getVideo(args[0])
        val lesson = application.repository.getLesson(video?.lessonId?.toString() ?: "")
        if (video == null || lesson == null || lesson.streamKey !is StreamKey.Restreamer) {
            bot.sendMessage(chatId, "Нет такого видео или предмета или не используется рестример")
            return@command
        }

        val x = listOfNotNull(
            lesson.streamKey.youtube?.let { "rtmp://a.rtmp.youtube.com/live2/${it.key}" },
            video.vkStreamKey,
        )
        application.restreamer.createStreamKey(lesson.streamKey.name, x)
        bot.sendMessage(chatId, "OK")
    }

    callbackQuery("VideoItemApplyToYTCmd") {
        val videoId = callbackQueryId("VideoItemApplyToYTCmd") ?: return@callbackQuery
        requireNotNull(application.repository.getVideo(videoId))

        val newMessage = bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id),
            "Пришлите id видео на ютубе или ссылку на него, чтобы применить к нему шаблон"
        ).get()
        application.userStates[callbackQuery.from.id] = UserState.ApplyingLessonTemplateToVideo(
            videoId,
            "YT",
            listOfNotNull(callbackQuery.message?.messageId, newMessage.messageId),
            application.userStates[callbackQuery.from.id],
        )
    }

    callbackQuery("VideoItemApplyToVKCmd") {
        val videoId = callbackQueryId("VideoItemApplyToVKCmd") ?: return@callbackQuery
        requireNotNull(application.repository.getVideo(videoId))

        val newMessage = bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id),
            "Пришлите id видео в вк (число, обозначенное за xxxx, в строке https://vk.com/video-211870343_xxxx), чтобы применить к нему шаблон. P.S. Потом научась парсить ссылки"
        ).get()
        application.userStates[callbackQuery.from.id] = UserState.ApplyingLessonTemplateToVideo(
            videoId,
            "VK",
            listOfNotNull(callbackQuery.message?.messageId, newMessage.messageId),
            application.userStates[callbackQuery.from.id],
        )
    }

    command("video") {
        if (args.size != 1) return@command
        val videoId = args[0]
        val video = requireNotNull(application.repository.getVideo(videoId)) { "No such video with id $videoId" }
        bot.sendVideo(ChatId.fromId(message.chat.id), video)
    }
}

private fun AdminDispatcher.generateThumbnails(
    template: ThumbnailsTemplate,
    lesson: Lesson,
    video: Video,
): Path {
    val generatedPhoto = Path.of("generated_${template.id}.png")
    generatedPhoto.outputStream().use { buffer ->
        Thumbnail.generateThumbnail(
            template,
            template.imageId?.let { application.filesRepository.getThumbnailsImagePath(it.toString()) },
            buffer,
            thumbnailsLectureNumber(lesson, video)
        )
    }
    return generatedPhoto
}
