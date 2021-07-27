plugins {
    id("kotlin-multiplatform")
    id("kotlinx-serialization")
    id("com.github.johnrengelman.shadow").version("5.2.0")
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
                implementation(kotlin("stdlib"))
                api("pw.binom.io:core:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:env:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:flux:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:strong-application:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:db:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:db-serialization:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:sqlite:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:kmigrator:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:file:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:httpServer:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:concurrency:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:xml:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:logger:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:process:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:webdav:${pw.binom.Versions.BINOM_VERSION}")
                api("org.jetbrains.kotlinx:kotlinx-serialization-core:${pw.binom.Versions.SERIALIZATION_VERSION}")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:${pw.binom.Versions.SERIALIZATION_VERSION}")
                api("org.jetbrains.kotlinx:kotlinx-serialization-properties:${pw.binom.Versions.SERIALIZATION_VERSION}")
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
        manifest {
            attributes("Main-Class" to "pw.binom.repo.JvmMain")
        }
    }
}