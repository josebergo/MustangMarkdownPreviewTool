pluginManagement {
    repositories {
        google {
            url = uri(providers.gradleProperty("googleMirror").getOrElse("https://dl.google.com/dl/android/maven2/"))
            content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx\\..*"); includeGroupByRegex("com\\.google.*") }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            url = uri(providers.gradleProperty("googleMirror").getOrElse("https://dl.google.com/dl/android/maven2/"))
            content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx\\..*"); includeGroupByRegex("com\\.google.*") }
        }
        mavenCentral()
    }
}
rootProject.name = "MustangMDView"
include(":app")
