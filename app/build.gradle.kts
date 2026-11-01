import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

val versionPropertiesFile = rootProject.file("version.properties")
val versionProperties = Properties()
if (versionPropertiesFile.exists()) {
    versionProperties.load(versionPropertiesFile.inputStream())
}

val isBundleReleaseRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("bundleRelease", ignoreCase = true)
}

val currentCode = versionProperties.getProperty("versionCode", "1").toInt()
val currentName = versionProperties.getProperty("versionName", "0.1.0")

val (appVersionCode, appVersionName) = if (isBundleReleaseRequested) {
    val nextCode = currentCode + 1
    val segments = currentName.split(".").toMutableList()
    val lastIndex = segments.lastIndex
    segments[lastIndex] = ((segments[lastIndex].toIntOrNull() ?: 0) + 1).toString()
    val nextName = segments.joinToString(".")
    versionPropertiesFile.writeText(
        "# Last built release. Auto-incremented on every bundleRelease build.\n" +
            "versionCode=$nextCode\n" +
            "versionName=$nextName\n",
    )
    nextCode to nextName
} else {
    currentCode to currentName
}

base {
    archivesName.set("SwaraLab-$appVersionName-$appVersionCode")
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    doLast {
        val releaseOutputDir = layout.buildDirectory.dir("outputs/bundle/release").get().asFile
        val builtAab = releaseOutputDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "aab" }
            .maxByOrNull { it.lastModified() }
        if (builtAab != null) {
            val releasesDir = rootProject.file("releases").apply { mkdirs() }
            val archived = releasesDir.resolve("SwaraLab-$appVersionName-$appVersionCode.aab")
            builtAab.copyTo(archived, overwrite = true)
            println("Archived release bundle: ${archived.absolutePath}")
        }
    }
}

android {
    namespace = "com.swaralab.app"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.swaralab.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:session"))
    implementation(project(":core:ui"))
    implementation(project(":engine:audio"))
    implementation(project(":engine:render"))
    implementation(project(":feature:library"))
    implementation(project(":feature:studio"))
    implementation(project(":feature:publish"))
    implementation(project(":feature:account"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
}
