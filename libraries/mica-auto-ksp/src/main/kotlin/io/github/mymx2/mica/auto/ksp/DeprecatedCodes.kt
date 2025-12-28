package io.github.mymx2.mica.auto.ksp

import com.google.devtools.ksp.symbol.KSAnnotation
import java.util.concurrent.ConcurrentHashMap

/** 缓存注解 */
private val annotationImplementerCache = ConcurrentHashMap<Pair<String, String>, Boolean>()

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
