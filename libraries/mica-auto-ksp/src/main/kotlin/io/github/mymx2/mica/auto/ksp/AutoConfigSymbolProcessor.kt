@file:Suppress("PrivatePropertyName", "detekt:VariableNaming", "detekt:NestedBlockDepth")

package io.github.mymx2.mica.auto.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import java.util.concurrent.ConcurrentHashMap
import net.dreamlu.mica.auto.annotation.AutoIgnore
import net.dreamlu.mica.auto.common.BootAutoType
import net.dreamlu.mica.auto.common.MultiSetMap

/**
 * 自动服务注册
 *
 * [autoservice](https://github.com/ZacSweers/auto-service-ksp/blob/main/processor/src/main/kotlin/dev/zacsweers/autoservice/ksp/AutoServiceSymbolProcessor.kt)
 */
@Suppress("DuplicatedCode")
class AutoConfigSymbolProcessor(environment: SymbolProcessorEnvironment) : SymbolProcessor {

  private val codeGenerator = environment.codeGenerator
  private val logger = environment.logger

  private val verify = environment.options["autoserviceKsp.verify"]?.toBoolean() == true
  private val verbose = environment.options["autoserviceKsp.verbose"]?.toBoolean() == true

  /**
   * The location to look for factories.
   *
   * Can be present in multiple JAR files.
   */
  private val FACTORIES_RESOURCE_LOCATION = "META-INF/spring.factories"
  /** devtools，有 Configuration 注解的 jar 一般需要 devtools 配置文件 */
  private val DEVTOOLS_RESOURCE_LOCATION = "META-INF/spring-devtools.properties"
  /** AutoConfiguration 注解 */
  private val AUTO_CONFIGURATION = "org.springframework.boot.autoconfigure.AutoConfiguration"
  /** AutoConfiguration imports out put */
  private val AUTO_CONFIGURATION_IMPORTS_LOCATION = "META-INF/spring/$AUTO_CONFIGURATION.imports"

  /** 缓存注解 */
  private val annotationImplementerCache = ConcurrentHashMap<Pair<String, String>, Boolean>()

  /** 数据承载 */
  private val factories = MultiSetMap<String, String>()

  /** spring boot 2.7 @AutoConfiguration */
  private val autoConfigurationImportsSet: MutableSet<String> = LinkedHashSet()

  @Suppress("detekt:TooGenericExceptionCaught")
  override fun process(resolver: Resolver): List<KSAnnotated> {
    try {
      processAnnotations(resolver)
      generateConfigFiles()
    } catch (e: Exception) {
      if (verbose) {
        logger.warn(e.message ?: "ksp build error")
      } else {
        logger.info(e.message ?: "ksp build error")
      }
    } finally {
      annotationImplementerCache.clear()
      factories.clear()
      autoConfigurationImportsSet.clear()
    }
    return emptyList()
  }

  @Suppress("detekt:TooGenericExceptionCaught")
  private fun generateConfigFiles() {
    if (autoConfigurationImportsSet.isNotEmpty()) {
      val resourceFile = AUTO_CONFIGURATION_IMPORTS_LOCATION
      log("Working on resource file: $resourceFile, contents: $autoConfigurationImportsSet")
      try {
        val dependencies = Dependencies(false)
        codeGenerator.createNewFile(dependencies, "", resourceFile, "").bufferedWriter().use {
          writer ->
          for (service in autoConfigurationImportsSet) {
            writer.write(service)
            writer.newLine()
          }
        }
        log("Wrote to: $resourceFile")
      } catch (e: Exception) {
        logger.error("Unable to create $resourceFile, $e")
      }
    }
    if (!factories.isEmpty) {
      val resourceFile = FACTORIES_RESOURCE_LOCATION
      log("Working on resource file: $resourceFile, contents: $factories")
      try {
        val dependencies = Dependencies(false)
        codeGenerator.createNewFile(dependencies, "", resourceFile, "").bufferedWriter().use {
          writer ->
          val text =
            factories.keySet().joinToString("\n\n") { key ->
              val values =
                factories[key].joinToString(prefix = "\\\n  ", separator = ",\\\n  ") { it }
              "$key=$values"
            }
          writer.write(text)
          writer.newLine()
        }
        log("Wrote to: $resourceFile")
      } catch (e: Exception) {
        logger.error("Unable to create $resourceFile, $e")
      }
    }
  }

  private fun processAnnotations(resolver: Resolver) {
    //    val autoIgnoreType =
    //      resolver.getClassDeclarationByName(
    //        resolver.getKSNameFromString(AutoIgnore::class.qualifiedName!!)
    //      )
    //    resolver
    //      .getSymbolsWithAnnotation(FEIGN_CLIENT_ANNOTATION)
    //      .filterIsInstance<KSClassDeclaration>()
    val classes =
      resolver.getNewFiles().flatMap { it.declarations }.filterIsInstance<KSClassDeclaration>()

    // spring.factories 兼容 spring boot 2.7
    classes.forEach allDeclarations@{ providerImplementer ->
      val annotations = providerImplementer.annotations

      annotations.forEach {
        // 忽略 @AutoIgnore 注解
        val annotationName = it.annotationType.resolve().declaration.qualifiedName?.asString()
        if (annotationName == AutoIgnore::class.qualifiedName) {
          return@allDeclarations
        }
      }

      BootAutoType.entries
        .map { Pair(it.annotation, it.configureKey) }
        .plus(Pair(BootAutoType.COMPONENT_ANNOTATION, AUTO_CONFIGURATION_IMPORTS_LOCATION))
        .plus(
          // 处理的注解 @FeignClient
          Pair(
            "org.springframework.cloud.openfeign.FeignClient",
            "net.dreamlu.mica.feign.MicaFeignAutoConfiguration",
          )
        )
        .forEach { autoType ->
          val autoName = autoType.first
          val autoFactory = autoType.second

          val annotationImplementer = isAnnotationImplementer(annotations, autoName)
          if (annotationImplementer) {
            val factoryName =
              providerImplementer.qualifiedName.run { this?.asString() ?: return@forEach }
            if (factories.containsVal(factoryName)) return@forEach

            if (autoName == BootAutoType.COMPONENT_ANNOTATION) {
              autoConfigurationImportsSet.add(factoryName)
            }
            if (
              autoName == "org.springframework.cloud.openfeign.FeignClient" &&
                providerImplementer.classKind != ClassKind.INTERFACE
            ) {
              log("@FeignClient annotation can only be applied to interfaces.")
              return@forEach
            }
            factories.put(autoFactory, factoryName)
          }
        }
    }
  }

  @Suppress("detekt:LoopWithTooManyJumpStatements", "detekt:ReturnCount")
  private fun isAnnotationImplementer(
    ksAnnotations: Sequence<KSAnnotation>,
    annotationFullName: String,
  ): Boolean {
    for (anno in ksAnnotations) {
      val desc = anno.annotationType.resolve().declaration
      val qualifiedName = desc.qualifiedName?.asString() ?: continue

      // 基础注解或跳过已处理
      if (qualifiedName.isFilteredAnnotation()) continue

      // 检查缓存（使用当前注解和目标注解作为复合键）
      val cacheKey = qualifiedName to annotationFullName
      annotationImplementerCache[cacheKey]?.let {
        if (it) return true // 缓存命中且为true直接返回
        else return@let // 缓存命中且为false则继续
      }

      // 直接匹配目标注解
      if (qualifiedName == annotationFullName) {
        annotationImplementerCache[cacheKey] = true
        return true
      }

      // 递归检查元注解（使用相同目标注解）
      if (isAnnotationImplementer(desc.annotations, annotationFullName)) {
        annotationImplementerCache[cacheKey] = true
        return true
      }

      // 缓存阴性结果
      annotationImplementerCache[cacheKey] = false
    }
    return false
  }

  // 扩展函数优化基础注解检测
  private fun String.isFilteredAnnotation(): Boolean {
    return this.startsWith("java.lang") ||
      this.startsWith("org.springframework.lang") ||
      this.startsWith("kotlin.Metadata") ||
      this.startsWith("kotlin.annotation") ||
      this.startsWith("org.junit") ||
      this == "java.lang.Override" // 添加常见基础注解
  }

  private fun log(message: String) {
    if (verbose) {
      logger.logging(message)
    }
  }
}

class AutoConfigSymbolProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return AutoConfigSymbolProcessor(environment)
  }
}
