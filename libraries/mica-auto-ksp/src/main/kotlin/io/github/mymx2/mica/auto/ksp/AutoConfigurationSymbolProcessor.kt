@file:Suppress("PrivatePropertyName", "detekt:VariableNaming", "detekt:NestedBlockDepth")

package io.github.mymx2.mica.auto.ksp

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import java.io.IOException
import net.dreamlu.mica.auto.annotation.AutoIgnore
import net.dreamlu.mica.auto.common.BootAutoType

/** Mica Auto 的 KSP 实现 */
@Suppress("DuplicatedCode")
class AutoConfigurationSymbolProcessorProcessor(environment: SymbolProcessorEnvironment) :
  SymbolProcessor {

  private val codeGenerator = environment.codeGenerator
  private val logger = environment.logger

  private val verbose = environment.options["AutoConfigurationKsp.verbose"]?.toBoolean() == true

  /** AutoConfiguration imports */
  private val AUTO_CONFIGURATION =
    Triple(
      "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
      "org.springframework.boot.autoconfigure.AutoConfiguration",
      BootAutoType.COMPONENT.annotation,
    )

  /** The location to look for factories. */
  private val FACTORIES_RESOURCE_LOCATION = "META-INF/spring.factories"

  /** Mica Feign */
  private val AUTO_FEIGN =
    Triple(
      FACTORIES_RESOURCE_LOCATION,
      "org.springframework.cloud.openfeign.FeignClient",
      "net.dreamlu.mica.feign.MicaFeignAutoConfiguration",
    )

  /** 数据承载 */
  private val providers: Multimap<String, Triple<String, String, KSFile>> = HashMultimap.create()

  @Suppress("detekt:TooGenericExceptionCaught")
  override fun process(resolver: Resolver): List<KSAnnotated> {
    val deferred = mutableListOf<KSAnnotated>()
    try {
      deferred.addAll(processAnnotations(resolver))
      generateConfigFiles()
    } catch (e: Exception) {
      if (verbose) {
        logger.warn(e.message ?: "ksp build error")
      } else {
        logger.info(e.message ?: "ksp build error")
      }
    } finally {
      providers.clear()
    }
    return deferred
  }

  private fun processAnnotations(resolver: Resolver): List<KSAnnotated> {
    BootAutoType.entries
      .map { Triple(FACTORIES_RESOURCE_LOCATION, it.annotation, it.configureKey) }
      .plus(AUTO_CONFIGURATION)
      .plus(AUTO_FEIGN)
      .forEach { autoType ->
        val (resourceFile, annotationName, providerInterface) = autoType

        resolver
          .getSymbolsWithAnnotation(annotationName, inDepth = false)
          .filterIsInstance<KSClassDeclaration>()
          .forEach allDeclarations@{ providerImplementer ->
            val annotations = providerImplementer.annotations

            annotations.forEach {
              // 忽略 @AutoIgnore 注解
              val annotationName = it.annotationType.resolve().declaration.qualifiedName?.asString()
              if (annotationName == AutoIgnore::class.qualifiedName) {
                return@allDeclarations
              }
            }

            val service =
              providerImplementer.qualifiedName.run { this?.asString() ?: return@forEach }

            if (
              annotationName == AUTO_FEIGN.second &&
                providerImplementer.classKind != ClassKind.INTERFACE
            ) {
              log("@FeignClient annotation can only be applied to interfaces.")
              return@forEach
            }
            providers.put(
              resourceFile,
              Triple(providerInterface, service, providerImplementer.containingFile!!),
            )
          }
      }

    return emptyList()
  }

  @Suppress("detekt:TooGenericExceptionCaught", "detekt:SpreadOperator")
  private fun generateConfigFiles() {
    providers.keySet().forEach { resourceFile ->
      log("Working on resource file: $resourceFile")

      try {
        val serviceProvider = providers[resourceFile]
        val ksFiles = serviceProvider.map { it.third }
        val dependencies = Dependencies(true, *ksFiles.toTypedArray())

        val contents =
          if (resourceFile == FACTORIES_RESOURCE_LOCATION) {
            val allServices = serviceProvider.groupBy({ it.first }, { it.second }).toSortedMap()
            log("New service file contents: $allServices")
            val text =
              allServices.entries.joinToString("\n\n") { (providerInterface, services) ->
                val implementations =
                  services.sorted().joinToString(prefix = "\\\n  ", separator = ",\\\n  ") { it }
                "$providerInterface=$implementations"
              }
            text
          } else {
            val allServices = serviceProvider.map { it.second }.sorted()
            log("New service file contents: $allServices")
            val text = allServices.joinToString("\n")
            text
          }

        // 创建 META-INF/services 文件
        codeGenerator.createNewFile(dependencies, "", resourceFile, "").bufferedWriter().use {
          writer ->
          writer.write(contents)
          writer.newLine()
        }

        log("Wrote to: $resourceFile")
      } catch (e: IOException) {
        logger.error("Unable to create $resourceFile, $e")
      }
    }
  }

  private fun log(message: String) {
    if (verbose) {
      logger.logging(message)
    }
  }
}

class AutoConfigurationSymbolProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return AutoConfigurationSymbolProcessorProcessor(environment)
  }
}
