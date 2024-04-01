import java.util.Properties


plugins {
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
}

val properties = Properties()
properties.load(project.rootProject.file("local.properties").inputStream())

val storeFile by extra(properties["store_file"])
val storePass by extra(properties["store_pass"])
val keyAlias by extra(properties["key_alias"])
val keyPass by extra(properties["key_pass"])


