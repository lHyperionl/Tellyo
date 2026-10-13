package com.tellyo.app.core.update

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Test

class GitHubUpdateRepositoryTest {
    private val repository = "lHyperionl/Tellyo"
    private val base = "https://github.com/$repository/releases/download/build-1001"
    private val hash = "a".repeat(64)

    @Test fun offersOnlyANewerCompatibleBuild() = runTest {
        val update = checker().checkForUpdate()!!
        assertEquals(1001, update.versionCode)
        assertEquals("0.1.1001", update.versionName)
        assertEquals("$base/tellyo.apk", update.apkUrl)
        assertEquals(hash, update.sha256)
    }

    @Test fun equalOrOlderBuildDoesNotPromptEvenWithDifferentVersionName() = runTest {
        assertNull(checker(installed = 1001).checkForUpdate())
        assertNull(checker(installed = 1002).checkForUpdate())
    }

    @Test fun incompatibleTvDoesNotPrompt() = runTest {
        assertNull(checker(sdk = 22).checkForUpdate())
    }

    @Test fun noReleaseDoesNotPrompt() = runTest {
        assertNull(checker(status = 404).checkForUpdate())
    }

    @Test fun draftsAndPrereleasesDoNotPrompt() = runTest {
        assertNull(checker(release = release().replace("\"draft\":false", "\"draft\":true")).checkForUpdate())
        assertNull(checker(release = release().replace("\"prerelease\":false", "\"prerelease\":true")).checkForUpdate())
    }

    @Test fun missingOrUnuploadedAssetsDoNotPrompt() = runTest {
        assertNull(checker(release = release().replace("tellyo.apk", "other.apk")).checkForUpdate())
        assertNull(checker(release = release().replace("uploaded", "new")).checkForUpdate())
    }

    @Test fun rejectsForeignRepositoryOrTagAndInsecureUrls() {
        listOf("http://github.com/$repository/releases/download/build-1001/tellyo.apk",
            "$base/tellyo.apk?redirect=1", "$base/tellyo.apk#fragment",
            "https://github.com/attacker/Tellyo/releases/download/build-1001/tellyo.apk",
            "$base/../build-1002/tellyo.apk",
            "https://github.com.evil.example/$repository/releases/download/build-1001/tellyo.apk",
        ).forEach { url ->
            assertFalse(url, isReleaseAssetUrl(url, repository, "build-1001", "tellyo.apk"))
        }
    }

    @Test fun rejectsInvalidHashSizeOrPackageMetadata() {
        listOf(manifest().replace(hash, "bad"), manifest().replace("\"sizeBytes\":4", "\"sizeBytes\":0"),
            manifest().replace("com.tellyo.app", "com.other.app"),
            manifest().replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            manifest().replace("\"sizeBytes\":4", "\"sizeBytes\":5"),
        ).forEach { metadata ->
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking { checker(manifest = metadata).checkForUpdate() }
            }
        }
    }

    @Test fun reportsRateLimitAsCheckFailure() {
        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { checker(status = 403).checkForUpdate() }
        }
    }

    @Test fun boundsChunkedReleaseMetadata() {
        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { checker(release = " ".repeat(256 * 1024 + 1)).checkForUpdate() }
        }
    }

    private fun checker(installed: Int = 1, sdk: Int = 35, status: Int = 200,
        release: String = release(), manifest: String = manifest()): GitHubUpdateRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            check(request.header("Authorization") == null)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(status).message("fixture")
                .body((if (request.url.host == "api.github.com") release else manifest).toResponseBody())
                .build()
        }.build()
        return GitHubUpdateRepository(client, repository, installed, sdk)
    }

    private fun release() = """{"tag_name":"build-1001","draft":false,"prerelease":false,"assets":[
        {"name":"tellyo-update.json","state":"uploaded","size":500,"browser_download_url":"$base/tellyo-update.json"},
        {"name":"tellyo.apk","state":"uploaded","size":4,"browser_download_url":"$base/tellyo.apk"}]}"""

    private fun manifest() = """{"schemaVersion":1,"applicationId":"com.tellyo.app","versionCode":1001,
        "versionName":"0.1.1001","minSdk":23,"apkAsset":"tellyo.apk","sha256":"$hash","sizeBytes":4}"""
}
