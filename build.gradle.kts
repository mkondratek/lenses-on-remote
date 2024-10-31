import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

fun properties(key: String) = project.findProperty(key)?.toString()

plugins {
  id("org.jetbrains.kotlin.jvm") version "2.0.21"
  id("org.jetbrains.intellij.platform") version "2.1.0"
  id("com.diffplug.spotless") version "6.25.0"
}

val platformVersion: String by project
val platformType: String by project
val javaVersion: String by project

group = properties("pluginGroup")!!

version = properties("pluginVersion")!!

repositories {
  maven { url = uri("https://www.jetbrains.com/intellij-repository/releases") }
  mavenCentral()
  gradlePluginPortal()
  intellijPlatform {
    defaultRepositories()
    jetbrainsRuntime()
  }
}

intellijPlatform {
  pluginConfiguration {
    name = properties("pluginName")
    version = properties("pluginVersion")
    ideaVersion {
      sinceBuild = properties("pluginSinceBuild")
      untilBuild = properties("pluginUntilBuild")
    }
  }
}

dependencies {
  intellijPlatform {
    jetbrainsRuntime()
    create(platformType, platformVersion)
    bundledPlugins(
        properties("platformPlugins")
            .orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty))
    instrumentationTools()
    pluginVerifier()
  }
}

spotless {
  lineEndings = com.diffplug.spotless.LineEnding.UNIX
  kotlinGradle {
    ktfmt()
    trimTrailingWhitespace()
  }
  kotlin {
    ktfmt()
    trimTrailingWhitespace()
    target("src/**/*.kt")
    toggleOffOn()
  }
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(javaVersion.toInt()))
    vendor = JvmVendorSpec.JETBRAINS
  }
}

tasks {
  // Set the JVM compatibility versions
  javaVersion.let {
    withType<JavaCompile> {
      sourceCompatibility = it
      targetCompatibility = it
    }
    withType<KotlinJvmCompile> { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
  }

  val customRunIde by
      intellijPlatformTesting.runIde.registering {
        task.get().jvmArgs("-Djdk.module.illegalAccess.silent=true")

        version.set(properties("platformRuntimeVersion"))
        val myType = IntelliJPlatformType.fromCode(properties("platformRuntimeType") ?: "IC")
        type.set(myType)
        plugins { plugins(properties("platformRuntimePlugins").orEmpty()) }
        splitMode.set(properties("splitMode")?.toBoolean() ?: false)
      }

  runIde {
    doLast {
      project.logger.error(
          """
          ==========================================
          The :runIde task is no longer supported.
          Please use the :customRunIde task instead.
          ==========================================
        """)
    }
  }
}
