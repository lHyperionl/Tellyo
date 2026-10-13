package com.tellyo.app.core.update

import com.tellyo.app.core.network.readLimitedBytes
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Public release metadata only; no GitHub credentials are shipped to TVs. */
class GitHubUpdateRepository(
    httpClient: OkHttpClient,
    private val repository: String,
    private val installedVersionCode: Int,
    private val sdkInt: Int,
) : UpdateRepository {
    private val client = httpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        require(repository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")))
    }

    override suspend fun checkForUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
        val releaseRequest = Request.Builder()
            .url("https://api.github.com/repos/$repository/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Tellyo/$installedVersionCode")
            .build()
        val release = client.newCall(releaseRequest).awaitResponse().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw IOException("Update check: HTTP ${response.code}")
            json.decodeFromString<GitHubRelease>(
                requireNotNull(response.body).readLimitedBytes(256 * 1024L).decodeToString(),
            )
        }
        if (release.draft || release.prerelease) return@withContext null
        val manifestAsset = release.assets.singleOrNull {
            it.name == "tellyo-update.json" && it.state == "uploaded"
        } ?: return@withContext null
        require(isReleaseAssetUrl(manifestAsset.downloadUrl, repository, release.tag, manifestAsset.name))
        val manifest = client.newCall(Request.Builder().url(manifestAsset.downloadUrl).build())
            .awaitResponse().use { response ->
                if (!response.isSuccessful) throw IOException("Update metadata: HTTP ${response.code}")
                json.decodeFromString<UpdateManifest>(
                    requireNotNull(response.body).readLimitedBytes(64 * 1024L).decodeToString(),
                )
            }
        require(manifest.schemaVersion == 1 && manifest.applicationId == "com.tellyo.app")
        require(manifest.versionCode in 1..2_100_000_000 && manifest.minSdk > 0)
        require(manifest.versionName.isNotBlank() && manifest.versionName.length <= 100)
        if (manifest.versionCode <= installedVersionCode || manifest.minSdk > sdkInt) return@withContext null
        require(manifest.sha256.matches(Regex("[a-fA-F0-9]{64}")))
        require(manifest.sizeBytes in 1..MAX_UPDATE_BYTES)
        require(manifest.apkAsset == "tellyo.apk")
        val apk = release.assets.singleOrNull {
            it.name == manifest.apkAsset && it.state == "uploaded"
        } ?: return@withContext null
        require(apk.size == manifest.sizeBytes)
        require(isReleaseAssetUrl(apk.downloadUrl, repository, release.tag, apk.name))
        AppUpdate(manifest.versionCode, manifest.versionName, apk.downloadUrl,
            manifest.sha256.lowercase(), manifest.sizeBytes)
    }
}

internal const val MAX_UPDATE_BYTES = 200 * 1024 * 1024L

internal fun isReleaseAssetUrl(value: String, repository: String, tag: String, name: String): Boolean {
    val url = value.toHttpUrlOrNull() ?: return false
    return url.isHttps && url.host == "github.com" && url.port == 443 &&
        url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null &&
        url.pathSegments == repository.split('/') + listOf("releases", "download", tag, name)
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tag: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<GitHubAsset>,
)

@Serializable
private data class GitHubAsset(
    val name: String,
    val state: String,
    val size: Long,
    @SerialName("browser_download_url") val downloadUrl: String,
)

@Serializable
internal data class UpdateManifest(
    val schemaVersion: Int,
    val applicationId: String,
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int,
    val apkAsset: String,
    val sha256: String,
    val sizeBytes: Long,
)
