import io.github.mymx2.plugin.dyIncludeProjects

plugins {
  id("io.github.mymx2.build") version "1.3.8"
  id("io.github.mymx2.build.feature.catalogs") version "1.3.8"
  id("io.github.mymx2.plugin.dy.example.settings") version "1.3.8"
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "auto-ksp"

dyIncludeProjects(
  mapOf(
    ":mica-auto-ksp" to "libraries/mica-auto-ksp",
    ":mica-auto-ksp-test" to "libraries/mica-auto-ksp-test",
  )
)
