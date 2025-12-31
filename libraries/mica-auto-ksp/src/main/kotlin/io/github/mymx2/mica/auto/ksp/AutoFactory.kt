package io.github.mymx2.mica.auto.ksp

import kotlin.reflect.KClass

/**
 * AutoFactory
 *
 * Auto Configuration for `META-INF/spring.factories`
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class AutoFactory(vararg val value: KClass<*>)
