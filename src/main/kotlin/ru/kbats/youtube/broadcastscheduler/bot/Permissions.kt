package ru.kbats.youtube.broadcastscheduler.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.*
import com.github.kotlintelegrambot.dispatcher.handlers.*
import com.github.kotlintelegrambot.entities.ChosenInlineResult
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.extensions.filters.Filter
import ru.kbats.youtube.broadcastscheduler.Application
import java.lang.Integer.min

class AdminDispatcher(val application: Application, private val dispatcher: Dispatcher) {
    fun text(text: String? = null, handleText: HandleText) {
        dispatcher.text(text) {
            if (application.repository.getAdmins().any { it.login == message.from?.username }) {
                println("User ${message.chat.username} send text `${message.text}`")
                handleText()
            }
        }
    }

    fun photos(handlePhotos: HandlePhotos) {
        dispatcher.photos {
            if (application.repository.getAdmins().any { it.login == message.from?.username }) {
                handlePhotos()
            }
        }
    }

    fun document(handleDocument: HandleDocument) {
        dispatcher.document {
            if (application.repository.getAdmins().any { it.login == message.from?.username }) {
                handleDocument()
            }
        }
    }

    fun callbackQuery(data: String? = null, handleCallbackQuery: HandleCallbackQuery) {
        dispatcher.callbackQuery(data) {
            if (application.repository.getAdmins().any { it.login == callbackQuery.from.username }) {
                println(
                    "User ${callbackQuery.from.username} send callback ${callbackQuery.data} from message " +
                            "`${callbackQuery.message?.text?.let { it.substring(0, min(50, it.length)) }}`"
                )
                handleCallbackQuery()
            }
        }
    }

    fun command(command: String, handleCommand: HandleCommand) {
        dispatcher.command(command) {
            if (application.repository.getAdmins().any { it.login == message.from?.username }) {
                handleCommand()
            }
        }
    }

    fun inlineQuery(handleInlineQuery: HandleInlineQuery) {
        dispatcher.inlineQuery {
            if (application.repository.getAdmins().any { it.login == inlineQuery.from.username }) {
                println("User ${inlineQuery.from.username} send inline query ${inlineQuery.query}")
                handleInlineQuery()
            }
        }
    }

    fun chosenInlineResult(handleChosenInlineResultHandlerEnvironment: HandleChosenInlineResultQuery) {
        dispatcher.addHandler(ChosenInlineResultHandler(Filter.All, handleChosenInlineResultHandlerEnvironment))
    }

    companion object {
//        val logger = getLogger(AdminDispatcher::class.java)!!
    }
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
    AdminDispatcher(application, this).body()
}
