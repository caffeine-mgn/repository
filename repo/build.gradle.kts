plugins {
//    kotlin("multiplatform")
//    id("com.github.johnrengelman.shadow")
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

val nativeEntryPoint = "pw.binom.repo.main"

kotlin {
    linuxX64 {
        binaries {
            executable {
                entryPoint = nativeEntryPoint
            }
        }
    }
    mingwX64 { // Use your target instead.
        binaries {
            executable {
                entryPoint = nativeEntryPoint
            }
        }
    }
    jvm()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                api(dependencies.platform("pw.binom.io:bom:${libs.versions.binom.get()}"))
                api("pw.binom.io:core")
                api("pw.binom.io:env")
                api("pw.binom.io:flux")
                api("pw.binom.io:db")
                api("pw.binom.io:db-serialization")
                api("pw.binom.io:sqlite")
                api("pw.binom.io:strong")
                api("pw.binom.io:strong-properties-yaml")
                api("pw.binom.io:strong-properties-ini")
                api("pw.binom.io:strong-web-server")
                api("pw.binom.io:signal")
                api("pw.binom.io:kmigrator")
                api("pw.binom.io:file")
                api("pw.binom.io:httpServer")
                api("pw.binom.io:concurrency")
                api("pw.binom.io:xml-serialization")
                api("pw.binom.io:logger")
                api("pw.binom.io:process")
                api("pw.binom.io:webdav")
                api(libs.kotlinx.serialization.json)
//                api(libs.kotlinx.serialization.protobuf)
                api(libs.kotlinx.coroutines)
//                api("org.jetbrains.kotlinx:kotlinx-serialization-core:${pw.binom.Versions.SERIALIZATION_VERSION}")
//                api("org.jetbrains.kotlinx:kotlinx-serialization-json:${pw.binom.Versions.SERIALIZATION_VERSION}")
//                api("org.jetbrains.kotlinx:kotlinx-serialization-properties:${pw.binom.Versions.SERIALIZATION_VERSION}")
            }
        }
        commonTest.dependencies {
            api(dependencies.platform("pw.binom.io:bom:${libs.versions.binom.get()}"))
            api("pw.binom.io:testing")
        }

//        val nativeMain by creating {
//            dependencies {
//                dependsOn(commonMain)
//            }
//        }
//        val linuxX64Main by getting {
//            dependencies {
//                dependsOn(nativeMain)
//            }
//        }
//        val mingwX64Main by getting {
//            dependencies {
//                dependsOn(linuxX64Main)
//            }
//        }
//
//        val mingwX86Main by getting {
//            dependencies {
//                dependsOn(mingwX64Main)
//            }
//        }
//
//        val linuxArm32HfpMain by getting {
//            dependencies {
//                dependsOn(linuxX64Main)
//            }
//        }
//
//        val jvmMain by getting {
//            dependencies {
//                api("org.jetbrains.kotlin:kotlin-stdlib:${pw.binom.Versions.KOTLIN_VERSION}")
//            }
//        }
    }
}

tasks {
    val jvmJar by getting(Jar::class)

    val shadowJar by creating(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
        from(jvmJar.archiveFile)
        configurations = listOf(project.configurations["jvmRuntimeClasspath"])
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/*.txt")
        manifest {
            attributes("Main-Class" to "pw.binom.repo.JvmMain")
        }
    }
}