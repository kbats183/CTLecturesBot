package ru.kbats.youtube.broadcastscheduler.states

import ru.kbats.youtube.broadcastscheduler.data.Lesson
import ru.kbats.youtube.broadcastscheduler.data.ThumbnailsImage
import ru.kbats.youtube.broadcastscheduler.data.ThumbnailsTemplate

sealed class UserState {
    object Default : UserState()
    object CreatingNewLiveStream : UserState()
    class ApplyingTemplateToVideo(val lectureId: String) : UserState()

    class CreatingThumbnailsImage(
        val step: String,
        val image: ThumbnailsImage,
        val prevMessagesIds: List<Long>,
        val prevState: UserState
    ) : UserState()

    class CreatingThumbnailsTemplate(
        val step: String,
        val template: ThumbnailsTemplate,
        val prevMessageId: Long?,
        val prevState: UserState,
    ) : UserState()

    class EditingThumbnailsTemplate(
        val id: String,
        val op: String,
        val prevMessagesIds: List<Long>,
        val prevState: UserState,
    ) : UserState()

    class CreatingLesson(val step: String, val lesson: Lesson, val prevMessagesIds: List<Long>) : UserState()
    class ChoosingLessonThumbnailsTemplate(
        val lessonId: String,
        val prevMessagesIds: List<Long>,
        val prevState: UserState
    ) : UserState()
class ChoosingLessonStreamKey(
        val lessonId: String,
        val prevMessagesIds: List<Long>,
        val prevState: UserState
    ) : UserState()

    class EditingLesson(
        val id: String,
        val op: String,
        val prevMessagesIds: List<Long>,
        val prevState: UserState,
    ) : UserState()

    class EditingVideo(
        val id: String,
        val op: String,
        val prevMessagesIds: List<Long>,
        val prevState: UserState,
        val buffer: List<String> = emptyList(),
    ) : UserState()
}
