package ru.kbats.youtube.broadcastscheduler

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.logging.LogLevel
import ru.kbats.youtube.broadcastscheduler.bot.setupDispatcher
import ru.kbats.youtube.broadcastscheduler.platforms.restreamer.Restreamer
import ru.kbats.youtube.broadcastscheduler.platforms.vk.VKApi
import ru.kbats.youtube.broadcastscheduler.states.UserStateStorage
import ru.kbats.youtube.broadcastscheduler.platforms.youtube.YoutubeApi
import ru.kbats.youtube.broadcastscheduler.platforms.youtube.getCredentials

class Application(private val config: Config) {
    internal val youtubeApi = YoutubeApi(getCredentials(System.getenv("YT_ENV") ?: "ct_lectures")!!)
    internal val vkApi = VKApi(config.vkConfig)
    internal val repository = getRepository(config)
    internal val filesRepository = FilesRepository(config)
    internal val userStates = UserStateStorage()
    internal val restreamer = Restreamer(config.restreamerApiUrl)

    fun run() {
        val bot = bot {
            logLevel = LogLevel.All(networkLogLevel = LogLevel.Network.None)
            token = config.botApiToken
            dispatch {
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
    application.run()
}
