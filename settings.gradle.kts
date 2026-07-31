@file:Suppress("UnstableApiUsage")

import io.github.mymx2.plugin.dyIncludeProjects

plugins {
  id("io.github.mymx2.build") version "1.5.3"
  id("io.github.mymx2.build.feature.catalogs") version "1.5.3"
  id("io.github.mymx2.plugin.dy.example.settings") version "1.5.3"
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
}
