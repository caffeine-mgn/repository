plugins {
//    kotlin("multiplatform") version "2.1.0" apply false
//    kotlin("plugin.serialization") version "2.1.0" apply false
//    id("com.github.johnrengelman.shadow") version "5.2.0" apply false
}

//buildscript {
//    ext.kotlin_version = "1.4.30"
//    ext.network_version = "0.1.28"
//    ext.serialization_version='1.0.1'

//    repositories {
//        mavenCentral()
//        mavenLocal()
//        maven(url="https://plugins.gradle.org/m2/")
//    }
//    dependencies {
//        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${pw.binom.Versions.KOTLIN_VERSION}")
//        classpath("org.jetbrains.kotlin:kotlin-serialization:${pw.binom.Versions.KOTLIN_VERSION}")
//        classpath("pw.binom.static-css:plugin:${pw.binom.Versions.STATIC_CSS_VERSION}")
//    }
//}


allprojects {
    group = "pw.binom.repo"

    repositories {
        maven(url = "https://repo.binom.pw")
//        mavenLocal()
        mavenCentral()
        jcenter()
    }
}