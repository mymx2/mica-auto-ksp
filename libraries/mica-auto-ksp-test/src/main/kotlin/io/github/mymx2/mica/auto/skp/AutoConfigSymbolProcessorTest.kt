@file:Suppress("PackageDirectoryMismatch")

package auto.ksp.test

import net.dreamlu.mica.auto.annotation.AutoIgnore
import net.dreamlu.mica.auto.annotation.AutoListener
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean

/** 正常的 AutoConfiguration */
@AutoConfiguration
class AutoConfigurationSuccess1 {

  @Bean fun testBean1(): String = let { "Hello testBean1" }
}

/** 正常的 AutoConfiguration */
@AutoConfiguration
class AutoConfigurationSuccess2 {

  @Bean fun testBean2(): String = let { "Hello testBean2" }
}

/** 忽略的 AutoConfiguration */
@AutoIgnore @AutoConfiguration class IgnoredAutoConfiguration

/** 正常的 ApplicationListener */
@AutoListener
class AutoListenerSuccess1 : ApplicationListener<ApplicationStartedEvent> {
  override fun onApplicationEvent(event: ApplicationStartedEvent) {
    println("ApplicationListener#1#${event::class.java.simpleName}")
  }
}

/** 正常的 ApplicationListener */
@AutoListener
class AutoListenerSuccess2 : ApplicationListener<ApplicationReadyEvent> {
  override fun onApplicationEvent(event: ApplicationReadyEvent) {
    println("ApplicationListener#2#${event::class.java.simpleName}")
  }
}
