package io.github.vrcmteam.vrcm.network.api.github.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReleaseData (
    val assets: List<AssetsData>,
    @SerialName("assets_url")
    val assetsUrl: String,
    val author: AuthorData,
    val body: String,
    @SerialName("created_at")
    val createdAt: String,
    val draft: Boolean,
    @SerialName("html_url")
    val htmlUrl: String,
    val id: Int,
    val name: String,
    @SerialName("node_id")
    val nodeId: String,
    val prerelease: Boolean,
    @SerialName("published_at")
    val publishedAt: String,
    @SerialName("tag_name")
    val tagName: String,
    @SerialName("tarball_url")
    val tarballUrl: String,
    @SerialName("target_commitish")
    val targetCommitish: String,
    @SerialName("upload_url")
    val uploadUrl: String,
    @SerialName("zipball_url")
    val zipBallUrl: String,
    val url: String
)