package ru.kbats.youtube.broadcastscheduler

data class Config(
    val botApiToken: String,
    val mongoDBConnectionString: String,
    val mongoDBBase: String,
    val publicFilesUrl: String = "https://kbats.ru/ctlecbot"
)

fun config(): Config {
    return Config(
        System.getenv("BOT_TOKEN"),
        System.getenv("MONGO"),
        System.getenv("MONGO_BASE"),
    )
}
