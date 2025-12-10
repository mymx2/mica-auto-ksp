@file:Suppress("PackageDirectoryMismatch")

package auto.ksp.test

import com.google.auto.service.AutoService

interface HelloService {
  fun hello(): String
}

@AutoService(HelloService::class)
class HelloServiceImpl : HelloService {
  override fun hello() = "OK"
}
