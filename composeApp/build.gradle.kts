import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.*

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.compose.compiler)
}

kotlin {

    jvm("desktop")

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)

            implementation(libs.koin.androidx.compose)

            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.stately.common)
            implementation(libs.ktor.client.darwin)
        }

        val desktopMain by getting
        desktopMain.dependencies {
            // support Dispatchers.Main
            implementation(libs.kotlinx.coroutines.swing)

            implementation(compose.desktop.currentOs)

            implementation(libs.ktor.client.okhttp)
        }

        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material)
            implementation(compose.material3)
//            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)

            implementation(libs.koin.compose)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization.kotlinx.json)

            implementation(libs.coil.compose.core)
            implementation(libs.coil.network.ktor)

            implementation(libs.chrisbanes.haze)

//            implementation(libs.kamel)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenModel)
            implementation(libs.voyager.transitions)
            implementation(libs.voyager.koin)
        }
    }
}

android {

    applicationVariants.all {
        outputs.all {
            val variantName = rootProject.name
            val versionName = versionName
            val newApkName = "$variantName-v$versionName.apk"
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = newApkName
        }
    }
    namespace = "io.github.vrcmteam.vrcm"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        applicationId = libs.versions.app.packageName.get()
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = libs.versions.app.code.get().toInt()
        versionName = libs.versions.app.name.get()
    }
//    buildFeatures {
//        compose = true
//    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // 防止没有local.properties文件没有配置签名报错导致没办法构建项目
    var storeFile: File? = null
    var storePass: String? = null
    var keyAlias: String? = null
    var keyPass: String? = null
    project.rootProject.file("local.properties").also {
        if (!it.isFile) return@also
        val properties = Properties()
        properties.load(it.inputStream())
        val storeFilePath = properties.getProperty("store_file")
         storeFile = if (storeFilePath.isNullOrEmpty()) null else file(storeFilePath)
         storePass = properties.getProperty("store_pass")
         keyAlias = properties.getProperty("key_alias")
         keyPass = properties.getProperty("key_pass")
    }

    signingConfigs {
        create("release") {
            this.storeFile = storeFile
            this.storePassword = storePass
            this.keyAlias = keyAlias
            this.keyPassword = keyPass
        }
    }

    buildTypes {
        getByName("release") {
            this.isMinifyEnabled = false
            this.signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            this.applicationIdSuffix = ".debug"
            this.isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}


compose.desktop {
    application {
        mainClass = "io.github.vrcmteam.vrcm.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = libs.versions.app.packageName.get()
            packageVersion = libs.versions.app.name.get()
        }
    }
}

