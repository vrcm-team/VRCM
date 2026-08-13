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
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidxRoom)
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
            implementation(libs.androidx.core)
            implementation(libs.androidx.exifinterface)
            implementation(libs.androidx.sqlite.framework)
            implementation(libs.awebp)

            implementation(libs.ktor.client.okhttp)
        }

        androidUnitTest.dependencies {
            implementation(libs.robolectric)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.androidx.sqlite.bundled)
        }

        val desktopMain by getting
        desktopMain.dependencies {
            // support Dispatchers.Main
            implementation(libs.kotlinx.coroutines.swing)

            implementation(compose.desktop.currentOs)

            implementation(libs.ktor.client.okhttp)
            implementation(libs.jna.platform)
            implementation(libs.androidx.sqlite.bundled)
        }

        val desktopTest by getting
        desktopTest.dependencies {
            implementation(compose.desktop.currentOs)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.okio.fakefilesystem)
        }

        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.okio)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(libs.navigation3.ui)
            implementation(libs.navigationevent.compose)
            implementation(libs.material3.adaptive.navigation3)
//            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
//            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.navigation3)
//            implementation(compose.components.uiToolingPreview)

            implementation(libs.multiplatform.settings)

            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization.kotlinx.json)

            implementation(libs.coil.compose.core)
            implementation(libs.coil.network.ktor)

            implementation(libs.chrisbanes.haze)

            implementation(libs.filekit.dialogs.compose)
            implementation(libs.qrose)

            implementation(libs.androidx.room.runtime)

//            implementation(libs.kamel)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.multiplatform.settings.test)
            // commonTest 直接使用 FakeFileSystem，Android 单元测试编译也需要该依赖。
            implementation(libs.okio.fakefilesystem)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
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
        versionName = libs.versions.app.version.get()
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
         storeFile = if (storeFilePath.isNullOrEmpty()) null else rootProject.file(storeFilePath)
         storePass = properties.getProperty("store_pass")
         keyAlias = properties.getProperty("key_alias")
         keyPass = properties.getProperty("key_pass")
    }

    if (storeFile != null) {
        signingConfigs {
            create("release") {
                this.storeFile = storeFile
                this.storePassword = storePass
                this.keyAlias = keyAlias
                this.keyPassword = keyPass
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (storeFile != null) {
                this.signingConfig = signingConfigs.getByName("release")
            }
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
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "VRCM"
            packageVersion = libs.versions.app.version.get()
            description = "VRChat friend and content manager"
            vendor = "VRCM Team"
            windows {
                iconFile.set(project.file("src/desktopMain/resources/VRCM.ico"))
                menuGroup = "VRCM"
                upgradeUuid = "aebfb803-0655-4c7e-8c79-f29e14618397"
            }
        }
    }
}
