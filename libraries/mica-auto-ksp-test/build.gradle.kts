import io.github.mymx2.plugin.spring.SpringBootHelper.getSpringBootVersion
import io.github.mymx2.plugin.spring.SpringBootHelper.springBootStarterWebMvc

plugins {
  id("io.github.mymx2.module.kotlin")
  id("io.github.mymx2.module.spring-boot")
}

val springBootVersion = getSpringBootVersion()

springBootStarterWebMvc(springBootVersion)

dependencies {
  ksp(projects.micaAutoKsp)
  implementation(projects.micaAutoKsp)
  implementation(depLibs.micaAuto)

  testImplementation(embeddedKotlin("test-junit5"))
}
