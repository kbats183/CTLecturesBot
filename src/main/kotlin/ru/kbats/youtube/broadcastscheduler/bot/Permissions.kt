package ru.kbats.youtube.broadcastscheduler.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.*
import com.github.kotlintelegrambot.dispatcher.handlers.*
import com.github.kotlintelegrambot.entities.ChosenInlineResult
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.github.kotlintelegrambot.extensions.filters.Filter
import ru.kbats.youtube.broadcastscheduler.Application
import ru.kbats.youtube.broadcastscheduler.bot.Dispatch.logger
import java.lang.Integer.min

class AdminDispatcher(val application: Application, private val dispatcher: Dispatcher) {
    fun text(text: String? = null, handleText: HandleText) {
        dispatcher.text(text) {
            if (isAdmin(message)) {
                handleText()
            }
        }
    }

    fun photos(handlePhotos: HandlePhotos) {
        dispatcher.photos {
            if (isAdmin(message)) {
                handlePhotos()
            }
        }
    }

    fun document(handleDocument: HandleDocument) {
        dispatcher.document {
            if (isAdmin(message)) {
                handleDocument()
            }
        }
    }

    fun callbackQuery(data: String? = null, handleCallbackQuery: HandleCallbackQuery) {
        dispatcher.callbackQuery(data) {
            if (isAdmin(callbackQuery.from)) {
                handleCallbackQuery()
            }
        }
    }

    fun command(command: String, handleCommand: HandleCommand) {
        dispatcher.command(command) {
            if (isAdmin(message)) {
                logger.info("User ${message.chat.username} send command ${message.text.logMessage()}")
                handleCommand()
            }
        }
    }

    fun inlineQuery(handleInlineQuery: HandleInlineQuery) {
        dispatcher.inlineQuery {
            if (isAdmin(inlineQuery.from)) {
                handleInlineQuery()
            }
        }
    }

//    fun chosenInlineResult(handleChosenInlineResultHandlerEnvironment: HandleChosenInlineResultQuery) {
//        dispatcher.addHandler(ChosenInlineResultHandler(Filter.All, handleChosenInlineResultHandlerEnvironment))
//    }

    private suspend fun isAdmin(message: Message?) = message?.chat?.type == "private" && isAdmin(message.from)

    private suspend fun isAdmin(user: User?) =
        user != null && application.repository.getAdmins().any { it.login == user.username }
}

class ChosenInlineResultHandler(
    private val filter: Filter,
    private val handleMessage: HandleChosenInlineResultQuery,
) : Handler {

    override fun checkUpdate(update: Update): Boolean =
        if (update.chosenInlineResult == null) {
            false
        } else {
            true
        }

    override suspend fun handleUpdate(bot: Bot, update: Update) {
        checkNotNull(update.chosenInlineResult)
        val messageHandlerEnv = ChosenInlineResultHandlerEnvironment(bot, update, update.chosenInlineResult!!)
        handleMessage(messageHandlerEnv)
    }
}

data class ChosenInlineResultHandlerEnvironment(
    val bot: Bot,
    val update: Update,
    val chosenInlineResult: ChosenInlineResult
)
typealias HandleChosenInlineResultQuery = suspend ChosenInlineResultHandlerEnvironment.() -> Unit

fun Dispatcher.withAdminRight(application: Application, body: AdminDispatcher.() -> Unit) {
    text {
        logger.info("User ${message.chat.username} send text ${message.text.logMessage()}")
    }
    photos {
        logger.info("User ${message.chat.username} send photos ${message.text.logMessage()}")
    }
    document {
        logger.info("User ${message.chat.username} send document ${message.text.logMessage()}")
    }
    callbackQuery {
        logger.info(
            "User ${callbackQuery.from.username} send callback ${callbackQuery.data} from message ${callbackQuery.message?.text.logMessage()}"
        )
    }
    inlineQuery {
        logger.info("User ${inlineQuery.from.username} send inline query ${inlineQuery.query.logMessage()}")
    }

    AdminDispatcher(application, this).body()
}

private fun String?.logMessage() =
    this?.let { "`" + it.substring(0, min(50, length)).replace("\n", " ") + "`" } ?: ""
