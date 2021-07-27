
plugins {
    id("static-css")
}
tasks {
    val buildCss by getting(pw.binom.css.GenerateCss::class) {
        outputCss.set(file("${buildDir}/output.css"))
    }
}

//dependencies {
//    api(project(":services:admin-styles:style-names"))
//}

/*
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.github.johnrengelman.shadow").version("5.2.0")
}
val nativeEntryPoint = "pw.binom.repo.web.main"

kotlin {
    js()
    jvm()
    sourceSets {
        val commonMain by getting {
            dependencies {
//                api(project(":web"))
                api("org.jetbrains.kotlin:kotlin-stdlib-common:${pw.binom.Versions.KOTLIN_VERSION}")
            }
        }

        val jvmMain by getting {
            dependencies {
                api("org.jetbrains.kotlin:kotlin-stdlib:${pw.binom.Versions.KOTLIN_VERSION}")
            }
        }
    }
}

tasks {
    val jvmJar by getting(Jar::class)

    val shadowJar by creating(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
        from(jvmJar.archiveFile)
        configurations = listOf(project.configurations["jvmRuntimeClasspath"])
    }

    val generateCSS by creating(pw.binom.GenerateCSSTask::class.java) {
        useJar(shadowJar)
    }
}
*/