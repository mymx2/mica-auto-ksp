package io.github.mymx2.mica.auto.ksp

import com.google.auto.service.AutoService
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.Sets
import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import java.io.IOException
import java.util.*

/**
 * [AutoService] 的 KSP 实现：
 *
 * from: https://github.com/ZacSweers/auto-service-ksp
 *
 * ```
 * 目标：
 * - 扫描所有 @AutoService(value = ...) 标注的类
 * - 校验实现类是否 implements 接口
 * - 将结果映射为： META-INF/services/<interface binary name>
 * ```
 *
 * 行为基本对齐 javac annotation processor 版本的 auto-service
 */
class AutoServiceSymbolProcessor(environment: SymbolProcessorEnvironment) : SymbolProcessor {

  private companion object {
    /** AutoService 注解的全限定名 */
    const val AUTO_SERVICE_NAME = "com.google.auto.service.AutoService"
  }

  private val codeGenerator = environment.codeGenerator
  private val logger = environment.logger

  /**
   * providers 的核心作用：
   *
   * ```
   * Key   : Service Interface 的 binary name
   * Value : (实现类的 binary name, 对应的 KSFile)
   *
   * 示例：
   *   com.foo.Service
   *     -> (com.foo.impl.ServiceImpl, ServiceImpl.kt)
   *
   * 为什么需要 KSFile：
   * - 用于 KSP 增量编译的 Dependencies
   * ```
   */
  private val providers: Multimap<String, Pair<String, KSFile>> = HashMultimap.create()

  /** 是否开启严格校验（实现类必须 implements 接口） */
  private val verify = environment.options["autoserviceKsp.verify"]?.toBoolean() == true

  /** 是否输出详细日志 */
  private val verbose = environment.options["autoserviceKsp.verbose"]?.toBoolean() == true

  /**
   * KSP 主处理入口
   *
   * 整体流程：
   *
   * ```
   * 1. 查找 [AutoService] 注解类型
   * 2. 扫描所有被 @AutoService 标注的类
   *    - 解析 annotation value（可能是单个或多个接口）
   *    - 校验实现关系
   *    - 按 Service Interface 分类收集
   * 3. 为每个 Service Interface 生成 META-INF/services 文件
   * 4. 返回 deferred symbols（用于下一轮处理）
   * ```
   */
  @Suppress("detekt:CyclomaticComplexMethod", "detekt:LongMethod")
  override fun process(resolver: Resolver): List<KSAnnotated> {

    // 1️⃣ 获取 AutoService 注解类型本身
    val autoServiceType =
      resolver
        .getClassDeclarationByName(resolver.getKSNameFromString(AUTO_SERVICE_NAME))
        ?.asType(emptyList())
        ?: run {
          // 如果 classpath 中根本没有 AutoService，直接跳过
          val message = "@AutoService type not found on the classpath, skipping processing."
          if (verbose) {
            logger.warn(message)
          } else {
            logger.info(message)
          }
          return emptyList()
        }

    // deferred：用于类型尚未解析完成（isError）的情况
    val deferred = mutableListOf<KSAnnotated>()

    // 2️⃣ 查找所有使用了 @AutoService 的符号
    val symbolsWithAnnotation = resolver.getSymbolsWithAnnotation(AUTO_SERVICE_NAME).toList()

    symbolsWithAnnotation.filterIsInstance<KSClassDeclaration>().forEach { providerImplementer ->

      // 找到该类上的 @AutoService 注解实例
      val annotation =
        providerImplementer.annotations.find { it.annotationType.resolve() == autoServiceType }
          ?: run {
            logger.error("@AutoService annotation not found", providerImplementer)
            return@forEach
          }

      // 读取 @AutoService(value = [...]) 的 value 参数
      val argumentValue = annotation.arguments.find { it.name?.getShortName() == "value" }!!.value

      /**
       * ```
       * AutoService 支持：
       *   @AutoService(Foo::class)
       *   @AutoService(Foo::class, Bar::class)
       *
       * KSP 里可能是：
       * - KSType
       * - List<KSType>
       * ```
       */
      @Suppress("UNCHECKED_CAST")
      val providerInterfaces =
        try {
          argumentValue as? List<KSType> ?: listOf(argumentValue as KSType)
        } catch (_: ClassCastException) {
          logger.error("No 'value' member value found!", annotation)
          return@forEach
        }

      if (providerInterfaces.isEmpty()) {
        logger.error(
          """
          No service interfaces specified by @AutoService annotation!
          You can provide them in annotation parameters:
          @AutoService(YourService::class)
          """
            .trimIndent(),
          annotation,
        )
      }

      // 3️⃣ 针对每个声明的 Service Interface 进行处理
      for (providerType in providerInterfaces) {

        // 类型尚未解析完成（例如跨模块）
        if (providerType.isError) {
          deferred += providerImplementer
          return@forEach
        }

        val providerDecl = providerType.declaration.closestClassDeclaration()!!

        // 校验实现关系
        when (checkImplementer(providerImplementer, providerType)) {
          ValidationResult.VALID -> {
            providers.put(
              providerDecl.toBinaryName(),
              providerImplementer.toBinaryName() to providerImplementer.containingFile!!,
            )
          }

          ValidationResult.INVALID -> {
            logger.error(
              "ServiceProviders must implement their service provider interface. " +
                "${providerImplementer.qualifiedName} does not implement " +
                providerDecl.qualifiedName,
              providerImplementer,
            )
          }

          ValidationResult.DEFERRED -> {
            deferred += providerImplementer
          }
        }
      }
    }

    // 4️⃣ 生成 META-INF/services 文件
    generateAndClearConfigFiles()

    // 返回需要下一轮处理的符号
    return deferred
  }

  /**
   * 校验 providerImplementer 是否真正实现了 providerType
   *
   * 返回值：
   * - VALID : 校验通过
   * - INVALID : 未实现接口
   * - DEFERRED : 依赖类型尚未解析完成
   */
  @Suppress("detekt:ReturnCount")
  private fun checkImplementer(
    providerImplementer: KSClassDeclaration,
    providerType: KSType,
  ): ValidationResult {

    // 未开启 verify 时直接放行
    if (!verify) {
      return ValidationResult.VALID
    }

    // 遍历所有父类型（包含接口、父类）
    for (superType in providerImplementer.getAllSuperTypes()) {
      when {
        superType.isAssignableFrom(providerType) -> return ValidationResult.VALID

        superType.isError -> return ValidationResult.DEFERRED
      }
    }

    return ValidationResult.INVALID
  }

  /** 根据 providers 内容生成 META-INF/services 文件 */
  @Suppress("detekt:SpreadOperator", "detekt:NestedBlockDepth")
  private fun generateAndClearConfigFiles() {

    for (providerInterface in providers.keySet()) {
      val resourceFile = "META-INF/services/$providerInterface"
      log("Working on resource file: $resourceFile")

      try {
        // TreeSet 保证稳定、有序输出
        val allServices: SortedSet<String> = Sets.newTreeSet()

        val foundImplementers = providers[providerInterface]
        val newServices = foundImplementers.map { it.first }.toSet()

        allServices.addAll(newServices)

        log("New service file contents: $allServices")

        // 收集来源文件，用于增量编译
        val ksFiles = foundImplementers.map { it.second }

        val dependencies = Dependencies(true, *ksFiles.toTypedArray())

        // 创建 META-INF/services 文件
        codeGenerator.createNewFile(dependencies, "", resourceFile, "").bufferedWriter().use {
          writer ->
          for (service in allServices) {
            writer.write(service)
            writer.newLine()
          }
        }

        log("Wrote to: $resourceFile")
      } catch (e: IOException) {
        logger.error("Unable to create $resourceFile, $e")
      }
    }

    // 清空，避免下轮重复生成
    providers.clear()
  }

  private fun log(message: String) {
    if (verbose) {
      logger.logging(message)
    }
  }

  /**
   * 将类声明转换为 binary name
   *
   * ```
   * 例如：
   *   com.foo.Outer.Inner
   * → com.foo.Outer$Inner
   * ```
   */
  private fun KSClassDeclaration.toBinaryName(): String = toClassName().reflectionName()

  /** 将 KSClassDeclaration 转换为 KotlinPoet 的 ClassName */
  @Suppress("detekt:SpreadOperator")
  private fun KSClassDeclaration.toClassName(): ClassName {
    require(!isLocal()) { "Local/anonymous classes are not supported!" }

    val pkgName = packageName.asString()
    val typesString = qualifiedName!!.asString().removePrefix("$pkgName.")

    val simpleNames = typesString.split(".")
    return ClassName(pkgName, simpleNames)
  }

  private enum class ValidationResult {
    VALID,
    INVALID,
    DEFERRED,
  }
}

/** KSP SPI Provider */
class AutoServiceSymbolProcessorProvider : SymbolProcessorProvider {

  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
    AutoServiceSymbolProcessor(environment)
}
