import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.gradle.DokkaTask
import org.tudalgo.algoutils.script.AlgoUtilsPublishPlugin
import java.net.URL

plugins {
    kotlin("jvm") version "2.1.10"
    `java-library`
    alias(libs.plugins.dokka)
}

buildscript {
    dependencies {
        classpath(libs.dokkaBase)
    }
}

val projectVersion = file("version").readLines().first()

allprojects {
    apply(plugin = "java-library")
    group = "org.tudalgo"
    version = projectVersion
    tasks {
        withType<JavaCompile> {
            options.encoding = "UTF-8"
            sourceCompatibility = "17"
            targetCompatibility = "17"
        }
    }
    tasks.withType<DokkaTask>().configureEach {
        dokkaSourceSets.configureEach {
            jdkVersion.set(17)
            includes.from(fileTree(projectDir) { include("*.md") })
            sourceLink {
                localDirectory.set(projectDir.resolve("src"))
                remoteUrl.set(URL("https://github.com/tudalgo/algoutils/tree/master/src"))
                remoteLineSuffix.set("#L")
            }
        }
    }
}

subprojects {
    apply<AlgoUtilsPublishPlugin>()
    apply(plugin = "org.jetbrains.dokka")
}

tasks.dokkaHtmlMultiModule {
    moduleName.set("Algoutils")
    includes.from("Module.md")
    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
        customAssets = listOf(file("logo-icon.svg"))
        footerMessage = "(c) 2023 tudalgo.org"
    }
}

dependencies {
    dokkaPlugin(libs.dokkaKotlinAsJavaPlugin)
}
