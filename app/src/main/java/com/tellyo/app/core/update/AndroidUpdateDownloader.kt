package com.tellyo.app.core.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** Android performs the final signature check again when installing. */
class AndroidUpdateDownloader(
    private val context: Context,
    client: OkHttpClient,
) : UpdateDownloader {
    private val downloader = ApkDownloader(client, File(context.cacheDir, "updates"))
    private val downloadMutex = Mutex()

    override suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File =
        downloadMutex.withLock {
            withContext(Dispatchers.IO) {
                // Keep only the current attempt; installed updates need no cache.
                File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
                val apk = downloader.download(update, onProgress)
                try {
                    verifyApk(apk, update)
                    apk
                } catch (error: Exception) {
                    apk.delete()
                    throw error
                }
            }
        }

    @Suppress("DEPRECATION")
    private fun verifyApk(apk: File, update: AppUpdate) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val manager = context.packageManager
        val candidate = manager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw IOException("Invalid APK")
        if (candidate.packageName != context.packageName ||
            PackageInfoCompat.getLongVersionCode(candidate) != update.versionCode.toLong()) {
            throw IOException("APK does not match update metadata")
        }
        val installed = manager.getPackageInfo(context.packageName, flags)
        val installedSigners = currentSigners(installed)
        val candidateSigners = currentSigners(candidate)
        if (installedSigners.isEmpty() || installedSigners != candidateSigners) {
            throw IOException("APK signing key does not match the installed app")
        }
    }

    @Suppress("DEPRECATION")
    private fun currentSigners(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners
            else info.signatures
        return signatures.orEmpty().map { it.toCharsString() }.toSet()
    }
}
