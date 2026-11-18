pluginManagement {
    repositories {
        // 国内镜像优先，官方仓库兜底：Gradle 插件门户在国内经常连不上。
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "Accounting"
include(":app")
