plugins {
    id("maven-publish")
    id("org.jetbrains.kotlin.multiplatform")
    id("kotlinx-serialization")
    id("com.github.johnrengelman.shadow").version("5.2.0")
}

val nativeEntryPoint = "pw.binom.repo.main"
val serialization_version = "1.0.1"
val network_version = "0.1.28"
val kotlin_version = "1.4.30"

kotlin {
    linuxX64 {
        binaries {
            executable {
                entryPoint = nativeEntryPoint
            }
        }
    }
    linuxArm32Hfp { // Use your target instead.
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
    mingwX86 { // Use your target instead.
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
                api("org.jetbrains.kotlin:kotlin-stdlib-common:$kotlin_version")
                api("pw.binom.io:core:$network_version")
                api("pw.binom.io:env:$network_version")
                api("pw.binom.io:flux:$network_version")
                api("pw.binom.io:strong:$network_version")
                api("pw.binom.io:sqlite:$network_version")
                api("pw.binom.io:file:$network_version")
                api("pw.binom.io:httpServer:$network_version")
                api("pw.binom.io:concurrency:$network_version")
                api("pw.binom.io:xml:$network_version")
                api("pw.binom.io:logger:$network_version")
                api("pw.binom.io:process:$network_version")
                api("pw.binom.io:webdav:$network_version")
                api("org.jetbrains.kotlinx:kotlinx-serialization-core:$serialization_version")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version")
                api("org.jetbrains.kotlinx:kotlinx-serialization-properties:$serialization_version")
            }
        }

        val nativeMain by creating {
            dependencies {
                dependsOn(commonMain)
            }
        }
        val linuxX64Main by getting {
            dependencies {
                dependsOn(nativeMain)
            }
        }
        val mingwX64Main by getting {
            dependencies {
                dependsOn(linuxX64Main)
            }
        }

        val mingwX86Main by getting {
            dependencies {
                dependsOn(mingwX64Main)
            }
        }

        val linuxArm32HfpMain by getting {
            dependencies {
                dependsOn(linuxX64Main)
            }
        }

        val jvmMain by getting {
            dependencies {
                api("org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version")
            }
        }
    }
}

tasks {
    val jvmJar by getting(Jar::class)

    val shadowJar by creating(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
        from(jvmJar.archiveFile)
        configurations = listOf(project.configurations["jvmRuntimeClasspath"])
        manifest {
            attributes("Main-Class" to "pw.binom.repo.JvmMain")
        }
    }
}