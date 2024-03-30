import java.util.Properties


plugins {
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
}

val properties = Properties()
properties.load(project.rootProject.file("local.properties").inputStream())

val store_file by extra(properties["store_file"])
val store_pass by extra(properties["store_pass"])
val key_alias by extra(properties["key_alias"])
val key_pass by extra(properties["key_pass"])


