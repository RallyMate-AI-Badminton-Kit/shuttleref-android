pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        maven("https://maven-other.tuya.com/repository/maven-releases/")
        maven("https://maven-other.tuya.com/repository/maven-commercial-releases/")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://developer.huawei.com/repo/")
        maven("https://jitpack.io")
    }
}
rootProject.name = "ShuttleRefAndroid"
include(":app", ":core")
