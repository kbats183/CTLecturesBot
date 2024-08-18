package ru.kbats.youtube.broadcastscheduler

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.logging.LogLevel
import com.google.api.services.youtube.model.LiveBroadcast
import com.google.api.services.youtube.model.LiveBroadcastStatus
import com.google.api.services.youtube.model.LiveStream
import com.google.api.services.youtube.model.Video
import ru.kbats.youtube.broadcastscheduler.bot.setupDispatcher
import ru.kbats.youtube.broadcastscheduler.data.Lecture
import ru.kbats.youtube.broadcastscheduler.states.UserStateStorage
import ru.kbats.youtube.broadcastscheduler.youtube.YoutubeApi
import ru.kbats.youtube.broadcastscheduler.youtube.getCredentials

class Application(private val config: Config) {
    internal val youtubeApi = YoutubeApi(getCredentials(System.getenv("YT_ENV") ?: "ct_lectures")!!)
    internal val repository = getRepository(config)
    internal val userStates = UserStateStorage()

    fun run() {
        val bot = bot {
            logLevel = LogLevel.Error
            token = config.botApiToken
            dispatch {
                message {
                    println("${message.document}")
                }
                setupDispatcher(this)
            }
        }
        bot.startPolling()
    }

    internal fun CallbackQueryHandlerEnvironment.callbackQueryId(commandPrefix: String): String? {
        if (callbackQuery.data.startsWith(commandPrefix)) {
            return callbackQuery.data.substring(commandPrefix.length)
        }
        return null
    }

    fun LiveStream.infoMessage() = "Live Stream ${snippet.title}\n" +
            "Stream id: ${id}\n" +
            "OBS key: " + cdn.ingestionInfo.streamName +
            (status?.let { "\nStatus: ${it.streamStatus}, ${it.healthStatus.status}" } ?: "")

    fun LiveBroadcast.infoMessage(): String = "Broadcast ${snippet.title}\n" +
            "Start time: ${snippet.actualStartTime ?: snippet.scheduledStartTime}\n" +
            "End time: ${snippet.actualEndTime ?: snippet.scheduledEndTime}\n" +
            "Status: ${status.emojy()}${status.lifeCycleStatus}\n" +
            "Privacy: ${status.privacyStatus}\n" +
            "Thumbnails: ${snippet.thumbnails?.maxres?.url?.withUpdateUrlSuffix() ?: "no"}\n" +
//                    "StramID: ${contentDetails.boundStreamId}\n" +
            "View: https://www.youtube.com/watch?v=${id}\n" +
            "Manage: https://studio.youtube.com/video/${id}/livestreaming"

    fun Video.infoMessage() = "Video ${snippet.title}\n" +
            "Status: ${status.uploadStatus}\n" +
            "Privacy: ${status.privacyStatus}\n" +
            "Thumbnails: ${snippet.thumbnails?.maxres?.url?.withUpdateUrlSuffix() ?: "no"}\n" +
            "View: https://www.youtube.com/watch?v=${id}\n" +
            "Manage: https://studio.youtube.com/video/${id}"

    companion object {
        const val thumbnailsDirectory = "thumbnails"

        private fun LiveBroadcastStatus.emojy(): String = when (this.lifeCycleStatus) {
            "complete" -> "☑️"
            "live" -> "\uD83D\uDFE2"
            "created" -> "\uD83D\uDD54"
            "ready" -> "\uD83D\uDD34"
            "testing", "testStarting", "liveStarting" -> "\uD83D\uDFE1"
            else -> "[" + this.lifeCycleStatus + "]"
        } + " "

        object InlineButtons {
            val mainMenu = InlineKeyboardMarkup.createSingleRowKeyboard(
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
        }
    }
}


fun main() {
//    println("${Clock.System.now().epochSeconds}")
//    println("${Clock.System.now().toLocalDateTime(timeZone)}")
    val application = Application(config())
    println("Hello!")
    application.run()
}
