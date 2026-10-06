pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}

rootProject.name = "StremioMobile"
include(":app")
include(":mpv-android-lib")
project(":mpv-android-lib").projectDir = file("third_party/mpv-android-lib/app")
includeBuild("streamio-core-kotlin/stremio-core-kotlin")

// LibVLC is kept at the settings level so both app variants use the exact same backend version.
gradle.projectsEvaluated {
    project(":app").dependencies.add("implementation", "org.videolan.android:libvlc-all:3.7.7")
}
