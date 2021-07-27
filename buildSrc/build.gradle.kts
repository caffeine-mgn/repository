buildscript {

    repositories {
        mavenLocal()
        mavenCentral()
        jcenter()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.21")
    }
}

plugins{
        kotlin("jvm") version "1.5.21"
}

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-stdlib:1.5.21")
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.21")
    api("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.5.21")
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.21")
}