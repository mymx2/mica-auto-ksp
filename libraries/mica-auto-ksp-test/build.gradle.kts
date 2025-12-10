plugins {
  id("io.github.mymx2.module.kotlin")
  id("io.github.mymx2.module.spring-boot")
  id("io.github.mymx2.tools.spring-openapi")
}

dependencies {
  ksp(projects.micaAutoKsp)
  implementation(projects.micaAutoKsp)

  testImplementation(embeddedKotlin("test-junit5"))
}
