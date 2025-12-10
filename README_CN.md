<p align="center">
  <a href="https://central.sonatype.com/artifact/io.github.mymx2/mica-auto-ksp" target="_blank"><img alt="maven-central-version"
    src="https://img.shields.io/maven-central/v/io.github.mymx2/mica-auto-ksp?strategy=latestProperty"/></a>
  <a href="https://central.sonatype.com/repository/maven-snapshots/io/github/mymx2/mica-auto-ksp/maven-metadata.xml" target="_blank"><img alt="maven-metadata-url"
    src="https://img.shields.io/maven-metadata/v?label=snapshot&metadataUrl=https://central.sonatype.com/repository/maven-snapshots/io/github/mymx2/mica-auto-ksp/maven-metadata.xml&strategy=latestProperty"/></a>
  <a href="https://github.com/mymx2/mica-auto-ksp/releases" target="_blank"><img alt="git-hub-release"
    src="https://img.shields.io/github/v/release/mymx2/mica-auto-ksp"/></a>
</p>

<p align="center">
  <a href="https://app.codacy.com/gh/mymx2/mica-auto-ksp/dashboard" target="_blank"><img alt="codacy-grade"
    src="https://img.shields.io/codacy/grade/64109c17cc5c4ea090db54cb773621fe"/></a>
  <a href="https://app.codecov.io/gh/mymx2/mica-auto-ksp" target="_blank"><img alt="codecov"
    src="https://img.shields.io/codecov/c/github/mymx2/mica-auto-ksp"/></a>
  <a href="https://github.com/mymx2/mica-auto-ksp/actions/workflows/publish-release.yml" target="_blank"><img alt="git-hub-actions-workflow-status"
    src="https://img.shields.io/github/actions/workflow/status/mymx2/mica-auto-ksp/publish-release.yml"/></a>
</p>

<p align="center">
  <a href="https://jdk.java.net" target="_blank"><img alt="JDK"
    src="https://img.shields.io/badge/dynamic/toml?logo=openjdk&label=JDK&color=brightgreen&url=https%3A%2F%2Fraw.githubusercontent.com%2Fmymx2%2Fmica-auto-ksp%2Fmain%2Fgradle%2Flibs.versions.toml&query=%24.versions.jdk&suffix=%2B"/></a>
  <a href="https://gradle.org" target="_blank"><img alt="GRADLE"
    src="https://img.shields.io/badge/dynamic/toml?logo=gradle&label=Gradle&color=209BC4&url=https%3A%2F%2Fraw.githubusercontent.com%2Fmymx2%2Fmica-auto-ksp%2Fmain%2Fgradle%2Flibs.versions.toml&query=%24.versions.gradle"/></a>
  <a href="https://kotlinlang.org/docs/getting-started.html" target="_blank"><img alt="KOTLIN"
    src="https://img.shields.io/badge/dynamic/toml?logo=kotlin&label=Kotlin&color=7f52ff&url=https%3A%2F%2Fraw.githubusercontent.com%2Fmymx2%2Fmica-auto-ksp%2Fmain%2Fgradle%2Flibs.versions.toml&query=%24.versions.kotlin"/></a>
  <a href="https://nodejs.org/en/download" target="_blank"><img alt="NODE"
    src="https://img.shields.io/badge/dynamic/toml?logo=nodedotjs&label=Node&color=5FA04E&url=https%3A%2F%2Fraw.githubusercontent.com%2Fmymx2%2Fmica-auto-ksp%2Fmain%2Fgradle%2Flibs.versions.toml&query=%24.versions.node"/></a>
</p>

<p align="center">
  <a href="https://github.com/mymx2" target="_blank"><img alt="mymx2"
      src="https://img.shields.io/badge/author-🤖_mymx2-E07A28?logo=github"/></a>
  <a href="https://docs.github.com/en/get-started/writing-on-github/getting-started-with-writing-and-formatting-on-github/basic-writing-and-formatting-syntax" target="_blank"><img alt="Markdown"
      src="https://img.shields.io/badge/md-GFM-0070C0?logo=Markdown"/></a>
  <a href="https://github.com/mymx2/mica-auto-ksp" target="_blank"><img alt="git-hub-license"
        src="https://img.shields.io/github/license/mymx2/mica-auto-ksp"/></a>
  <a href="https://deepwiki.com/mymx2/mica-auto-ksp" target="_blank"><img alt="Ask DeepWiki"
      src="https://deepwiki.com/badge.svg"/></a>
</p>

# mica-auto-ksp

<p>
  <a href="https://central.sonatype.com/artifact/io.github.mymx2/mica-auto-ksp" target="_blank"><img alt="maven-central-version"
    src="https://img.shields.io/maven-central/v/io.github.mymx2/mica-auto-ksp?strategy=latestProperty"/></a>
</p>

[English](README.md) | 中文

[mica-auto](https://github.com/lets-mica/mica-auto) 的 [ksp](https://github.com/google/ksp) 实现。

## 使用方法

```gradle
plugins {
  id("com.google.devtools.ksp") version "<version>"
  // ...
}

dependencies {
  ksp("io.github.mymx2:mica-auto-ksp:<version>")
}
```

## 相关链接

- Google Auto: https://github.com/google/auto
- Spring 5 - spring-context-indexer: https://github.com/spring-projects/spring-framework/tree/master/spring-context-indexer
- Auto-configuration: https://docs.spring.io/spring-boot/4.0-SNAPSHOT/reference/features/developing-auto-configuration.html

## 更多资讯

![dreamlu](https://raw.githubusercontent.com/lets-mica/mica-auto/master/docs/dreamlu-weixin.jpg)
