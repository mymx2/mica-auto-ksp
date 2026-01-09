plugins {
  id("io.github.mymx2.module.kotlin")
  id("io.github.mymx2.module.spring-boot")
}

dependencies {
  ksp(projects.micaAutoKsp)
  implementation(projects.micaAutoKsp)
  implementation(depLibs.micaAuto)

  testImplementation(embeddedKotlin("test-junit5"))
}
