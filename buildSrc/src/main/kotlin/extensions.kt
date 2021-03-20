import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.withType
import java.io.ByteArrayOutputStream

fun Project.configureShadowJar() {
    apply<ShadowPlugin>()
    tasks {
        withType<ShadowJar> {
            archiveClassifier.set("")
            archiveFileName.set("ViaBackwards-${project.name.substringAfter("viabackwards-").capitalize()}-${project.version}.jar")
            destinationDirectory.set(rootProject.projectDir.resolve("build/libs"))
            configureRelocations()
        }
        getByName("build") {
            dependsOn(withType<ShadowJar>())
        }
        withType<Jar> {
            if (name == "jar") {
                archiveClassifier.set("unshaded")
            }
            from(project.parent!!.file("LICENSE.txt")) {
                into("")
            }
        }
    }
}

private fun ShadowJar.configureRelocations() {
    relocate("com.google.gson", "us.myles.viaversion.libs.gson")
    relocate("net.md_5.bungee", "us.myles.viaversion.libs.bungeecordchat") {
        include("net.md_5.bungee.api.chat.*")
        include("net.md_5.bungee.api.ChatColor")
        include("net.md_5.bungee.api.ChatMessageType")
        include("net.md_5.bungee.chat.*")
    }
    relocate("it.unimi.dsi.fastutil", "us.myles.viaversion.libs.fastutil")
}

fun Project.latestCommitHash(): String {
    val byteOut = ByteArrayOutputStream()
    exec {
        commandLine = listOf("git", "rev-parse", "--short", "HEAD")
        standardOutput = byteOut
    }
    return byteOut.toString(Charsets.UTF_8.name()).trim()
}
