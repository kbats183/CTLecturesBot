package ru.kbats.youtube.broadcastscheduler.states

sealed class UserState {
    object Default : UserState()
    object CreatingNewLiveStream : UserState()
    class ApplyingTemplateToVideo(val lectureId: String) : UserState()

    object CreatingThumbnailsImage : UserState()
    class CreatingThumbnailsImage2(val name: String) : UserState()

    class CreatingThumbnailsTemplate(val prevMessageId: Long?) : UserState()
    class CreatingThumbnailsTemplate2(val name: String, val prevMessageId: Long?) : UserState()
    class CreatingThumbnailsTemplate3(
        val name: String,
        val firstLine: String,
        val secondLine: String,
        val prevMessageId: Long?
    ) : UserState()
    class CreatingThumbnailsTemplate4(
        val name: String,
        val firstLine: String,
        val secondLine: String,
        val bottomLine: String,
        val prevMessageId: Long?
    ) : UserState()
    class CreatingThumbnailsTemplate5(
        val name: String,
        val firstLine: String,
        val secondLine: String,
        val bottomLine: String,
        val termNumber: String,
        val prevMessageId: Long?
    ) : UserState()

    class EditingThumbnailsTemplate(
        val id: String,
        val op: String,
        val prevMessagesIds: List<Long>
    ) : UserState()
}
