plugins {
  id("io.github.mymx2.module.kotlin")
  id("io.github.mymx2.feature.publish-vanniktech")
}

dependencies {
  api(depLibs.autoService)
  api(depLibs.micaAuto)
  implementation(depLibs.kotlinpoet)
  implementation(depLibs.symbolProcessingApi)

  testImplementation(embeddedKotlin("test-junit5"))
}
