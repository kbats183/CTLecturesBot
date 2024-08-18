package ru.kbats.youtube.broadcastscheduler

import ru.kbats.youtube.broadcastscheduler.youtube.YoutubeApi
import ru.kbats.youtube.broadcastscheduler.youtube.getCredentials

fun main() {
    val youtubeApi = YoutubeApi(getCredentials(System.getenv("YT_ENV") ?: "ct_lectures")!!)
    println(youtubeApi.getPlaylistItems(playlistId = "PLd7QXkfmSY7YCE9YHJbWOIHGmaNnpt3I3"))
//    youtubeApi.getAllVideosBefore("UCc8_XiJXPMz699NvDmtGoTA", 1000, "2022-08-20T00:00:00Z")
}
