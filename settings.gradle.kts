pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()

        // 阿里云公共仓库
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }

        // JitPack
        maven {
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "gldemo"
include(":app")
include(":scenceView")
