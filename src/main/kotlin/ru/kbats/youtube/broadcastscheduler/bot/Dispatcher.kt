package ru.kbats.youtube.broadcastscheduler.bot

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.entities.ChatId
import ru.kbats.youtube.broadcastscheduler.Application
import ru.kbats.youtube.broadcastscheduler.YoutubeVideoIDMatcher
import ru.kbats.youtube.broadcastscheduler.bot.dispatcher.setupBroadcastDispatcher
import ru.kbats.youtube.broadcastscheduler.bot.dispatcher.setupLecturesDispatcher
import ru.kbats.youtube.broadcastscheduler.bot.dispatcher.setupLiveStreamsDispatcher
import ru.kbats.youtube.broadcastscheduler.states.BotUserState

fun Application.setupDispatcher(dispatcher: Dispatcher) {
    dispatcher.withAdminRight(this) {
        text {
            val chatId = ChatId.fromId(message.chat.id)
            if (text.startsWith("/addAdmin")) {
                val components = text.split("\n")
                if (components.size != 3) {
                    bot.sendMessage(chatId, "Incorrect input")
                    return@text
                }
                repository.addAdmin(components[1], components[2])
                bot.sendMessage(chatId, "Ok")
                return@text
            }
            if ("/cancel" in text) {
                userStates[message.chat.id] = BotUserState.Default
                return@text
            }
            when (val state = userStates[message.chat.id]) {
                is BotUserState.CreatingNewLiveStream -> {
                    val newLiveStream = youtubeApi.createStream(text)
                    bot.sendMessage(chatId, newLiveStream.infoMessage())
                    userStates[message.chat.id] = BotUserState.Default
                }

                is BotUserState.ApplyingTemplateToVideo -> {
                    val videoId = YoutubeVideoIDMatcher.match(text) ?: return@text Unit.also {
                        bot.sendMessage(chatId, text = "Invalid video id or url")
                    }
                    if (youtubeApi.getVideo(videoId) == null) {
                        bot.sendMessage(chatId, text = "No video with id $videoId")
                        return@text
                    }
                    val lecture = repository.getLecture(state.lectureId) ?: return@text Unit.also {
                        bot.sendMessage(chatId, text = "No such lecture")
                    }
                    val applyingMessage = bot.sendMessage(chatId, text = "Applying ...")
                    val video = applyTemplateToVideo(videoId, lecture)
                    applyingMessage.getOrNull()?.let { bot.delete(it) }

                    if (video == null) {
                        bot.sendMessage(chatId, "Failed to schedule stream")
                        return@text
                    }
                    bot.sendMessage(chatId, text = video.infoMessage())
                    userStates[message.chat.id] = BotUserState.Default
                }

                else -> {
                    bot.sendMessage(
                        chatId, text = "Main menu",
                        replyMarkup = InlineButtons.mainMenu,
                    )
                }
            }
        }
        callbackQuery("HideCallbackMessageCmd") {
            bot.deleteMessage(
                chatId = callbackQuery.message?.chat?.id?.let { ChatId.fromId(it) } ?: return@callbackQuery,
                messageId = callbackQuery.message?.messageId ?: return@callbackQuery,
            )
        }

        setupLiveStreamsDispatcher()
        setupBroadcastDispatcher()
        setupLecturesDispatcher()
    }
}

