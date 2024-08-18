import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.0.10"
    kotlin("plugin.serialization") version "2.0.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
}

group = "ru.kbats.youtube.broadcastscheduler"
version = "0.1"

application {
    mainClass.set("ru.kbats.youtube.broadcastscheduler.MainKt")
}

tasks {
//    val fatJar = register<Jar>("fatJar") {
//        dependsOn.addAll(
//            listOf("compileJava", "compileKotlin", "processResources")
//        ) // We need this for Gradle optimization to work
//        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
//        manifest { attributes(mapOf("Main-Class" to application.mainClass)) } // Provided we set it up in the application plugin configuration
//        val sourcesMain = sourceSets.main.get()
//        val contents = configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } +
//                sourcesMain.output
//        from(contents)
//    }
//    jar {
//        archiveFileName.set("reactions-bot-${project.version}-part.jar")
//    }
    shadowJar {
        archiveFileName.set("YoutubeBroadcastScheduler-${project.version}.jar")
        dependsOn(distTar, distZip)
    }
    task<Copy>("release") {
        from(shadowJar)
        destinationDir = rootProject.rootDir.resolve("artifacts")
    }
}

repositories {
    mavenCentral()
    maven { setUrl("https://jitpack.io") }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.2.0")

    implementation("ch.qos.logback:logback-classic:1.5.7")
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:5.1.2")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    implementation("com.google.api-client:google-api-client:1.25.0")
    implementation("com.google.apis:google-api-services-youtube:v3-rev222-1.25.0")
    implementation("com.google.http-client:google-http-client-jackson2:1.44.2")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.36.0")

//    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.0.7")
}
