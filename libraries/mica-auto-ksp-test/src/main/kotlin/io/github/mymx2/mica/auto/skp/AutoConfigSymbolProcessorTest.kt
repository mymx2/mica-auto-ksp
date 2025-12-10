@file:Suppress(
  "PrivatePropertyName",
  "detekt:VariableNaming",
  "detekt:NestedBlockDepth",
  "PackageDirectoryMismatch",
)

package auto.ksp.test

import net.dreamlu.mica.auto.annotation.AutoIgnore
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component

@AutoConfiguration
class TestAutoConfiguration {

  @Bean fun testBean(): String = let { "Hello AutoConfiguration" }
}

@Component class TestComponent

@AutoIgnore @Component class IgnoredComponent
