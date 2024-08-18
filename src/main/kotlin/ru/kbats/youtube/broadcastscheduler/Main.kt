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

    companion object {
        const val thumbnailsDirectory = "thumbnails"
    }
}


fun main() {
//    println("${Clock.System.now().epochSeconds}")
//    println("${Clock.System.now().toLocalDateTime(timeZone)}")
    val application = Application(config())
    println("Hello!")
    application.run()
}
