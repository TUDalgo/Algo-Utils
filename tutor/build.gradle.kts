import org.jetbrains.dokka.DokkaConfiguration.Visibility
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import java.net.URL

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
}

dependencies {
    api(project(":algoutils-student"))
    api(libs.spoon)
    api(libs.jagr)
    api(libs.mockito)
    api(libs.asm)
    api(libs.asmTree)
    testImplementation(libs.junit)
    dokkaPlugin(libs.dokkaKotlinAsJavaPlugin)
}

tasks {
    test {
        useJUnitPlatform()
    }
    dokkaHtmlPartial {
        // moduleName.set("Student")
        pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
            footerMessage = "(c) 2023 tudalgo.org"
        }
        dokkaSourceSets.configureEach {
            includes.from("Module.md")
            jdkVersion.set(17)
            sourceLink {
                localDirectory.set(projectDir.resolve("src"))
                remoteUrl.set(URL("https://github.com/tudalgo/algoutils/tree/master/tutor/src"))
                remoteLineSuffix.set("#L")
            }
            perPackageOption {
                reportUndocumented.set(true)
                documentedVisibilities.set(setOf(Visibility.PUBLIC, Visibility.PROTECTED))
            }
        }
    }
}
