import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.changelog.closure
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    // Java support
    id("java")
    // Kotlin support
    id("org.jetbrains.kotlin.jvm") version "1.7.22"
    // gradle-intellij-plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
    id("org.jetbrains.intellij") version "0.7.2"
    // gradle-changelog-plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
    id("org.jetbrains.changelog") version "1.1.2"
    // read more  https://github.com/ben-manes/gradle-versions-plugin gradle-upgrade-interactive
    id("com.github.ben-manes.versions") version "0.38.0"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

// Configure project's dependencies
repositories {
    maven { setUrl("https://maven.aliyun.com/repository/public/") }
    maven { setUrl("https://maven.aliyun.com/repository/gradle-plugin") }
    maven { setUrl("https://maven.aliyun.com/repository/google") }
    maven { setUrl("https://www.jitpack.io") }
}

dependencies {
    compileOnly("cn.hutool:hutool-all:5.6.0")
}

// Configure gradle-intellij-plugin plugin.
// Read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
    pluginName = properties("pluginName")
    version = properties("platformVersion")
    type = properties("platformType")
    downloadSources = properties("platformDownloadSources").toBoolean()
    updateSinceUntilBuild = true
    // 沙箱目录位置，用于保存IDEA的设置，默认在build文件下面，防止clean，放在根目录下。
    sandboxDirectory = "$projectDir/idea-sandbox"
    // idea 2020.3.2 依赖库
    intellijRepo = "https://code.aliyun.com/intellij-repository/idea/raw/main"
    //  2020.3.2 版本 jbr runtime 依赖库
    jreRepo = "https://code.aliyun.com/intellij-repository/idea/raw/main/releases/com/jetbrains/intellij/jbre"
    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
    setPlugins(*properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty).toTypedArray())
}

// Configure gradle-changelog-plugin plugin.
// Read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    version = properties("pluginVersion")
    groups = emptyList()
}

tasks {
    // Set the compatibility versions to 11
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
        options.encoding = "UTF-8"
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
        kotlinOptions.useIR = true
    }

    patchPluginXml {
        version(properties("pluginVersion"))
        sinceBuild(properties("pluginSinceBuild"))
        untilBuild(properties("pluginUntilBuild"))

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription(
            closure {
                File("./README.md").readText().lines().run {
                    val start = "<!-- Plugin description -->"
                    val end = "<!-- Plugin description end -->"

                    if (!containsAll(listOf(start, end))) {
                        throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                    }
                    subList(indexOf(start) + 1, indexOf(end))
                }.joinToString("\n").run { markdownToHTML(this) }
            }
        )

        // Get the latest available change notes from the changelog file
        changeNotes(
            closure {
                changelog.getLatest().toHTML()
            }
        )
    }

    runPluginVerifier {
        ideVersions(properties("pluginVerifierIdeVersions"))
    }

    buildSearchableOptions {
        enabled = false
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token(System.getenv("PUBLISH_TOKEN"))
        // pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels(properties("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').first())
    }

    runIde {
        // Absolute path to installed target 3.5 Android Studio to use as IDE Development Instance
        // The "Contents" directory is macOS specific.
        // ideDirectory ("C:/dev/apps/AndroidStudio/ch-1/202.7049475")
    }

    named<DependencyUpdatesTask>("dependencyUpdates").configure {
        // optional parameters
        checkForGradleUpdate = true
        outputFormatter = "txt"
        outputDir = "build/dependencyUpdates"
        reportfileName = "report"
    }
}
