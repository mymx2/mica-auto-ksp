plugins {
  id("io.github.mymx2.module.kotlin")
  id("io.github.mymx2.feature.publish-vanniktech")
}

dependencies {
  api(depLibs.autoService)
  implementation(depLibs.kotlinpoet)
  implementation(depLibs.micaAuto)
  implementation(depLibs.symbolProcessingApi)

  testImplementation(embeddedKotlin("test-junit5"))
}
