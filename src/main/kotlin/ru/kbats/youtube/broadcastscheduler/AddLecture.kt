package ru.kbats.youtube.broadcastscheduler

import kotlinx.coroutines.runBlocking
import ru.kbats.youtube.broadcastscheduler.data.*

fun main() {
    val config = config()
    val repository = getRepository(config)

    runBlocking {
        println("Executing ...")
        repository.lecture.insertOne(
            Lecture(
                name = "1.CompArch35ev",
                title = "[s1 | 2022] Архитектура ЭВМ, Женя Виноградов",
                description = "Записи курса архитектуры ЭВМ, который читается для студентов первого года обучения программы «Прикладная математика и информатика» факультета ИТиП университета ИТМО в первом семестре.\n" +
                        "Лектор: Женя Виноградов\n" +
                        "Все видео: https://youtube.com/playlist?list=PLd7QXkfmSY7YMtITm18rfn4piFBer6EnS",
                currentLectureNumber = 5,
                doubleNumeration = false,
                lectureType = LectureType.Lecture,
                //tart, honey, yellow, green, capri, bluetiful, violet, pink
                thumbnails = LectureThumbnails("comparch_m3132_5_ev.png", "honey"),
                scheduling = LectureBroadcastScheduling(
                    startDay = 5,
                    startHour = 11,
                    startMinute = 40,
                    streamKeyId = "c8_XiJXPMz699NvDmtGoTA1663338478701552",
                ),
                privacy = LectureBroadcastPrivacy.Public,
            )
        )

//        repository.db.getCollection<Lecture>().insertOne(
//            Lecture(
//                name = "DifEq37pr",
//                title = "[s3 | 2022] Дифференциальные Уравнения, Анастасия Мурзина",
//                description = "Записи практик курса дифференциальных уравнений, который читается для студентов второго года обучения программы «Прикладная математика и информатика» факультета ИТиП университета ИТМО в третьем семестре.\n" +
//                        "Практик: Мурзина Анастасия Алексеевна\n" +
//                        "Все видео: https://www.youtube.com/playlist?list=PLd7QXkfmSY7a7YpTa_hMfOPOGrCtNVr-c",
//                currentLectureNumber = 3,
//                doubleNumeration = false,
//                lectureType = LectureType.Practice,
//                //tart, honey, yellow, green, capri, bluetiful, violet, pink
//                thumbnails = LectureThumbnails("diffeq_murzina_pr.png", "tart"),
//            )
//        )

//        val lecture = repository.db.getCollection<Lecture>().findOne(Lecture::name eq "MStat39")!!
//        repository.db.getCollection<Lecture>().updateOne(
//            Lecture::name eq "MStat39",
//            lecture.copy(
//                description = "Записи курса математической статистики, который читается для студентов третьего года обучения программы «Прикладная математика и информатика» факультета ИТиП университета ИТМО в пятом семестре\n" +
//                        "Лектор: Иван Лимар\n" +
//                        "Все видео: https://www.youtube.com/playlist?list=PLd7QXkfmSY7a2qy9JsJIe-sFhiS5M1cs9"
//            )
//        )
        println("Ready")
    }
}
