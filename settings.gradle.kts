@file:Suppress("UnstableApiUsage")

import io.github.mymx2.plugin.dyIncludeProjects
import io.github.mymx2.plugin.resetTaskGroup

plugins {
  id("io.github.mymx2.build") version "1.5.6"
  id("io.github.mymx2.build.feature.catalogs") version "1.5.6"
  id("io.github.mymx2.plugin.dy.example.settings") version "1.5.6"
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "auto-ksp"

dyIncludeProjects(
  mapOf(
    ":mica-auto-ksp" to "libraries/mica-auto-ksp",
    ":mica-auto-ksp-test" to "libraries/mica-auto-ksp-test",
  )
)

gradle.lifecycle.afterProject {
  if (listOf("writeLocks", "unzipSourceJars").all { tasks.names.contains(it) }) {
    tasks.register("i") {
      description = "Install: writeLocks + unzipSourceJars"
      group = "alias"
      dependsOn(tasks.named("writeLocks"), tasks.named("unzipSourceJars"))
    }
  }
  mapOf("fmt" to "qualityGate", "lint" to "qualityCheck", "dev" to "bootRun").forEach {
    (alias, target) ->
    if (tasks.names.contains(target) && !tasks.names.contains(alias)) {
      tasks.register(alias) {
        description = "Task aliases: fmt → qualityGate, lint → qualityCheck, dev → bootRun"
        group = "alias"
        dependsOn(target)
      }
    }
  }
  listOf(
      "i" to "alias",
      "fmt" to "alias",
      "lint" to "alias",
      "dev" to "alias",
    )
    .forEach { resetTaskGroup(it.first, it.second) }
}
