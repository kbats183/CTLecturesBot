package ru.kbats.youtube.broadcastscheduler.bot.dispatcher

import com.github.kotlintelegrambot.entities.ChatId
import ru.kbats.youtube.broadcastscheduler.bot.AdminDispatcher
import ru.kbats.youtube.broadcastscheduler.bot.InlineButtons
import ru.kbats.youtube.broadcastscheduler.bot.callbackQueryId
import ru.kbats.youtube.broadcastscheduler.bot.infoMessage
import ru.kbats.youtube.broadcastscheduler.states.UserState

fun AdminDispatcher.setupLiveStreamsDispatcher() {
    val youtubeApi = application.youtubeApi

    callbackQuery("LiveStreamsCmd") {
        bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id),
            text = "LiveStreams",
            replyMarkup = InlineButtons.streamsMenu,
        )
    }
    callbackQuery("LiveStreamsNewCmd") {
        bot.sendMessage(ChatId.fromId(callbackQuery.from.id), text = "Enter name of new stream")
        application.userStates[callbackQuery.from.id] = UserState.CreatingNewLiveStream
    }
    callbackQuery("LiveStreamsListCmd") {
        val liveStreams = youtubeApi.getStreams().sortedBy { it.snippet.title }
        bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id), text = "List of streams",
            replyMarkup = InlineButtons.streamsNav(liveStreams)
        )
    }
    callbackQuery("LiveStreamsItemCmd") {
        val id = callbackQueryId("LiveStreamsItemCmd") ?: return@callbackQuery
        val stream = youtubeApi.getStream(id)
        if (stream == null) {
            bot.sendMessage(ChatId.fromId(callbackQuery.from.id), text = "Stream not found")
            return@callbackQuery
        }
        bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id),
            text = stream.infoMessage(),
            replyMarkup = InlineButtons.streamManage(stream)
        )
    }
    callbackQuery("LiveStreamsItemRefreshCmd") {
        val id = callbackQueryId("LiveStreamsItemRefreshCmd") ?: return@callbackQuery
        val stream = youtubeApi.getStream(id) ?: return@callbackQuery
        bot.editMessageText(
            chatId = callbackQuery.message?.chat?.id?.let { ChatId.fromId(it) },
            messageId = callbackQuery.message?.messageId,
            text = stream.infoMessage(),
            replyMarkup = InlineButtons.streamManage(stream)
        )
    }
}
