package ru.kbats.youtube.broadcastscheduler.bot.dispatcher

import com.github.kotlintelegrambot.entities.ChatId
import ru.kbats.youtube.broadcastscheduler.bot.AdminDispatcher
import ru.kbats.youtube.broadcastscheduler.bot.InlineButtons
import ru.kbats.youtube.broadcastscheduler.bot.callbackQueryId
import ru.kbats.youtube.broadcastscheduler.bot.infoMessage


fun AdminDispatcher.setupBroadcastDispatcher() {
    val youtubeApi = application.youtubeApi

    callbackQuery("BroadcastsCmd") {
        bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id), text = "Broadcasts",
            replyMarkup = InlineButtons.broadcastsMenu
        )
    }
    callbackQuery("BroadcastsActiveCmd") {
        val broadcasts = youtubeApi.getBroadcasts("active") + youtubeApi.getBroadcasts("upcoming")
        bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id), text = "Active and upcoming broadcasts",
            replyMarkup = InlineButtons.broadcastsNav(broadcasts)
        )
    }
    callbackQuery("BroadcastsItemCmd") {
        val id = callbackQueryId("BroadcastsItemCmd") ?: return@callbackQuery
        val item = youtubeApi.getBroadcast(id)
        if (item == null) {
            bot.sendMessage(ChatId.fromId(callbackQuery.from.id), text = "Broadcast not found")
            return@callbackQuery
        }
        bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id),
            text = item.infoMessage(),
            replyMarkup = InlineButtons.broadcastManage(item)
        )
    }
    callbackQuery("BroadcastsItemRefreshCmd") {
        val id = callbackQueryId("BroadcastsItemRefreshCmd") ?: return@callbackQuery
        val item = youtubeApi.getBroadcast(id) ?: return@callbackQuery
        bot.editMessageText(
            chatId = callbackQuery.message?.chat?.id?.let { ChatId.fromId(it) },
            messageId = callbackQuery.message?.messageId,
            text = item.infoMessage(),
            replyMarkup = InlineButtons.broadcastManage(item)
        )
    }
    callbackQuery("BroadcastsItemTestCmd") {
        val id = callbackQueryId("BroadcastsItemTestCmd") ?: return@callbackQuery
        val item = youtubeApi.transitionBroadcast(id, "testing") ?: return@callbackQuery
        bot.editMessageText(
            chatId = callbackQuery.message?.chat?.id?.let { ChatId.fromId(it) },
            messageId = callbackQuery.message?.messageId,
            text = item.infoMessage(),
            replyMarkup = InlineButtons.broadcastManage(item)
        )
    }
    callbackQuery("BroadcastsItemStartCmd") {
        val id = callbackQueryId("BroadcastsItemStartCmd") ?: return@callbackQuery
        val item = youtubeApi.getBroadcast(id) ?: return@callbackQuery
        bot.editMessageText(
            chatId = callbackQuery.message?.chat?.id?.let { ChatId.fromId(it) },
            messageId = callbackQuery.message?.messageId,
            text = item.infoMessage(),
            replyMarkup = InlineButtons.broadcastManage(item, confirmStart = true)
        )
    }
    callbackQuery("BroadcastsItemStartConfirmCmd") {
        val id = callbackQueryId("BroadcastsItemStartConfirmCmd") ?: return@callbackQuery
        val item = youtubeApi.transitionBroadcast(id, "live") ?: return@callbackQuery
        bot.editMessageText(
            chatId = callbackQuery.message?.chat?.id?.let { ChatId.fromId(it) },
            messageId = callbackQuery.message?.messageId,
            text = item.infoMessage(),
            replyMarkup = InlineButtons.broadcastManage(item)
        )
    }
    callbackQuery("BroadcastsItemStopCmd") {
        val id = callbackQueryId("BroadcastsItemStopCmd") ?: return@callbackQuery
        val item = youtubeApi.getBroadcast(id) ?: return@callbackQuery
        bot.editMessageText(
            chatId = callbackQuery.message?.chat?.id?.let { ChatId.fromId(it) },
            messageId = callbackQuery.message?.messageId,
            text = item.infoMessage(),
            replyMarkup = InlineButtons.broadcastManage(item, confirmStop = true)
        )
    }
    callbackQuery("BroadcastsItemStopConfirmCmd") {
        val id = callbackQueryId("BroadcastsItemStopConfirmCmd") ?: return@callbackQuery
        val item = youtubeApi.transitionBroadcast(id, "complete") ?: return@callbackQuery
        bot.editMessageText(
            chatId = callbackQuery.message?.chat?.id?.let { ChatId.fromId(it) },
            messageId = callbackQuery.message?.messageId,
            text = item.infoMessage(),
            replyMarkup = InlineButtons.broadcastManage(item)
        )
    }
}
