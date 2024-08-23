package ru.kbats.youtube.broadcastscheduler.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.HandleInlineQuery
import com.github.kotlintelegrambot.dispatcher.handlers.InlineQueryHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.InlineQuery
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.inlinequeryresults.InlineQueryResult
import com.github.kotlintelegrambot.entities.inlinequeryresults.InputMessageContent
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.google.api.services.youtube.model.LiveBroadcast
import com.google.api.services.youtube.model.LiveBroadcastStatus
import com.google.api.services.youtube.model.LiveStream
import com.google.api.services.youtube.model.Video
import ru.kbats.youtube.broadcastscheduler.data.Lecture
import ru.kbats.youtube.broadcastscheduler.data.ThumbnailsTemplate
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

val addThumbnailsImageInlineResult = InlineQueryResult.Article(
    id = "__add",
    title = "Add new Thumbnails Image",
    description = "",
    inputMessageContent = InputMessageContent.Text("To add new thumbnails image click button and than send short name of thumbnails image, for example Скаков П. С."),
    replyMarkup = InlineButtons.thumbnailsImagesNewMenu,
)

object InlineButtons {
    val mainMenu = InlineKeyboardMarkup.createSingleRowKeyboard(
        InlineKeyboardButton.CallbackData("Thumbnails", "ThumbnailsTemplatesCmd"),
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

    val thumbnailsTemplatesMenu = InlineKeyboardMarkup.createSingleRowKeyboard(
        InlineKeyboardButton.CallbackData("New", "ThumbnailsTemplatesNewCmd"),
        InlineKeyboardButton.SwitchInlineQueryCurrentChat("List", "ThumbnailsTemplates"),
        InlineKeyboardButton.CallbackData("Images", "ThumbnailsImagesCmd"),
    )

    fun thumbnailsTemplateManage(template: ThumbnailsTemplate): InlineKeyboardMarkup {
        fun editProperty(name: String, title: String): InlineKeyboardButton.CallbackData {
            return InlineKeyboardButton.CallbackData(
                "Изменить $title",
                "ThumbnailsTemplatesItemEdit${name}Cmd${template.id}"
            )
        }

        return InlineKeyboardMarkup.create(
            listOf(
                editProperty("Name", "название"),
                editProperty("Title", "заголовок"),
            ), listOf(
                editProperty("Lecturer", "лектора"),
                editProperty("Term", "семестр")
            ), listOf(
                editProperty("Color", "цвет"),
                editProperty("Image", "картинку"),
            )
        )
    }

    val thumbnailsTemplateEditImage = InlineKeyboardMarkup.createSingleButton(
        InlineKeyboardButton.SwitchInlineQueryCurrentChat(
            "Выбрать",
            "ThumbnailsImages"
        )
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

val colorsTgExample = "\uD83D\uDD34 \\- `tart`\n" +
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

        val thumbs = itemsSupplier()
            .dropWhile { inlineQuery.offset != "" && inlineQuery.offset != it.id }
            .take(50)

        val stuffResult = addItems.filter { inlineQuery.offset == "" }

        bot.answerInlineQuery(
            inlineQueryId = inlineQuery.id,
            inlineQueryResults = stuffResult + thumbs.take(50 - stuffResult.size),
            nextOffset = thumbs.getOrNull(50 - stuffResult.size)?.id ?: "end"
        )
    }
}

val thumbnailsTemplateIdRegexp = "thumbnails\\_template\\_([0-9a-f]+)".toRegex()
val thumbnailsImageIdRegexp = "thumbnails\\_image\\_([0-9a-f]+)".toRegex()
