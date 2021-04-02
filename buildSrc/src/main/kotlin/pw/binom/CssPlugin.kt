package pw.binom

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

open class CssPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("kotlin-platform-jvm")
        target.gradle.addListener(object : DependencyResolutionListener {
            override fun beforeResolve(dependencies: ResolvableDependencies) {
                target.configurations.getAt("runtime").dependencies.add(target.dependencies.create("org.jetbrains.kotlin:kotlin-stdlib:1.4.30"))
                target.gradle.removeListener(this)
            }

            override fun afterResolve(dependencies: ResolvableDependencies) {
            }

        })
        val generateMainTask = target.tasks.register("generateCssMainSource", GenerateMain::class.java)
        val compileKotlin = target.tasks.findByName("compileKotlin") as KotlinCompile
        compileKotlin.dependsOn(generateMainTask.get())
        val mainFileDir = target.buildDir.resolve("css-gen-main")
        generateMainTask.get().mainFile.set(mainFileDir.resolve("GeneratedMain.kt"))
        val kotlin = target.extensions.getByName("kotlin") as KotlinJvmProjectExtension
        kotlin.sourceSets.findByName("main")!!.kotlin.srcDir(mainFileDir)
        val generateCss = target.tasks.register("buildCss", GenerateCss::class.java)
        generateCss.get().dependsOn(compileKotlin)
        generateCss.get().outputCss.set(target.buildDir.resolve("css/${target.name}.css"))
        generateCss.get().classpath = compileKotlin.outputs.files + target.configurations.getAt("runtime")
    }
}

open class GenerateCss : JavaExec() {

    @OutputFile
    var outputCss = project.objects.fileProperty()

    init {
        group = "build"
        mainClass.set("pw.binom.css.GeneratedMain")
        argumentProviders += CommandLineArgumentProvider { arrayListOf(outputCss.asFile.get().absolutePath) }
    }
}

open class GenerateMain : DefaultTask() {
    @OutputFile
    internal var mainFile = project.objects.fileProperty()

    init {
        group = "build"
    }

    @TaskAction
    fun execute() {
        mainFile.asFile.get().outputStream().bufferedWriter().use {
            it.append("package pw.binom.css\n\n")
                .append("import java.io.File\n\n")
                .append("object GeneratedMain{\n")
                .append("\t@JvmStatic\n")
                .append("\tfun main(args:Array<String>){\n")
                .append("\t\tFile(args[0]).outputStream().bufferedWriter().use {\n")
                .append("\t\t\tstyle.buildRecursive(it)\n")
                .append("\t\t}\n")
                .append("\t}\n")
                .append("}")
        }
    }
}
