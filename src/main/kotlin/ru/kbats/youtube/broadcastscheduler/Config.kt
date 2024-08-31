package ru.kbats.youtube.broadcastscheduler

import ru.kbats.youtube.broadcastscheduler.platforms.vk.VKApi

data class Config(
    val botApiToken: String,
    val mongoDBConnectionString: String,
    val mongoDBBase: String,
    val publicFilesUrl: String = "https://kbats.ru/ctlecbot",
    val restreamerApiUrl: String,
    val vkConfig: VKApi.VKApiConfig,
)

fun config(): Config {
    return Config(
        System.getenv("BOT_TOKEN"),
        System.getenv("MONGO"),
        System.getenv("MONGO_BASE"),
        restreamerApiUrl = System.getenv("RESTREAMER_API"),
        vkConfig = VKApi.VKApiConfig(
            System.getenv("VK_TOKEN"),
            System.getenv("VK_USER").toLong(),
            System.getenv("VK_GROUP").toLong(),
        )
    )
}

