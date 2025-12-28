package io.github.mymx2.mica.auto.ksp

import auto.ksp.test.AutoConfigurationSuccess1
import auto.ksp.test.AutoConfigurationSuccess2
import auto.ksp.test.HelloService
import auto.ksp.test.HelloServiceImpl
import auto.ksp.test.IgnoredAutoConfiguration
import io.github.mymx2.mica.auto.skp.MicaAutoKspTest
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.getBean
import org.springframework.beans.factory.getBeanProvider
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestConstructor

@SpringBootTest(classes = [MicaAutoKspTest::class])
@ExtendWith(OutputCaptureExtension::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MicaAutoKspIntegrationTests(private val context: ApplicationContext) {

  private val generatedResourcesDir = "build/generated/ksp/main/resources"

  @Test
  @DisplayName("打印所有加载的 Beans 便于调试")
  fun printLoadedBeans(output: CapturedOutput) {
    val beanNames = context.beanDefinitionNames.sorted()
    println("Loaded beans: $beanNames")
    assertThat(output).contains("Loaded beans")
  }

  @Test
  @DisplayName("检查 AutoConfiguration Beans 是否加载")
  fun checkAutoConfigurationBeansLoaded() {
    assertThat(context.getBean<AutoConfigurationSuccess1>()).isNotNull
    assertThat(context.getBean<AutoConfigurationSuccess2>()).isNotNull
    assertThat(context.getBeanProvider<IgnoredAutoConfiguration>().ifAvailable).isNull()
  }

  @Test
  @DisplayName("通过 ServiceLoader 加载 HelloService")
  fun loadHelloServiceViaServiceLoader() {
    val loader = ServiceLoader.load(HelloService::class.java)
    val services = loader.iterator().asSequence().toList()

    assertThat(services).isNotEmpty

    val impl =
      services.firstOrNull { it.javaClass.simpleName == HelloServiceImpl::class.simpleName }
    assertThat(impl).isNotNull
    assertThat(impl!!.hello()).isEqualTo("OK")
  }

  @Test
  @DisplayName("检查 META-INF/services 文件内容")
  fun checkMetaInfServicesContent() {
    val serviceFile = Path.of(generatedResourcesDir, "META-INF/services/auto.ksp.test.HelloService")
    assertThat(Files.exists(serviceFile)).isTrue()

    val lines = Files.readAllLines(serviceFile)
    assertThat(lines).containsExactly("auto.ksp.test.HelloServiceImpl")
  }

  @Test
  @DisplayName("检查 Spring AutoConfiguration.imports 文件内容")
  fun checkSpringAutoConfigurationImportsContent() {
    val autoConfigFile =
      Path.of(
        generatedResourcesDir,
        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
      )
    assertThat(Files.exists(autoConfigFile)).isTrue()

    val lines = Files.readAllLines(autoConfigFile)
    assertThat(lines)
      .contains(
        "auto.ksp.test.AutoConfigurationSuccess1",
        "auto.ksp.test.AutoConfigurationSuccess2",
      )
  }

  @Test
  @DisplayName("检查 Spring.factories 文件内容")
  fun checkSpringFactoriesContent() {
    val factoriesFile = Path.of(generatedResourcesDir, "META-INF/spring.factories")
    assertThat(Files.exists(factoriesFile)).isTrue()

    val content = Files.readString(factoriesFile)
    assertThat(content)
      .contains(
        """
        org.springframework.context.ApplicationListener=\
          auto.ksp.test.AutoListenerSuccess1,\
          auto.ksp.test.AutoListenerSuccess2
        """
          .trimIndent()
      )
  }
}
