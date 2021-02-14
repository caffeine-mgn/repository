plugins {
    id("maven-publish")
    id("org.jetbrains.kotlin.multiplatform")
    id("kotlinx-serialization")
}

val nativeEntryPoint = "pw.binom.repo.main"

kotlin {
    js {
        browser {
            testTask {
                useKarma {
                    useFirefox()
                    useChrome()
                    useChromeHeadless()
                }
            }
        }
        binaries.executable()
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api("org.tlsys:css:1.0.4")
                api("org.jetbrains.kotlin:kotlin-stdlib-common:${pw.binom.Versions.KOTLIN_VERSION}")
                api("org.jetbrains.kotlinx:kotlinx-serialization-core:${pw.binom.Versions.SERIALIZATION_VERSION}")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:${pw.binom.Versions.SERIALIZATION_VERSION}")
            }
        }

        val jsMain by getting {
            dependsOn(commonMain)
            dependencies {
                api("org.jetbrains.kotlin:kotlin-stdlib-js:${pw.binom.Versions.KOTLIN_VERSION}")
            }
        }

        val jsTest by getting {
            dependencies {
                api(kotlin("test-js"))
            }
        }
    }
}

tasks {
    val copyCss by creating(Copy::class) {
        dependsOn("compileKotlinJs")
        dependsOn(":web-styles:generateCSS")
        with(copySpec {
            from(project(":web-styles").buildDir.resolve("css"))
            include("*.css")
        })
        this.destinationDir = project.buildDir.resolve("processedResources/js/main")
    }
}