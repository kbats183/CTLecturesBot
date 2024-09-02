package ru.kbats.youtube.broadcastscheduler.platforms.youtube

import java.io.IOException

class FailedApiRequestException(override val cause: IOException) : RuntimeException("Failed to execute youtube api request")
