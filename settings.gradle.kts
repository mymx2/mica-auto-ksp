import io.github.mymx2.plugin.dyCreateVersionCatalogs
import io.github.mymx2.plugin.dyIncludeProjects

plugins {
  id("io.github.mymx2.build") version "1.3.2"
  id("io.github.mymx2.plugin.dy.example.settings") version "1.3.2"
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "auto-ksp"

dyCreateVersionCatalogs(mapOf("depLibs" to "gradle/depLibs.versions.toml"))

dyIncludeProjects(
  mapOf(
    ":mica-auto-ksp" to "libraries/mica-auto-ksp",
    ":mica-auto-ksp-test" to "libraries/mica-auto-ksp-test",
  )
)
