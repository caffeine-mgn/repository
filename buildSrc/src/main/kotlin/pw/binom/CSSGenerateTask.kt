package pw.binom

import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Jar
import java.io.File

open class GenerateCSSTask constructor() : JavaExec() {
    @org.gradle.api.tasks.OutputFile
    open var outputFile: File = project.buildDir.resolve("css/output.css")
    open fun useJar(jar: Jar) {
        dependsOn(jar)
        classpath = project.fileTree(jar.archiveFile.get().asFile)
        doFirst {
            args = listOf(outputFile.absolutePath)
        }
    }

    init {
        mainClass.set("pw.styles.Main")
    }
}