package io.github.mymx2.mica.auto.skp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication class MicaAutoKspTest

@Suppress("detekt:SpreadOperator")
fun main(args: Array<String>) {
  runApplication<MicaAutoKspTest>(*args)
}
