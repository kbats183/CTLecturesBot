package ru.kbats.youtube.broadcastscheduler.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.InlineQueryHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.InlineQuery
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.inlinequeryresults.InlineQueryResult
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.google.api.services.youtube.model.LiveBroadcast
import com.google.api.services.youtube.model.LiveBroadcastStatus
import com.google.api.services.youtube.model.LiveStream
import com.google.api.services.youtube.model.Video
import ru.kbats.youtube.broadcastscheduler.data.*
import ru.kbats.youtube.broadcastscheduler.states.UserState
import ru.kbats.youtube.broadcastscheduler.withUpdateUrlSuffix

internal fun CallbackQueryHandlerEnvironment.callbackQueryId(commandPrefix: String): String? {
    if (callbackQuery.data.startsWith(commandPrefix)) {
        return callbackQuery.data.substring(commandPrefix.length)
    }
    return null
}

internal fun Bot.delete(message: Message) {
    deleteMessage(ChatId.fromId(message.chat.id), message.messageId)
}

fun LiveStream.infoMessage() = "Live Stream ${snippet.title}\n" +
        "Stream id: ${id}\n" +
        "OBS key: " + cdn.ingestionInfo.streamName +
        (status?.let { "\nStatus: ${it.streamStatus}, ${it.healthStatus.status}" } ?: "")


fun Video.infoMessage() = "Video ${snippet.title}\n" +
        "Status: ${status.uploadStatus}\n" +
        "Privacy: ${status.privacyStatus}\n" +
        "Thumbnails: ${snippet.thumbnails?.maxres?.url?.withUpdateUrlSuffix() ?: "no"}\n" +
        "View: https://www.youtube.com/watch?v=${id}\n" +
        "Manage: https://studio.youtube.com/video/${id}"

fun LiveBroadcast.infoMessage(): String = "Broadcast ${snippet.title}\n" +
        "Start time: ${snippet.actualStartTime ?: snippet.scheduledStartTime}\n" +
        "End time: ${snippet.actualEndTime ?: snippet.scheduledEndTime}\n" +
        "Status: ${status.emojy()}${status.lifeCycleStatus}\n" +
        "Privacy: ${status.privacyStatus}\n" +
        "Thumbnails: ${snippet.thumbnails?.maxres?.url?.withUpdateUrlSuffix() ?: "no"}\n" +
//                    "StramID: ${contentDetails.boundStreamId}\n" +
        "View: https://www.youtube.com/watch?v=${id}\n" +
        "Manage: https://studio.youtube.com/video/${id}/livestreaming"

internal fun LiveBroadcastStatus.emojy(): String = when (this.lifeCycleStatus) {
    "complete" -> "☑️"
    "live" -> "\uD83D\uDFE2"
    "created" -> "\uD83D\uDD54"
    "ready" -> "\uD83D\uDD34"
    "testing", "testStarting", "liveStarting" -> "\uD83D\uDFE1"
    else -> "[" + this.lifeCycleStatus + "]"
} + " "

object InlineButtons {
    val mainMenu = InlineKeyboardMarkup.createSingleRowKeyboard(
        InlineKeyboardButton.CallbackData("Thumbnails", "ThumbnailsTemplatesCmd"),
        InlineKeyboardButton.CallbackData("Lessons", "LessonsCmd"),
//        InlineKeyboardButton.CallbackData("Videos", "Video"),
    )
    val oldMainMenu = InlineKeyboardMarkup.createSingleRowKeyboard(
        InlineKeyboardButton.CallbackData("Streams", "LiveStreamsCmd"),
        InlineKeyboardButton.CallbackData("Broadcasts", "BroadcastsCmd"),
        InlineKeyboardButton.CallbackData("Lectures", "LecturesCmd"),
    )
    val streamsMenu = InlineKeyboardMarkup.createSingleRowKeyboard(
        InlineKeyboardButton.CallbackData("List", "LiveStreamsListCmd"),
        InlineKeyboardButton.CallbackData("New", "LiveStreamsNewCmd"),
        InlineKeyboardButton.CallbackData("Hide", "HideCallbackMessageCmd"),
    )
    val broadcastsMenu = InlineKeyboardMarkup.createSingleRowKeyboard(
        InlineKeyboardButton.CallbackData("Active and upcoming", "BroadcastsActiveCmd"),
        InlineKeyboardButton.CallbackData("New", "BroadcastsNewCmd"),
        InlineKeyboardButton.CallbackData("Hide", "HideCallbackMessageCmd"),
    )

    private fun gridNav(
        commandPrefix: String,
        items: List<Pair<String, String>>,
        itemsPerRow: Int = 4,
        lastRow: List<InlineKeyboardButton> = mutableListOf(
            InlineKeyboardButton.CallbackData(
                "Hide",
                "HideCallbackMessageCmd"
            )
        )
    ): InlineKeyboardMarkup {
        return InlineKeyboardMarkup.create(items.map {
            InlineKeyboardButton.CallbackData(
                it.second,
                commandPrefix + it.first
            )
        }.fold(mutableListOf<MutableList<InlineKeyboardButton>>()) { rows, it ->
            (rows.lastOrNull()?.takeIf { it.size < itemsPerRow }
                ?: mutableListOf<InlineKeyboardButton>().also { rows += it }) += it
            return@fold rows
        } + listOf(lastRow))
    }

    fun streamsNav(liveStreams: List<LiveStream>) =
        gridNav("LiveStreamsItemCmd", liveStreams.map { it.id to it.snippet.title })

    fun streamManage(stream: LiveStream) =
        InlineKeyboardMarkup.createSingleRowKeyboard(
            InlineKeyboardButton.CallbackData("Refresh", "LiveStreamsItemRefreshCmd${stream.id}"),
            InlineKeyboardButton.CallbackData("Hide", "HideCallbackMessageCmd"),
        )

    fun broadcastsNav(liveStreams: List<LiveBroadcast>) =
        gridNav(
            "BroadcastsItemCmd",
            liveStreams.map { it.id to (it.status.emojy() + it.snippet?.title) }, 1
        )

    fun <T> MutableList<T>.addIf(condition: Boolean, value: T) {
        if (condition) add(value)
    }

    fun broadcastManage(
        broadcast: LiveBroadcast,
        confirmStart: Boolean = false,
        confirmStop: Boolean = false
    ): InlineKeyboardMarkup {
        val buttons = mutableListOf<List<InlineKeyboardButton>>(
            listOf(
                InlineKeyboardButton.CallbackData("Refresh", "BroadcastsItemRefreshCmd${broadcast.id}"),
                InlineKeyboardButton.CallbackData("Hide", "HideCallbackMessageCmd")
            )
        )
        buttons.addIf(
            broadcast.contentDetails?.boundStreamId != null,
            if (broadcast.status.lifeCycleStatus == "ready") {
                listOf(
                    InlineKeyboardButton.CallbackData(
                        "Bound stream",
                        "LiveStreamsItemCmd${broadcast.contentDetails?.boundStreamId}"
                    ),
                    InlineKeyboardButton.CallbackData(
                        "Test and start",
                        "BroadcastsItemTestCmd${broadcast.id}"
                    )
                )
            } else {
                listOf(
                    InlineKeyboardButton.CallbackData(
                        "Bound stream",
                        "LiveStreamsItemCmd${broadcast.contentDetails?.boundStreamId}"
                    )
                )
            }
        )
        buttons.addIf(
            broadcast.status.lifeCycleStatus == "testing" && !confirmStart, listOf(
                InlineKeyboardButton.CallbackData(
                    "Start stream \uD83D\uDFE2", "BroadcastsItemStartCmd${broadcast.id}"
                )
            )
        )
        buttons.addIf(
            broadcast.status.lifeCycleStatus == "testing" && confirmStart, listOf(
                InlineKeyboardButton.CallbackData(
                    "Confirm start stream", "BroadcastsItemStartConfirmCmd${broadcast.id}"
                )
            )
        )
        buttons.addIf(
            broadcast.status.lifeCycleStatus == "live" && !confirmStop, listOf(
                InlineKeyboardButton.CallbackData(
                    "Stop stream \uD83D\uDFE5", "BroadcastsItemStopCmd${broadcast.id}"
                )
            )
        )
        buttons.addIf(
            broadcast.status.lifeCycleStatus == "live" && confirmStop, listOf(
                InlineKeyboardButton.CallbackData(
                    "Confirm stop stream", "BroadcastsItemStopConfirmCmd${broadcast.id}"
                )
            )
        )
        return InlineKeyboardMarkup.create(buttons)
    }

    fun lecturesNav(lectures: List<Lecture>) =
        gridNav(
            "LecturesItemCmd", lectures.map { it.id.toString() to it.name },
            lastRow = listOf(
                InlineKeyboardButton.CallbackData("New", "LecturesNewCmd"),
                InlineKeyboardButton.CallbackData("Refresh", "LecturesRefreshCmd"),
                InlineKeyboardButton.CallbackData("Hide", "HideCallbackMessageCmd"),
            )
        )


    fun lectureManage(lecture: Lecture): InlineKeyboardMarkup {
        val buttons = mutableListOf<List<InlineKeyboardButton>>(
            listOf(
                InlineKeyboardButton.CallbackData("Refresh", "LecturesItemRefreshCmd${lecture.id}"),
                InlineKeyboardButton.CallbackData("Hide", "HideCallbackMessageCmd")
            ),
            listOf(
                InlineKeyboardButton.CallbackData("Previous number", "LecturesItemPrevNumberCmd${lecture.id}"),
                InlineKeyboardButton.CallbackData("Next number", "LecturesItemNextNumberCmd${lecture.id}")
            )
        )

        if (lecture.thumbnails != null) {
            buttons.add(
                listOf(
                    InlineKeyboardButton.CallbackData("Thumbnails", "LecturesItemThumbnailsCmd${lecture.id}"),
                    InlineKeyboardButton.CallbackData(
                        "Apply template to",
                        "LecturesItemApplyTemplateCmd${lecture.id}"
                    ),
                )
            )
        }
        if (lecture.scheduling != null) {
            buttons.add(
                listOf(
                    InlineKeyboardButton.CallbackData(
                        "Schedule broadcast",
                        "LecturesItemSchedulingCmd${lecture.id}"
                    )
                )
            )
        }
        return InlineKeyboardMarkup.create(buttons)
    }

    val thumbnailsImagesMenu = InlineKeyboardMarkup.createSingleRowKeyboard(
        InlineKeyboardButton.CallbackData("New", "ThumbnailsImagesNewCmd"),
        InlineKeyboardButton.SwitchInlineQueryCurrentChat("List", "ThumbnailsImages")
    )

    val thumbnailsImagesNewMenu = InlineKeyboardMarkup.createSingleRowKeyboard(
        InlineKeyboardButton.CallbackData("New", "ThumbnailsImagesNewCmd")
    )

    fun thumbnailsImagesManage(image: ThumbnailsImage, state: UserState) =
        InlineKeyboardMarkup.create(
            listOfNotNull(InlineKeyboardButton.CallbackData(
                "Назад ⬅\uFE0F",
                "ThumbnailsImageItemBackCmd${image.id}"
            ).takeIf { state != UserState.Default }),
        )

    val thumbnailsTemplatesMenu = InlineKeyboardMarkup.createSingleRowKeyboard(
        InlineKeyboardButton.CallbackData("New", "ThumbnailsTemplatesNewCmd"),
        InlineKeyboardButton.SwitchInlineQueryCurrentChat("List", "ThumbnailsTemplates"),
        InlineKeyboardButton.CallbackData("Images", "ThumbnailsImagesCmd"),
    )

    fun thumbnailsTemplateManage(template: ThumbnailsTemplate, state: UserState): InlineKeyboardMarkup {
        fun editProperty(title: String, name: String): InlineKeyboardButton.CallbackData {
            return InlineKeyboardButton.CallbackData(
                "Изменить $title",
                "ThumbnailsTemplatesItemEdit${name}Cmd${template.id}"
            )
        }

        return InlineKeyboardMarkup.create(
            listOfNotNull(InlineKeyboardButton.CallbackData(
                "Назад ⬅\uFE0F",
                "ThumbnailsTemplatesItemBackCmd"
            ).takeIf { state != UserState.Default }),
            listOf(
                editProperty("название", "Name"),
                editProperty("заголовок", "Title"),
            ), listOf(
                editProperty("лектора", "Lecturer"),
                editProperty("семестр", "Term")
            ), listOf(
                editProperty("цвет", "Color"),
                editProperty("картинку", "Image"),
            )
        )
    }

    val thumbnailsTemplateEditImage = InlineKeyboardMarkup.createSingleButton(
        InlineKeyboardButton.SwitchInlineQueryCurrentChat(
            "Выбрать",
            "ThumbnailsImages"
        )
    )

    val lessonsMenu = InlineKeyboardMarkup.createSingleRowKeyboard(
        InlineKeyboardButton.CallbackData("Добавить", "LessonsNewCmd"),
        InlineKeyboardButton.SwitchInlineQueryCurrentChat("Список", "Lessons")
    )

    fun lessonManage(lesson: Lesson): InlineKeyboardMarkup {
        fun b(title: String, name: String) = InlineKeyboardButton.CallbackData(title, "Lesson${name}Cmd${lesson.id}")

        return InlineKeyboardMarkup.create(
            listOfNotNull(
                b(
                    "Добавить видео (${lesson.currentLectureNumber})",
                    "AddVideoAsk"
                ).takeIf { lesson.mainTemplateId != null },
                b("Выбрать шаблон превью", "SettingsEditThumbnailsTemplate").takeIf { lesson.mainTemplateId == null },
                InlineKeyboardButton.SwitchInlineQueryCurrentChat("Все видео", "VideosLesson${lesson.id} "),
            ), listOf(
                b("Номер - 1", "ChangeNumberDec"),
                b("Номер + 1", "ChangeNumberInc"),
            ), listOfNotNull(
                b("Создать плейлист Youtube", "CreatePlaylistYT").takeIf { lesson.youtubePlaylistId == null },
                b("Создать плейлист ВК", "CreatePlaylistVK").takeIf { lesson.vkPlaylistId == null },
            ), listOf(
                b("Настройки", "SettingsMenu"),
//                editProperty("Delete", "Удалить"),
            )
        )
    }

    fun lessonSettings(lesson: Lesson): InlineKeyboardMarkup {
        fun b(title: String, name: String) =
            InlineKeyboardButton.CallbackData(title, "LessonSettings${name}Cmd${lesson.id}")

        return InlineKeyboardMarkup.create(
            listOf(
                b("✏\uFE0F  название", "EditName"),
                b("✏\uFE0F  заголовок видео", "EditTitle"),
            ),
            listOf(
                b("✏\uFE0F  лектора", "EditLecturer"),
                b("✏\uFE0F  номер семестра", "EditTermNumber"),
            ),
            listOf(
                b("✏\uFE0F  шаблон превью", "EditThumbnailsTemplate"),
                b("✏\uFE0F  ключ трансляции", "EditStreamKey"),
            ),
            listOf(
                b("✏\uFE0F  тип доступа", "EditPrivacy"),
                b("✏\uFE0F  тип записей", "EditType"),
            ),
            listOf(
                b("Назад", "Back")
            ),
        )
    }


    fun lessonsEditThumbnailsMenu(lesson: Lesson) = InlineKeyboardMarkup.create(
        listOfNotNull(
            InlineKeyboardButton.CallbackData("Создать новый для предмета", "LessonsEditThumbnailsTemplateNewCmd"),
            InlineKeyboardButton.SwitchInlineQueryCurrentChat("Выбрать существующий", "ThumbnailsTemplates"),
        ),
        listOfNotNull(
            InlineKeyboardButton.CallbackData("Изменить текущий", "LessonsEditThumbnailsTemplateEditCmd")
                .takeIf { lesson.mainTemplateId != null },
            InlineKeyboardButton.CallbackData("Отмена", "LessonsSettingsEditThumbnailsTemplateCancelCmd")
        )
    )

    fun videoManage(
        video: ru.kbats.youtube.broadcastscheduler.data.Video,
        ytVideo: LiveBroadcast?
    ): InlineKeyboardMarkup {
        fun b(title: String, name: String) = InlineKeyboardButton.CallbackData(title, "VideoItem${name}Cmd${video.id}")

        return InlineKeyboardMarkup.create(
            listOfNotNull(
                listOfNotNull(
                    b("Обложка видео", "ThumbnailsGen"),
                    b("Обновить", "Refresh"),
                ), listOf(
                    b("Запланировать трансляцию", "ScheduleStreamAsk"),
//                    b("❌Загрузить запись❌", "UploadRecord"),
//                b("Применить к загруженному", "UploadRecord"),
                ).takeIf { video.state == VideoState.New }, listOf(
                    b("Применить к YT видео", "ApplyToYT"),
                    b("Применить к VK видео", "ApplyToVK"),
                ).takeIf { video.state == VideoState.New || video.state == VideoState.Recorded }, listOfNotNull(
                    b("Начать предпросмотр трансляции", "StartTesting").takeIf {
                        video.state == VideoState.Scheduled || video.state == VideoState.LiveTest && ytVideo?.status?.lifeCycleStatus == "ready"
                    },
                    b("Начать трансляцию", "StartStreaming")
                        .takeIf { video.state == VideoState.LiveTest && (ytVideo == null || ytVideo.status.lifeCycleStatus.let { it == "testing" }) },
                    b("Обновить состояние", "StartStreaming")
                        .takeIf { video.state == VideoState.LiveTest && (ytVideo != null && ytVideo.status.lifeCycleStatus.let { it == "liveStarting" || it == "live" || it == "complete" }) },
                    b("Закончить трансляцию", "StopStreaming").takeIf { video.state == VideoState.Live },
                ), listOfNotNull(
                    b("✏\uFE0F номер лекции", "EditLectureNumber").takeIf { video.customTitle == null },
                    b("\uD83D\uDEE0 специальное название", "EditCustomTitle"),
                ).takeIf { video.state == VideoState.New }, listOf(
                    InlineKeyboardButton.CallbackData("Скрыть", "HideCallbackMessageCmd")
                )
            )
        )
    }

    fun lessonsEditStreamKey(lesson: Lesson) = InlineKeyboardMarkup.create(
        listOf(
            InlineKeyboardButton.CallbackData("Использовать рестример", "LessonsEditStreamKeyRestreamerCmd"),
            InlineKeyboardButton.SwitchInlineQueryCurrentChat("Только youtube", "StreamKeyForLessons"),
        ),
        listOf(
            InlineKeyboardButton.CallbackData("Отмена", "LessonsEditStreamKeyCancelCmd")
        )
    )

    fun videoStopStream(video: ru.kbats.youtube.broadcastscheduler.data.Video): InlineKeyboardMarkup {
        return InlineKeyboardMarkup.create(
            listOf(InlineKeyboardButton.CallbackData("Завершить", "VideoItemStopStreamingConfirmCmd${video.id}")),
            listOf(InlineKeyboardButton.CallbackData("Отмена", "VideoItemStopStreamingCancelCmd${video.id}")),
        )
    }

    fun addVideoNewVideoAsk(lesson: Lesson): InlineKeyboardMarkup {
        return InlineKeyboardMarkup.create(
            listOf(InlineKeyboardButton.CallbackData("Добавить", "LessonAddVideoCmd${lesson.id}")),
            listOf(InlineKeyboardButton.CallbackData("Отмена", "HideCallbackMessageCmd")),
        )
    }

    fun scheduleStreamVideoAsk(video: ru.kbats.youtube.broadcastscheduler.data.Video): InlineKeyboardMarkup {
        return InlineKeyboardMarkup.create(
            listOf(InlineKeyboardButton.CallbackData("Запланировать трансляцию", "VideoItemScheduleStreamCmd${video.id}")),
            listOf(InlineKeyboardButton.CallbackData("Отмена", "HideCallbackMessageCmd")),
        )
    }

    val hideCallbackButton = InlineKeyboardMarkup.createSingleRowKeyboard(
        InlineKeyboardButton.CallbackData("Скрыть", "HideCallbackMessageCmd")
    )
}

val String.escapeMarkdown
    get() = this
        .replace("_", "\\_")
        .replace("*", "\\*")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .replace("~", "\\~")
        .replace("`", "\\`")
        .replace(">", "\\>")
        .replace("#", "\\#")
        .replace("+", "\\+")
        .replace("-", "\\-")
        .replace("=", "\\=")
        .replace("|", "\\|")
        .replace("{", "\\{")
        .replace("}", "\\}")
        .replace(".", "\\.")
        .replace("!", "\\!")

val itmoColors = arrayListOf("tart", "honey", "yellow", "green", "capri", "bluetiful", "violet", "pink")
val itmoColorsTgExample = "\uD83D\uDD34 \\- `tart`\n" +
        "\uD83D\uDFE0 \\- `honey`\n" +
        "\uD83D\uDFE1 \\- `yellow`\n" +
        "\uD83D\uDFE2 \\- `green`\n" +
        "\uD83C\uDF10 \\- `capri`\n" +
        "\uD83D\uDD35 \\- `bluetiful`\n" +
        "\uD83D\uDFE3 \\- `violet`\n" +
        "\uD83D\uDC5A \\- `pink` или другой rgb \\#012345"

suspend fun InlineQueryHandlerEnvironment.renderInlineListItems(
    inlinePrefix: String,
    addItems: List<InlineQueryResult> = emptyList(),
    itemsSupplier: suspend () -> List<InlineQueryResult>
) {
    if (inlineQuery.query.startsWith(inlinePrefix) && inlineQuery.chatType == InlineQuery.ChatType.SENDER) {
        if (inlineQuery.offset == "end") {
            return
        }

        val items = itemsSupplier()
            .dropWhile { inlineQuery.offset != "" && inlineQuery.offset != it.id }
            .take(50)

        val stuffResult = addItems.filter { inlineQuery.offset == "" }

        bot.answerInlineQuery(
            inlineQueryId = inlineQuery.id,
            inlineQueryResults = stuffResult + items.take(50 - stuffResult.size),
            nextOffset = items.getOrNull(50 - stuffResult.size)?.id ?: "end"
        ).get()
    }
}

fun LectureType.toTitle() = when (this) {
    LectureType.Lecture -> "лекция"
    LectureType.Practice -> "практика"
}

fun VideoState.toTitle() = when (this) {
    VideoState.New -> "создано"
    VideoState.Scheduled -> "запланировано"
    VideoState.LiveTest -> "предпросмотр"
    VideoState.Live -> "транслируется"
    VideoState.Recorded -> "загружена запись"
}

fun LectureBroadcastPrivacy.toTitle() = when (this) {
    LectureBroadcastPrivacy.Public -> "публично"
    LectureBroadcastPrivacy.Unlisted -> "по ссылке"
}

fun StreamKey?.toTitle() = when (this) {
    is StreamKey.Youtube -> "Youtube ${name.escapeMarkdown} \\(`${key}`\\)"
    is StreamKey.Restreamer -> "Рестример `${name.escapeMarkdown}`"
    else -> "Не задан"
}

val thumbnailsTemplateIdRegexp = "thumbnails\\_template\\_([0-9a-f]+)".toRegex()
val thumbnailsImageIdRegexp = "thumbnails\\_image\\_([0-9a-f]+)".toRegex()
val lessonIdRegexp = "lesson\\_([0-9a-f]+)".toRegex()
val videoIdRegexp = "video\\_([0-9a-f]+)".toRegex()
val lessonStreamKeyIdRegexp = "lesson\\_stream\\_key\\_([0-9a-zA-Z\\-]+)".toRegex()
