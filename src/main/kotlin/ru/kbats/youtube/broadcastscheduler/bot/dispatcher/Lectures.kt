package ru.kbats.youtube.broadcastscheduler.bot.dispatcher

import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.TelegramFile
import ru.kbats.youtube.broadcastscheduler.Application
import ru.kbats.youtube.broadcastscheduler.bot.*
import ru.kbats.youtube.broadcastscheduler.data.Lecture
import ru.kbats.youtube.broadcastscheduler.states.UserState
import ru.kbats.youtube.broadcastscheduler.thumbnail.Thumbnail
import kotlin.io.path.Path

fun AdminDispatcher.setupLecturesDispatcher() {
    val repository = application.repository

    callbackQuery("LecturesCmd") {
        bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id), text = "Lectures",
            replyMarkup = InlineButtons.lecturesNav(repository.getLectures())
        )
    }
    callbackQuery("LecturesRefreshCmd") {
        bot.editMessageText(
            chatId = callbackQuery.message?.chat?.id?.let { ChatId.fromId(it) },
            messageId = callbackQuery.message?.messageId,
            text = "Lectures",
            replyMarkup = InlineButtons.lecturesNav(repository.getLectures()),
        )
    }
    callbackQuery("LecturesItemCmd") {
        val id = callbackQueryId("LecturesItemCmd") ?: return@callbackQuery
        val lecture = repository.getLecture(id) ?: return@callbackQuery
        bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id), text = lecture.infoMessage(),
            replyMarkup = InlineButtons.lectureManage(lecture)
        )
    }
    fun CallbackQueryHandlerEnvironment.editLectureMessage(lecture: Lecture) {
        bot.editMessageText(
            chatId = callbackQuery.message?.chat?.id?.let { ChatId.fromId(it) },
            messageId = callbackQuery.message?.messageId,
            text = lecture.infoMessage(),
            replyMarkup = InlineButtons.lectureManage(lecture)
        )
    }
    callbackQuery("LecturesItemRefreshCmd") {
        val id = callbackQueryId("LecturesItemRefreshCmd") ?: return@callbackQuery
        val lecture = repository.getLecture(id) ?: return@callbackQuery
        editLectureMessage(lecture)
    }
    callbackQuery("LecturesItemPrevNumberCmd") {
        val id = callbackQueryId("LecturesItemPrevNumberCmd") ?: return@callbackQuery
        repository.updateLecture(id) { it.copy(currentLectureNumber = it.currentLectureNumber - 1) }
        val lecture = repository.getLecture(id) ?: return@callbackQuery
        editLectureMessage(lecture)
    }
    callbackQuery("LecturesItemNextNumberCmd") {
        val id = callbackQueryId("LecturesItemNextNumberCmd") ?: return@callbackQuery
        repository.updateLecture(id) { it.copy(currentLectureNumber = it.currentLectureNumber + 1) }
        val lecture = repository.getLecture(id) ?: return@callbackQuery
        editLectureMessage(lecture)
    }
    callbackQuery("LecturesItemThumbnailsCmd") {
        val id = callbackQueryId("LecturesItemThumbnailsCmd") ?: return@callbackQuery
        val lecture = repository.getLecture(id) ?: return@callbackQuery
        if (lecture.thumbnails == null) {
            bot.sendMessage(ChatId.fromId(callbackQuery.from.id), text = "No thumbnails info")
            return@callbackQuery
        }

        val generatingMessage = bot.sendMessage(ChatId.fromId(callbackQuery.from.id), text = "Generating ...")
        try {
            val generateFile = Thumbnail.generate(
                Path(Application.thumbnailsDirectory),
                lecture.thumbnails,
                lecture.currentThumbnailLecture()
            )
            bot.sendDocument(
                ChatId.fromId(callbackQuery.from.id),
                TelegramFile.ByFile(generateFile),
            )
            generatingMessage.getOrNull()?.let { bot.delete(it) }
        } catch (e: Throwable) {
            println(e.message ?: e::class.java.name)
            bot.sendMessage(ChatId.fromId(callbackQuery.from.id), text = e.message ?: e::class.java.name)
        }
    }
    callbackQuery("LecturesItemSchedulingCmd") {
        val id = callbackQueryId("LecturesItemSchedulingCmd") ?: return@callbackQuery
        val lecture = repository.getLecture(id) ?: return@callbackQuery
        if (lecture.scheduling == null) {
            bot.sendMessage(ChatId.fromId(callbackQuery.from.id), text = "No scheduling info")
            return@callbackQuery
        }
        val schedulingMessage = bot.sendMessage(ChatId.fromId(callbackQuery.from.id), text = "Scheduling ...")
        val scheduledStream = application.scheduleStream(lecture)
        if (scheduledStream == null) {
            schedulingMessage.getOrNull()?.let { bot.delete(it) }
            bot.sendMessage(ChatId.fromId(callbackQuery.from.id), "Failed to schedule stream")
            return@callbackQuery
        }
        schedulingMessage.getOrNull()?.let { bot.delete(it) }
        bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id),
            text = scheduledStream.infoMessage(),
            replyMarkup = InlineButtons.broadcastManage(scheduledStream)
        )
    }
    callbackQuery("LecturesItemApplyTemplateCmd") {
        val id = callbackQueryId("LecturesItemApplyTemplateCmd") ?: return@callbackQuery
        repository.getLecture(id) ?: return@callbackQuery
        application.userStates[callbackQuery.from.id] = UserState.ApplyingTemplateToVideo(id)
        bot.sendMessage(
            ChatId.fromId(callbackQuery.from.id),
            text = "Send video id or url to apply lecture template or click /cancel"
        )
    }

}


private fun Lecture.infoMessage(): String = "Lecture ${name}\n" +
        "Title: ${currentTitle()}\n" +
        "Description: ${description}\n" +
        "Lecture type: ${lectureType}\n" +
        "Lecture numeration: ${if (doubleNumeration) "double" else "single"}\n" +
        (playlistId?.let { "Playlist: https://www.youtube.com/playlist?list=$it" } ?: "")
