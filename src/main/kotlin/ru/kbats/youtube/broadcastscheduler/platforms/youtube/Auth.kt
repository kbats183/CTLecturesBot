package ru.kbats.youtube.broadcastscheduler.platforms.youtube

import com.google.api.client.auth.oauth2.Credential
import ru.kbats.youtube.broadcastscheduler.youtube.Auth

private var DEFAULT_SCOPES: List<String> = listOf("https://www.googleapis.com/auth/youtube")

fun getCredentials(user: String): Credential? {
    return Auth.authorize(DEFAULT_SCOPES, "createbroadcast_" + user)
}
