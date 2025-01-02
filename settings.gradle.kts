pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") } // Add JitPack repository here
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // Add JitPack repository here
        jcenter() {
            content{
                include("com.thereartofdev.edmodo", "android-image-cropper")
            }
        }
    }
}

rootProject.name = "AITeacher"
include(":app")
// Include other modules if you have any