package io.github.vrcmteam.vrcm.service.data

data class VersionDto(
    val tagName: String,
    val htmlUrl: String,
    val body: String,
    val hasNewVersion: Boolean,
    val downloadUrl: List<String> = emptyList(),
)
